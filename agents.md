# Agents Guide

This document helps automation agents work effectively on the **Wachter** service.

## Project Snapshot
- **Purpose:** authorize requests from Control Center and transparently proxy them to domain services.
- **Stack:** Java 21, Spring Boot 3, RestClient (JDK HTTP), OpenTelemetry (OTLP/HTTP), Woody tracing library, JWT via Spring Security.
- **Tracing Flow:** `WoodyTracingFilter` normalizes Woody headers, hydrates Woody trace context, starts an OpenTelemetry span, and stores normalized headers in the request.
- **Proxy Flow:** `WachterService` performs access checks, `WachterClient` (using `RestClient`) forwards requests, and `WachterController` returns upstream status/headers/body unchanged.

## Key Components
- `dev.vality.wachter.config` – Spring configuration; `ApplicationConfig`, `WebConfig`, `OtelConfig`.
- `dev.vality.wachter.config.tracing` – tracing helpers (`WoodyHeadersNormalizer`, `WoodyTraceContextApplier`, `WoodyTelemetrySupport`, `WoodyTracingFilter`).
- `dev.vality.wachter.client` – outbound proxy pieces (`WachterClient`, `WachterRequestFactory`, `WachterClientResponse`).
- `dev.vality.wachter.security` – access control (`AccessService`, `RoleAccessService`, `JwtTokenDetailsExtractor`).
- `dev.vality.wachter.service` – application services (`WachterService`, `MethodNameReaderService`).
- `dev.vality.wachter.controller` – REST controllers (`WachterController`, error handling tests).
- `dev.vality.wachter.constants` – header/attribute constants.

## Tests & Verification
- **Unit tests:** `mvn test` (runs JUnit + WireMock integration suite).
- **Key suites:** `WachterClient*Test`, `WebConfigTest`, controller tests, `WachterIntegrationTest` (WireMock-based end-to-end proxy verification).
- Ensure new features include coverage across: header normalization, trace context propagation, authorization checks, proxy behaviour.

## Conventions & Practices
- Maintain dual Woody headers (`woody.*` + `x-woody-*`).
- Preserve upstream responses exactly (status, headers, body).
- Use `JwtTokenDetailsExtractor` for JWT-derived values; avoid duplicating claim parsing.
- When touching tracing, ensure OpenTelemetry span attributes (`HTTP_*`, `traceparent`) remain intact.
- Follow existing Checkstyle conventions: `final` for immutable locals, concise logging via SLF4J, minimal inline comments.
- Update documentation only when requested.

## Common Tasks
1. **Modify tracing behaviour:** inspect `config/tracing` classes; update tests in `WebConfigTest` and `WachterIntegrationTest`.
2. **Adjust outbound proxying:** change `WachterRequestFactory`/`WachterClient`; add/update tests in `client` package and integration suite.
3. **Authorization changes:** update `AccessService`, `RoleAccessService`; extend security tests accordingly.

## Useful Commands
- Run full suite: `mvn test`
- Format/imports: rely on IDE (no automatic formatter configured).
- Generate coverage (optional): `mvn test -Pcoverage` (if profile exists; verify before use).

## Gotchas
- `WachterController` enforces deadline checks before proxying; always keep `DeadlineUtil` behaviour in mind.
- `WoodyHeadersNormalizer` merges JWT metadata and deadlines with priority rules—changing behaviour requires revisiting corresponding tests.
- Integration tests spin up WireMock; avoid port conflicts by keeping default configuration.

Stay aligned with the README and trace plans when planning new work.
