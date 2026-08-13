# Wachter

Wachter — HTTP-шлюз для авторизации и прозрачного проксирования Thrift-запросов от внешних систем к внутренним сервисам. Сервис определяет целевой адрес по заголовку `Service`, проверяет доступ пользователя по JWT и Thrift-методу, а затем передаёт запрос вместе с Woody-контекстом и метаданными пользователя.

## HTTP-интерфейс

- `POST /wachter` на порту `8022` — единственный эндпоинт прикладного API.
- Остальные пути на прикладном порту возвращают `404 Unknown address`.
- Actuator-эндпоинты `health`, `info` и `prometheus` доступны на management-порту `8023`.

Запрос должен содержать:

- Bearer JWT в заголовке `Authorization`;
- имя целевого сервиса в заголовке `Service`;
- бинарное тело Thrift-вызова.

Соответствие значений `Service` внутренним URL настраивается в `wachter.services` в `application.yml` или переопределяется при развёртывании.

## Обработка запроса

1. `WoodyTracingFilter` нормализует входящие Woody-заголовки и создаёт Woody `TraceData` через `WFlow`.
2. Данные пользователя из JWT и служебные заголовки запроса добавляются в `woody.meta.user-identity.*`.
3. `WachterService` читает имя метода из бинарного Thrift-пакета.
4. `ServiceMapper` определяет целевой сервис по заголовку `Service`.
5. `AccessService` проверяет доступ с учётом метода, сервиса, email пользователя и ролей JWT.
6. `WachterClient` отправляет запрос в upstream через Spring `RestClient` и Apache HttpClient 5.
7. Клиент получает статус и тело upstream-ответа, а Woody-заголовки ответа преобразуются обратно во внешнее представление.

## Woody metadata

Wachter поддерживает внутренние заголовки `woody.*` и внешние заголовки `x-woody-*`. Для каждого запроса сервис восстанавливает либо создаёт Woody trace context и передаёт его в upstream.

В `WFlow` также добавляются данные пользователя из JWT:

- `user-identity.id`;
- `user-identity.username`;
- `user-identity.email`;
- `user-identity.realm`.

Эти значения доступны как Woody custom metadata и публикуются в MDC с префиксом `rpc.server.metadata.`. Заголовки `X-Request-ID`, `X-Request-Deadline` и `X-Invoice-ID` также преобразуются в Woody metadata.

## OpenTelemetry

W3C trace context (`traceparent` и `tracestate`) обрабатывает OpenTelemetry Java Agent. Сборка помещает agent в runtime image и запускает приложение с параметром `-javaagent`. Wachter не создаёт OTEL span вручную и не переносит эти заголовки как обычные proxy-заголовки.

При штатном запуске agent:

- продолжает входящий W3C trace context либо создаёт новый trace;
- создаёт серверный span для входящего запроса;
- внедряет актуальный context в исходящий HTTP-запрос.

Woody tracing и OpenTelemetry — независимые механизмы: `WFlow` отвечает за Woody trace context и `woody.meta`, Java Agent — за OTEL spans и W3C propagation.
