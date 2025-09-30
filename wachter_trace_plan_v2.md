# План адаптации traceId в wachter (версия 2, с учётом control-center)

## Контекст

Основной фронтовой клиент (`control-center`) формирует заголовки:

```ts
{
  'x-woody-trace-id': traceId,
  'x-woody-span-id': traceId,
  'x-woody-parent-id': undefined,
  ...
}
```

то есть parent отсутствует, traceId и spanId совпадают. План должен учитывать и такие входные данные.

## Шаги

1. **Сбор входных заголовков**
    - Считать `traceparent`, `x-woody-trace-id`, `x-woody-span-id`, `x-woody-parent-id`, `x-woody-deadline` и прочие
      метаданные.
    - Для `control-center`: если `x-woody-parent-id` отсутствует, трактовать вызов как корневой (parent =
      `TraceContext.NO_PARENT_ID`).
    - Если доступен `traceparent`, использовать его как первоисточник.
   - Парсить как современные (`x-woody-*`), так и устаревшие (`woody.*`, `woody.meta.user-identity.*`) заголовки,
     чтобы сохранить обратную совместимость.

2. **Инициализация `TraceContext`**
    - Подготовить вспомогательный компонент, восстанавливающий `TraceData`:
        - `traceId` = `x-woody-trace-id` (или из `traceparent`).
        - `spanId` = `x-woody-span-id`; при отсутствии сгенерировать.
        - `parentId` = `x-woody-parent-id` или `NO_PARENT_ID` (когда undefined или пусто).
        - Дедлайн: парсить `x-woody-deadline`, если есть.
        - OTEL: если пришёл `traceparent`, создавать OTEL span как child; иначе стартовать новый SERVER-span с данным
          traceId, сохраняя внешнюю цепочку.
    - Применить `TraceContext.setCurrentTraceData(traceData)` до бизнес-логики; `WFlow.createServiceFork` использовать
      только над уже инициализированным контекстом или отказаться от него.
   - Убедиться, что инициализация выполняется до вызова `WachterService.process`, чтобы `getTraceId()` продолжало
     работать от `TraceContext`.

3. **Логирование и MDC**
    - Убедиться, что `TraceContext.init()` приводит к записи trace в MDC (`MDCUtils.putSpanData`), чтобы логи `wachter`
      содержали внешний traceId, пригодный для расследований.
   - Поддерживать формат логов, совместимый с эталоном (`hellgate.log.json`): в MDC должны появляться поля `trace_id`,
     `span_id`, `parent_id`, `deadline`, `otel_trace_id`, `otel_span_id`, `otel_trace_flags`, а также *распарсенный*
     RPC-контекст (`trace.rpc.server.*`, `trace.rpc.client.*` или аналогичная иерархия).
   - Источники данных: `TraceContext.getCurrentTraceData().getServiceSpan().getSpan()` (trace_id, span_id, parent_id,
     deadline), `TraceContext.getCurrentTraceData().getOtelSpan()` (otel_* поля), метаданные (`user-identity.*`) через
     существующие `MetadataExtensionKit`, параметры вызова (service/function/URL/deadline) — из `ServiceMapper`,
     `MethodNameReaderService`, `HttpServletRequest` и `Service` заголовка.
   - [TODO] Заполнение RPC-контекста пока не реализовано; необходимо при следующей итерации добавить извлечение
     service/function/endpoint и положить их в MDC.
   - Если какие-то значения отсутствуют во входном запросе, оставлять их пустыми/по умолчанию, но фиксировать это в
     логах или метриках, чтобы отследить неполные трассы.

