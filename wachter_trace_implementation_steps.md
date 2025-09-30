# Wachter Trace & Proxy Rework — Detailed Implementation Steps

## Легенда

- **Контекст** – файлы или компоненты, где ведётся работа.
- **Действия** – конкретные шаги, которые нужно выполнить.
- **Результат** – ожидаемый итог шага.
- **Проверка** – как убедиться, что шаг выполнен корректно.

## Шаг 1. Подготовка вспомогательной логики для восстановления TraceContext

- **Контекст**: `/src/main/java/dev/vality/wachter/config/WebConfig.java`, новый пакет `trace` или `context`.
- **Действия**:
    1. Создать утилиту/компонент `TraceContextInitializer`, который:
        - Читает заголовки (`traceparent`, `x-woody-*`, `woody.*`, `X-Request-ID`, `X-Request-Deadline`).
        - Формирует `TraceData` (traceId/spanId/parentId, deadline, otel-span).
        - Если заголовок не найден, генерирует значения (spanId, parentId = `NO_PARENT_ID`) и логирует предупреждение.
        - Инициализирует MDC (`MDCUtils.putSpanData`) и RPC-метаданные (через `MetadataExtensionKit`).
    2. В `WebConfig` заменить прямой вызов `new WFlow().createServiceFork` на использование этого компонента:
        - До fork/chain вызова – восстановить контекст.
        - Если `WFlow` остаётся, передавать `TraceContext` внутрь без генерации нового корневого span.
- **Результат**: любые обработчики получают корректно заполненный `TraceContext`.
- **Проверка**: unit-тесты `TraceContextInitializer` с кейсами (полный набор заголовков, только traceId, нет
  заголовков).

## Шаг 2. Обновление логирования MDC под формат hellgate

- **Контекст**: `TraceContextInitializer`, `MDCUtils` (использование).
- **Действия**:
    1. После восстановления `TraceContext` добавлять в MDC:
        - `trace_id`, `span_id`, `parent_id`, `deadline`, `otel_trace_id`, `otel_span_id`, `otel_trace_flags`.
        - RPC-контекст: `trace.rpc.server.service`, `trace.rpc.server.function`, `trace.rpc.server.url`,
          `trace.rpc.server.deadline`, аналогично для клиента (по данным `ServiceMapper`, `MethodNameReaderService`,
          входного запроса, Service-заголовка).
    2. Если данные отсутствуют – оставлять пустые строки, но логировать предупреждение.
- **Результат**: в логах появляются поля, соответствующие `hellgate.log.json`.
- **Проверка**: интеграционные тесты/лог-тесты (можно сравнить записи при `INFO`-логировании, убедиться в наличии
  ключей).

## Шаг 3. Переработка WachterClient в динамический HTTP-прокси

- **Контекст**: `/src/main/java/dev/vality/wachter/client/WachterClient.java`.
- **Действия**:
    1. Заменить `Apache HttpClient` на `Spring RestClient` (конфигурация бин – в `ApplicationConfig`).
    2. Реализовать метод `send(HttpServletRequest request, byte[] body, String targetUrl)` так, чтобы:
        - Определять HTTP-метод (`request.getMethod()`), целевой URI (target + query parameters).
        - Копировать все транспортные заголовки (кроме hop-by-hop), включая `Content-Type`, `Accept`.
        - Вставлять/переиспользовать Woody-заголовки (`x-woody-*`, `traceparent`, устаревшие `woody.*`).
        - Отдавать тело запроса (для методов с телом) с корректным `Content-Length` / streaming, если требуется.
        - После `retrieve()` возвращать код статуса, заголовки и тело *без модификаций*.
- **Результат**: `WachterClient` можно использовать как прозрачный прокси для любых HTTP-запросов.
- **Проверка**: unit-тесты на клиент (`MockRestServiceServer`), интеграционные тесты контроллера.

## Шаг 4. Возврат ответа и обработка ошибок

- **Контекст**: `WachterClient`, `WachterResponseHandler` (можно удалить или адаптировать).
- **Действия**:
    1. Настроить `RestClient` так, чтобы:
        - При успешном ответе возвращать: статус, заголовки, тело (в виде `ResponseEntity<byte[]>`).
        - При ошибке (4xx/5xx) – не кидать исключение (использовать `.onStatus()`), а возвращать оригинальный ответ.
    2. В `WachterService` адаптировать обработку ответа (логирование вход/выход).
- **Результат**: клиенты получают точную копию downstream-ответа, включая ошибки.
- **Проверка**: интеграционные тесты с mock-сервером, проверка статуса/заголовков.

## Шаг 5. Поддержка устаревших заголовков и Traceparent

- **Контекст**: `TraceContextInitializer`, `WachterClient`.
- **Действия**:
    1. При чтении заголовков: сопоставлять пары (`x-woody-*` <-> `woody.*`), если присутствует только один вариант –
       генерировать второй.
    2. `traceparent`:
        - Если приходит – парсить и восстанавливать OTEL span.
        - Если нет – строить новый из `TraceContext` (поддержка downstream).
- **Результат**: полная обратная совместимость с клиентами.
- **Проверка**: юнит-тесты с комбинациями заголовков.

## Шаг 6. Обновление тестов

- **Контекст**: `/src/test/java/dev/vality/wachter/...`
- **Действия**:
    1. Обновить существующие тесты (`WachterControllerTest`, `ErrorControllerTest`, `WoodyHeaderTest`).
    2. Добавить новые сценарии:
        - Полный набор заголовков, частичный, отсутствие.
        - Разные HTTP-методы (GET/POST/PUT/DELETE), контент (JSON, бинарный, Thrift).
        - Ошибочные ответы downstream (4xx/5xx, таймауты).
    3. Использовать `MockMvc` + mock downstream server (`WireMock` / TestRestController`).
- **Результат**: уверенность в корректности проксирования и трейсинга.
- **Проверка**: запуск `mvn test`.

## Шаг 7. Документация и финализация

- **Контекст**: `wachter_trace_analysis.md`, README (если требуется), changelog.
- **Действия**:
    1. Обновить `wachter_trace_analysis.md` с описанием новой архитектуры trace/proxy.
    2. При необходимости обновить README.
    3. Добавить заметки в changelog или в описание PR.
- **Результат**: документация отражает изменения.
- **Проверка**: ревью, согласование с командой.
