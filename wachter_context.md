# Wachter – Reference Context

## Project Overview
- **Role:** Security gateway for Control Center — authenticates users, enforces role-based access to domain APIs, then transparently proxies HTTP/Thrift payloads.
- **Stack:** Java 21, Spring Boot 3, RestClient (JDK `HttpClient` backend), Spring Security (Keycloak JWT), Woody tracing library, OpenTelemetry (SDK + OTLP/HTTP exporter), WireMock for integration tests.
- **Key Traits:** Dual-format Woody headers (`woody.*` + legacy `x-woody-*`), full upstream response passthrough, guaranteed W3C `traceparent` propagation.

## Runtime Flow
1. **Ingress filter (`WoodyTracingFilter`):** normalizes incoming Woody headers, restores Woody `TraceContext`, and starts an OpenTelemetry SERVER span that injects `traceparent`, records response status, and captures exceptions.
2. **Controller (`WachterController`):** validates `X-Request-Deadline`, delegates to the service layer, and returns upstream responses (status, headers, body) unchanged.
3. **Service layer (`WachterService`):** extracts thrift method name, retrieves JWT from Spring Security, runs role-based checks via `AccessService`/`RoleAccessService`, resolves target service URL, and forwards the call.
4. **Outbound proxy (`WachterClient`/`WachterRequestFactory`):** merges servlet headers, normalized Woody headers, trace-context data, and JWT fallbacks; mirrors both Woody header families; executes the request via Spring `RestClient` and wraps the upstream response.

## Configuration Highlights
- `ApplicationConfig`: builds a `RestClient` using `JdkClientHttpRequestFactory` backed by `HttpClient` with configured timeouts; exposes `WachterClient` bean.
- `WebConfig`: registers `WoodyTracingFilter` and exposes helper beans for tests (`normalizeWoodyHeaders`, `applyWoodyHeadersToTraceContext`).
- `OtelConfig`: conditionally initializes OpenTelemetry (OTLP HTTP exporter, always-on sampler, W3C propagators) and registers it globally.
- `application.yml`: defines service mappings, client timeout properties, authorization flags, and OpenTelemetry endpoint.

## Security & Access Control
- JWT parsed via Spring Security; `JwtTokenDetailsExtractor` centralizes claim extraction (subject, username, email, realm, roles).
- `AccessService` assembles `AccessData` and defers permission checks to `RoleAccessService` (service-level or method-level access).
- Keycloak/OpenID behavior is stubbed for tests through `AbstractKeycloakOpenIdAsWiremockConfig` and `KeycloakOpenIdStub`.

## Tracing & Header Strategy
- Normalized Woody headers stored under request attribute `wachter.normalizedWoodyHeaders`.
- `WoodyRequestFactory` ensures both `woody.*` and `x-woody-*` headers are emitted, including `meta.user-identity.*` suffixes derived via `WoodySuffixes.userIdentitySuffix`.
- `WoodyHeadersNormalizer` merges JWT metadata, resolves relative deadlines from `X-Request-Deadline`, and respects existing `woody.deadline` values.
- OpenTelemetry spans carry HTTP semantic attributes (`HTTP_METHOD`, `HTTP_TARGET`, `HTTP_STATUS_CODE`), set status to ERROR for 5xx, and record exceptions.

## Package Map
- `dev.vality.wachter.config` – Spring configuration (application, web, OTEL, security).
- `dev.vality.wachter.config.tracing` – tracing utilities linking Woody and OpenTelemetry.
- `dev.vality.wachter.client` – outbound proxy logic (`WachterClient`, `WachterRequestFactory`, `WachterClientResponse`).
- `dev.vality.wachter.service` – business logic (`WachterService`, `MethodNameReaderService`).
- `dev.vality.wachter.security` – access control helpers and JWT utilities.
- `dev.vality.wachter.controller` – REST endpoints and error handling.
- `dev.vality.wachter.constants` – shared header and request attribute constants.
- `dev.vality.wachter.utils` – deadline and thrift utilities.

## Testing
- **Unit suites:** `WebConfigTest`, `WachterClient*Test`, controller/security tests validate header propagation, trace context hydration, and authorization logic.
- **Integration:** `WachterIntegrationTest` (WireMock) verifies end-to-end behavior: headers mirrored, trace context captured, response passthrough.
- **Command:** run all tests with `mvn test`.

## Operational Notes
- Maintain deadline consistency between `DeadlineUtil` checks and header normalization.
- OpenTelemetry exporter controlled via `otel.enabled` and `otel.resource` properties; ensure environment supplies OTLP endpoint.
- Follow Checkstyle expectations (`final` for immutable locals, minimal comments) to keep build green.
- Avoid modifying documentation without explicit request; `README.md` already synchronized with current architecture.

## Quick Snippets
- **Normalized headers access:**
  ```java
  @SuppressWarnings("unchecked")
  Map<String, String> headers = (Map<String, String>)
      request.getAttribute(RequestAttributeNames.NORMALIZED_WOODY_HEADERS);
  ```
- **Run full suite:**
  ```bash
  mvn test
  ```
- **Create RestClient with custom timeout:**
  ```java
  RestClient client = builder
      .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(2))
          .build()))
      .build();
  ```

Keep this context handy when planning automation or reviewing Wachter changes.
