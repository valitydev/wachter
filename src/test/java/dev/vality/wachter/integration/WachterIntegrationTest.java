package dev.vality.wachter.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.vality.wachter.client.WachterClient;
import dev.vality.wachter.config.AbstractKeycloakOpenIdAsWiremockConfig;
import dev.vality.wachter.testutil.TMessageUtil;
import org.apache.thrift.protocol.TProtocolFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static dev.vality.wachter.tracing.TraceHeaders.*;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties = {
        "wachter.services.Domain.url=${wiremock.server.baseUrl}/domain",
        "wachter.services.Deanonimus.url=${wiremock.server.baseUrl}/deanonimus",
        "wachter.services.MerchantStatistics.url=${wiremock.server.baseUrl}/magista"
})
class WachterIntegrationTest extends AbstractKeycloakOpenIdAsWiremockConfig {

    private static final ObjectMapper OBJECT_MAPPER = new JsonMapper();

    @Value("${server.port}")
    private int port;

    private RestClient restClient;
    private Logger wachterClientLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @Autowired
    private TProtocolFactory protocolFactory;

    @BeforeEach
    void setUp() {
        wachterClientLogger = (Logger) LoggerFactory.getLogger(WachterClient.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        wachterClientLogger.addAppender(logAppender);
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build()))
                .build();
    }

    @AfterEach
    void tearDown() {
        wachterClientLogger.detachAppender(logAppender);
        logAppender.stop();
        resetAllRequests();
    }

    @Test
    void shouldProxyRequestWithCompleteTracingHeaders() throws Exception {
        var traceId = "GZyWNGugAAA";
        var spanId = "GZyWNGugBBB";
        var parentId = "undefined";
        var deadline = Instant.now().plusSeconds(300);
        var requestId = UUID.randomUUID().toString();
        var payload = TMessageUtil.createTMessage(protocolFactory);
        var responseBody = "integration-response".getBytes();
        var upstreamTraceparent = "00-cfa3d3072a4e3e99fc14829a65311819-6e4609576fa4d077-01";
        var jwt = generateSimpleJwtWithRoles();

        stubFor(post(urlEqualTo("/deanonimus"))
                .withRequestBody(binaryEqualTo(payload))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(WOODY_TRACE_ID, traceId)
                        .withHeader(WOODY_PARENT_ID, parentId)
                        .withHeader(WOODY_SPAN_ID, spanId)
                        .withHeader(OTEL_TRACE_PARENT, upstreamTraceparent)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/x-thrift")
                        .withBody(responseBody)));

        var response = restClient.post()
                .uri("/wachter")
                .contentType(MediaType.valueOf("application/x-thrift"))
                .headers(headers -> {
                    // Browser/CDN headers - should be filtered out
                    headers.set("user-agent",
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:143.0) Gecko/20100101 Firefox/143.0");
                    headers.set("accept", "application/x-thrift");
                    headers.set("accept-encoding", "gzip, br");
                    headers.set("accept-language", "ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3");
                    headers.set("cache-control", "no-cache");
                    headers.set("cdn-loop", "cloudflare; loops=1");
                    headers.set("cf-connecting-ip", "185.121.234.44");
                    headers.set("cf-ipcountry", "NL");
                    headers.set("cf-ray", "98be96799e2ae4bc-AMS");
                    headers.set("cf-visitor", "{\"scheme\":\"https\"}");
                    headers.set("cf-warp-tag-id", "ef03c554-2080-404d-bd10-1928b8a59810");
                    headers.set("dnt", "1");
                    headers.set("origin", "https://iddqd.valitydev.com");
                    headers.set("pragma", "no-cache");
                    headers.set("priority", "u=4");
                    headers.set("referer", "https://iddqd.valitydev.com/");
                    headers.set("sec-fetch-dest", "empty");
                    headers.set("sec-fetch-mode", "cors");
                    headers.set("sec-fetch-site", "same-site");
                    headers.set("sec-gpc", "1");
                    headers.set("x-forwarded-proto", "https");

                    // Authentication and service
                    headers.set("Authorization", "Bearer " + jwt);
                    headers.set("Service", "Deanonimus");

                    // Woody tracing headers
                    headers.set(ExternalHeaders.X_WOODY_TRACE_ID, traceId);
                    headers.set(ExternalHeaders.X_WOODY_SPAN_ID, spanId);
                    headers.set(ExternalHeaders.X_WOODY_PARENT_ID, parentId);
                    headers.set(ExternalHeaders.X_WOODY_META_ID, "b54a93c4-415d-4f33-a5e9-3608fd043ff4");
                    headers.set(ExternalHeaders.X_WOODY_META_USERNAME, "noreply@valitydev.com");
                    headers.set(ExternalHeaders.X_WOODY_META_EMAIL, "noreply@valitydev.com");
                    headers.set(ExternalHeaders.X_WOODY_META_REALM, "internal");

                    // Request metadata
                    headers.set(ExternalHeaders.X_REQUEST_ID, requestId);
                    headers.set(ExternalHeaders.X_REQUEST_DEADLINE, deadline.toString());
                    headers.set(OTEL_TRACE_PARENT, upstreamTraceparent);
                })
                .body(payload)
                .retrieve()
                .toEntity(byte[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getHeaders().containsHeader(OTEL_TRACE_PARENT));
        assertArrayEquals(responseBody, response.getBody());

        List<LoggedRequest> requests = findAll(postRequestedFor(urlEqualTo("/deanonimus")));
        assertEquals(1, requests.size());
        LoggedRequest upstreamRequest = requests.get(0);

        assertEquals(traceId, upstreamRequest.getHeader(WOODY_TRACE_ID));
        assertEquals(spanId, upstreamRequest.getHeader(WOODY_SPAN_ID));
        assertEquals(parentId, upstreamRequest.getHeader(WOODY_PARENT_ID));
        assertTrue(upstreamRequest.containsHeader(WOODY_DEADLINE));

        assertEquals("application/x-thrift", upstreamRequest.getHeader(HttpHeaders.CONTENT_TYPE));
        assertEquals("application/x-thrift", upstreamRequest.getHeader(HttpHeaders.ACCEPT));
        assertEquals("gzip, br", upstreamRequest.getHeader(HttpHeaders.ACCEPT_ENCODING));
        assertEquals("ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3",
                upstreamRequest.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
        assertEquals("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:143.0) Gecko/20100101 Firefox/143.0",
                upstreamRequest.getHeader(HttpHeaders.USER_AGENT));

        var jwtClaims = decodeJwtPayload(jwt);
        assertEquals(jwtClaims.get("sub").asString(),
                upstreamRequest.getHeader(WOODY_META_ID));
        assertEquals(jwtClaims.get("preferred_username").asString(),
                upstreamRequest.getHeader(WOODY_META_USERNAME));
        assertEquals(jwtClaims.get("email").asString(),
                upstreamRequest.getHeader(WOODY_META_EMAIL));
        assertEquals(extractRealm(jwtClaims),
                upstreamRequest.getHeader(WOODY_META_REALM));

        var sendLog = logAppender.list.stream()
                .filter(event -> event.getFormattedMessage().startsWith("-> Send request"))
                .findFirst()
                .orElseThrow();
        var mdc = sendLog.getMDCPropertyMap();
        assertEquals(jwtClaims.get("sub").asString(),
                mdc.get("rpc.server.metadata.user-identity.id"));
        assertEquals(jwtClaims.get("preferred_username").asString(),
                mdc.get("rpc.server.metadata.user-identity.username"));
        assertEquals(jwtClaims.get("email").asString(),
                mdc.get("rpc.server.metadata.user-identity.email"));
        assertEquals(extractRealm(jwtClaims),
                mdc.get("rpc.server.metadata.user-identity.realm"));

        assertFalse(upstreamRequest.containsHeader(OTEL_TRACE_PARENT));

        assertEquals(requestId, upstreamRequest.getHeader(WOODY_META_REQUEST_ID));
        assertEquals(deadline.toString(), upstreamRequest.getHeader(WOODY_META_REQUEST_DEADLINE));

        assertFalse(upstreamRequest.containsHeader("cf-ray"));
        assertFalse(upstreamRequest.containsHeader("cdn-loop"));
        assertFalse(upstreamRequest.containsHeader("cf-visitor"));
        assertFalse(upstreamRequest.containsHeader("dnt"));
        assertFalse(upstreamRequest.containsHeader("sec-fetch-mode"));
        assertFalse(upstreamRequest.containsHeader("pragma"));
        assertFalse(upstreamRequest.containsHeader("cache-control"));
        assertFalse(upstreamRequest.containsHeader("origin"));
        assertFalse(upstreamRequest.containsHeader("referer"));
    }

    @Test
    void shouldNormalizeAndForwardMixedWoodyHeaders() throws Exception {
        final var deadline = Instant.now().plusSeconds(300);
        final var payload = TMessageUtil.createTMessage(protocolFactory);
        final var responseBody = "test-response".getBytes();
        final var jwt = generateSimpleJwtWithRoles();
        final var jwtClaims = decodeJwtPayload(jwt);
        var traceparent = "00-3d8202ad198e4d37771c995246e1b356-9cfa814ae977266e-01";

        stubFor(post(urlEqualTo("/magista"))
                .withRequestBody(binaryEqualTo(payload))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/x-thrift")
                        .withBody(responseBody)));

        final ResponseEntity<byte[]> response = restClient.post()
                .uri("/wachter")
                .contentType(MediaType.valueOf("application/x-thrift"))
                .headers(headers -> {
                    headers.set("Authorization", "Bearer " + jwt);
                    headers.set("Service", "MerchantStatistics");

                    // Mixed woody and x-woody headers
                    headers.set(WOODY_TRACE_ID, "GZvsthKQAAA");
                    headers.set(ExternalHeaders.X_WOODY_SPAN_ID, "GZvsthKQBBB");
                    headers.set(WOODY_PARENT_ID, "parent-woody");
                    headers.set(ExternalHeaders.X_WOODY_DEADLINE, deadline.toString());

                    // User identity in different formats
                    headers.set(WOODY_META_REALM, "/woody-realm");
                    headers.set(ExternalHeaders.X_WOODY_META_ID, "header-user-id");

                    // Request metadata
                    headers.set(ExternalHeaders.X_REQUEST_ID, "mixed-request-id");
                    headers.set(ExternalHeaders.X_REQUEST_DEADLINE, deadline.toString());

                    // The application must leave trace context propagation to the Java agent.
                    headers.set(OTEL_TRACE_PARENT, traceparent);
                })
                .body(payload)
                .retrieve()
                .toEntity(byte[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(responseBody, response.getBody());

        // Verify upstream request headers were normalized correctly
        List<LoggedRequest> requests = findAll(postRequestedFor(urlEqualTo("/magista")));
        assertEquals(1, requests.size());
        LoggedRequest upstreamRequest = requests.get(0);

        // All headers should be normalized to woody.* format
        assertEquals("GZvsthKQAAA", upstreamRequest.getHeader(WOODY_TRACE_ID));
        assertEquals("GZvsthKQBBB", upstreamRequest.getHeader(WOODY_SPAN_ID));
        assertEquals("parent-woody", upstreamRequest.getHeader(WOODY_PARENT_ID));
        assertNotNull(upstreamRequest.getHeader(WOODY_DEADLINE));

        // User identity metadata should be sourced from JWT when present
        assertEquals(jwtClaims.get("sub").asString(), upstreamRequest.getHeader(WOODY_META_ID));
        assertEquals(jwtClaims.get("preferred_username").asString(),
                upstreamRequest.getHeader(WOODY_META_USERNAME));
        assertEquals(jwtClaims.get("email").asString(),
                upstreamRequest.getHeader(WOODY_META_EMAIL));
        assertEquals(extractRealm(jwtClaims),
                upstreamRequest.getHeader(WOODY_META_REALM));

        // Maven tests run without the Java agent, so the application must not proxy trace context itself.
        assertFalse(upstreamRequest.containsHeader(OTEL_TRACE_PARENT));

        // Request metadata should be preserved
        assertEquals("mixed-request-id", upstreamRequest.getHeader(WOODY_META_REQUEST_ID));
        assertEquals(deadline.toString(), upstreamRequest.getHeader(WOODY_META_REQUEST_DEADLINE));
    }

    @Test
    void shouldStripHopByHopHeadersBeforeProxying() throws Exception {
        final var deadline = Instant.now().plusSeconds(60);
        final var payload = TMessageUtil.createTMessage(protocolFactory);

        stubFor(WireMock.post(urlEqualTo("/domain"))
                .withRequestBody(binaryEqualTo(payload))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())));

        final ResponseEntity<byte[]> response = restClient.post()
                .uri("/wachter")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .headers(headers -> {
                    headers.set("Authorization", "Bearer " + generateSimpleJwtWithRoles());
                    headers.set("Service", "Domain");
                    headers.set(ExternalHeaders.X_REQUEST_ID, UUID.randomUUID().toString());
                    headers.set(ExternalHeaders.X_REQUEST_DEADLINE, deadline.toString());
                    headers.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
                    headers.set(HttpHeaders.CONNECTION, "keep-alive");
                    headers.set(HttpHeaders.TE, "trailers");
                    headers.set(HttpHeaders.HOST, "example.org");
                })
                .body(payload)
                .retrieve()
                .toEntity(byte[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(postRequestedFor(urlEqualTo("/domain"))
                .withHeader(HttpHeaders.HOST, matching("localhost:\\d+"))
                .withoutHeader(HttpHeaders.TRANSFER_ENCODING)
                .withoutHeader(HttpHeaders.TE)
                .withRequestBody(binaryEqualTo(payload)));
    }

    @Test
    void shouldReturnCorsHeadersOnSuccessfulResponse() throws Exception {
        final var deadline = Instant.now().plusSeconds(120);
        final var payload = TMessageUtil.createTMessage(protocolFactory);
        final var origin = "https://iddqd.valitydev.com";

        stubFor(WireMock.post(urlEqualTo("/domain"))
                .withRequestBody(binaryEqualTo(payload))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())));

        final ResponseEntity<byte[]> response = restClient.post()
                .uri("/wachter")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .headers(headers -> {
                    headers.set("Authorization", "Bearer " + generateSimpleJwtWithRoles());
                    headers.set("Service", "Domain");
                    headers.set(ExternalHeaders.X_REQUEST_ID, UUID.randomUUID().toString());
                    headers.set(ExternalHeaders.X_REQUEST_DEADLINE, deadline.toString());
                    headers.set(HttpHeaders.ORIGIN, origin);
                })
                .body(payload)
                .retrieve()
                .toEntity(byte[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("*", response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertNull(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void shouldReturnCorsHeadersOnErrorResponse() throws Exception {
        final var deadline = Instant.now().plusSeconds(120);
        final var payload = TMessageUtil.createTMessage(protocolFactory);
        final var origin = "https://iddqd.valitydev.com";

        stubFor(WireMock.post(urlEqualTo("/domain"))
                .withRequestBody(binaryEqualTo(payload))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.BAD_GATEWAY.value())));

        final ResponseEntity<byte[]> response = restClient.post()
                .uri("/wachter")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .headers(headers -> {
                    headers.set("Authorization", "Bearer " + generateSimpleJwtWithRoles());
                    headers.set("Service", "Domain");
                    headers.set(ExternalHeaders.X_REQUEST_ID, UUID.randomUUID().toString());
                    headers.set(ExternalHeaders.X_REQUEST_DEADLINE, deadline.toString());
                    headers.set(HttpHeaders.ORIGIN, origin);
                })
                .body(payload)
                .retrieve()
                .toEntity(byte[].class);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("*", response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertNull(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    private static JsonNode decodeJwtPayload(String jwt) {
        var parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT");
        }
        var payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        try {
            return OBJECT_MAPPER.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT payload", e);
        }
    }

    private static String extractRealm(JsonNode jwtClaims) {
        var issuerNode = jwtClaims.get("iss");
        if (issuerNode == null || issuerNode.isNull()) {
            return null;
        }
        var issuer = issuerNode.asString();
        if (issuer == null) {
            return null;
        }
        var normalized = issuer.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        var lastSlash = normalized.lastIndexOf('/');
        var realm = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        return realm.isBlank() ? null : realm;
    }
}
