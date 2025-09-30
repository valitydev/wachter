# Анализ интеграции `wachter` с `woody_java`

## 1. Поведение `wachter`

- **Входной фильтр**: `WebConfig#woodyFilter` оборачивает каждый HTTP-запрос в `new WFlow().createServiceFork(...)`,
  создавая новый `TraceData` и `traceId`.
- **Чтение traceId**: и `WachterService`, и `WachterClient` извлекают `traceId` только из входных HTTP-заголовков (
  `x-woody-trace-id`/`woody.trace-id`). Если внешний клиент не передал эти заголовки, значение остаётся `null`.
- **Форвардинг**: при отправке запроса дальше `WachterClient` копирует все заголовки из исходного запроса. Новый
  `traceId`, созданный `WFlow`, никак не экспортируется в заголовки forwarding-запроса.
- **Следствие**: когда `wachter` служит первой точкой входа, downstream-сервисы не получают `traceId`, поэтому сквозная
  трассировка отсутствует.

## 2. Референсные реализации

- **anapi-v2** (`config/WebConfig`): идентичный `WFlow`-фильтр, но сервис обрабатывает запросы сам. Для внешних вызовов
  используются Thrift-клиенты (`THSpawnClientBuilder`), где `TraceContext` → `x-woody-*` хедеры выставляются
  автоматически.
- **disputes-api** (`config/NetworkConfig`): аналогичный фильтр применяется только к REST-пути, downstream-вызовы
  выполняются через Woody Thrift-клиентов и сервлеты (`THServiceBuilder`).
- **p2p-api** (`config/WebConfig`): тот же фильтр; сервис не проксирует HTTP, а работает через Woody-клиентов.
- **disputes-tg-bot** (`servlet/*.java`): вход — Thrift-сервлет, собранный `THServiceBuilder`, где Woody сам
  восстанавливает `TraceContext` и формирует заголовки.
- **fraudbusters-ui**: фронтовый Angular-проект без прямой работы с `woody_java` (не влияет на серверный `traceId`).

## 3. Отличия и первопричина

- В эталонных сервисах `traceId` в HTTP/Thrift-запросах берётся из `TraceContext` внутри Woody-инфраструктуры, а не из
  пользовательских заголовков.
- Только `wachter` выступает чистым HTTP-прокси и пытается полагаться на существующие заголовки, не экспортируя
  `TraceContext` → `x-woody-*`.
- Поэтому даже при корректно созданном `TraceContext` (через `createServiceFork`) downstream-сервис видит
  пустой/дефолтный `traceId`.

## 4. Рекомендации

1. **Доставать источник traceId из Woody**:
   ```java
   TraceData traceData = TraceContext.getCurrentTraceData();
   String traceId = traceData.getServiceSpan().getSpan().getTraceId();
   ```
   Использовать это значение для `AccessData.traceId` и логирования вместо чтения HTTP-заголовков.
2. **Экспортировать Woody-контекст в исходящий запрос**: перед вызовом `httpclient.execute` добавить `x-woody-*`
   заголовки (trace-id, span-id, deadline и др.) из `TraceContext`. Можно реализовать утилиту, аналогичную серверному
   `TransportEventInterceptor`, или воспользоваться уже существующими конвертерами Woody.
3. **(Опционально) Поддерживать входящие заголовки**: если ожидается продолжение trace-chain из внешней системы, следует
   распарсить `x-woody-*`/`traceparent` перед `createServiceFork` и проинициализировать `TraceContext` вручную, иначе
   новый trace будет начинаться в `wachter`.

Использование этих шагов обеспечит валидный `traceId` в проксируемых запросах и восстановит сквозную трассировку.