4. **Проксирование downstream**
   - Пробрасывать весь набор входных заголовков (включая `X-Request-ID`, `X-Request-Deadline`, пользовательские мета-
     поля) без перезаписи traceId.
   - Дополнять недостающие системные заголовки: при отсутствии `traceparent` сформировать его из восстановленного
     контекста; если spanId был сгенерирован, добавить в заголовки.
   - Обновить логирование, чтобы фиксировать полный набор заголовков, попадающий в downstream.
    - Текущую логику `WachterClient` адаптировать так, чтобы она использовала восстановленный `TraceContext`, а не
      создаваемый внутри trace.
   - По-прежнему генерировать устаревшие `woody.*` заголовки из `x-woody-*`, чтобы сервисы, ориентирующиеся на старый
     формат, продолжали работать.
   - [TODO] В прокси понадобится использовать сохранённый `TraceHeaders` из request-атрибута, чтобы провалидированные
     значения traceId/spanId/parentId уходили downstream; реализовать при шаге 3.
   - Сделать `WachterClient` полноценным динамическим HTTP-прокси: учитывать исходный HTTP-метод, URI, query-параметры,
     тело запроса, а также `Content-Type`/`Accept` и другие транспортные заголовки.
   - Перейти с `Apache HttpClient` на `Spring RestClient` (см. пример `RemoteClient.kt`), чтобы переиспользовать его
     fluent-API и современные фичи (reactive error handling, перехватчики и т.п.).
   - Прозрачно возвращать ответ клиенту: код статуса, заголовки и тело (включая ошибки) должны совпадать с downstream-
     ответом без искажений.

5. **Fallback**
    - Если ни один trace-заголовок не пришёл, генерировать новый `TraceData`, логировать предупреждение и продолжать как
      сейчас.

6. **Тестирование**
    - Набор сценариев:
        - Полный `traceparent` + `x-woody-*`.
        - Только `x-woody-trace-id`/`x-woody-span-id` (как у `control-center`).
        - Наличие только `x-woody-trace-id` (нужно сгенерировать span).
        - Отсутствие заголовков.
    - Проверять, что downstream-сервис видит внешний traceId, а логи `wachter` — тот же ID.

7. **Документация**
    - Обновить `wachter_trace_analysis.md` и при необходимости README/контекст с описанием поддерживаемого формата
      заголовков и поведения в разных сценариях.
   - Зафиксировать расположение плана: актуальная версия — `/wachter/wachter_trace_plan_v2.md`, предыдущая —
     `/wachter/wachter_trace_plan_v1.md`.

## Дополнительные замечания

- `WachterClient` уже копирует полный набор заголовков и логирует их; при рефакторинге сохранить это поведение.
- Рассматривать как минимум четыре сценария клиентов и покрывать их тестами.
- Добавить обработку и тесты для устаревших заголовков (`woody.trace-id`, `woody.parent-id`,
  `woody.meta.user-identity.*`), чтобы `TraceContext` корректно заполнял метаданные (`user-identity.*`) через
  существующие ExtensionKit’ы.
- Протестировать прокси-логику для различных HTTP-методов (GET/POST/PUT/DELETE), типов контента (JSON, Thrift,
  двоичные файлы) и больших тел; убедиться, что `RestClient` корректно воспроизводит исходный запрос.
- Покрыть негативные сценарии: коды `4xx/5xx`, таймауты, ошибки сети. Убедиться, что прокси возвращает оригинальный
  статус/тело, а логи содержат информацию о неуспешном ответе.
- `WachterService.process(...)` уже полагается на `TraceContext.getCurrentTraceData()`; восстановление контекста должно
  происходить до вызова service-слоя, иначе traceId будет NULL.
- `WebConfig` сейчас использует `new WFlow().createServiceFork(...)`; план предусматривает подготовку контекста до
  запуска fork либо отказ от него.
- Логи собираются JSON-аппендером (см. `hellgate.log.json`); нужные поля появляются только при наличии значений в MDC.
- Данные о названии сервиса, методе, URL и т.п. берутся через `ServiceMapper`, `MethodNameReaderService`, заголовок
  `Service`; при рефакторинге опираться на них для RPC-контекста.
- Поведение по генерации устаревших заголовков (`woody.*`) сохраняется в `WachterClient` — это важно для обратной
  совместимости.
- Новые тесты желательно размещать рядом с существующими (например, `ErrorControllerTest`, `WoodyHeaderTest`),
  расширяя текущий набор сценариев.
   1. Полный набор (`traceparent` + `x-woody-*`).
   2. Формат `control-center` (trace и span совпадают, parent отсутствует).
   3. Только `x-woody-trace-id` (span и parent придётся сгенерировать).
   4. Заголовки отсутствуют — нужен новый trace + предупреждение в логах.
