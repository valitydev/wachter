package dev.vality.wachter.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.vality.wachter.config.AbstractKeycloakOpenIdAsWiremockConfig;
import dev.vality.wachter.testutil.TMessageUtil;
import dev.vality.woody.api.trace.context.TraceContext;
import org.apache.thrift.protocol.TProtocolFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static dev.vality.wachter.constants.TraceHeadersConstants.*;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties = {
        "wachter.services.Domain.url=http://localhost:${wiremock.server.port}/domain",
        "wachter.services.Deanonimus.url=http://localhost:${wiremock.server.port}/deanonimus",
        "wachter.services.MerchantStatistics.url=http://localhost:${wiremock.server.port}/magista"
})
class WachterIntegrationTest extends AbstractKeycloakOpenIdAsWiremockConfig {

    private static final String TRACEPARENT_PATTERN = "00-[0-9a-f]{32}-[0-9a-f]{16}-0[0-1]";

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @Autowired
    private TProtocolFactory protocolFactory;

    @BeforeEach
    void setUpRestClient() {
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
        TraceContext.setCurrentTraceData(null);
        resetAllRequests();
    }

    @Test
    void shouldProxyRequestWithCompleteTracingHeaders() throws Exception {
        final var traceId = "GZyWNGugAAA";
        final var spanId = "GZyWNGugBBB";
        final var parentId = "undefined";
        final var deadline = Instant.now().plusSeconds(300);
        final var requestId = UUID.randomUUID().toString();
        final var payload = TMessageUtil.createTMessage(protocolFactory);
        final var responseBody = "integration-response".getBytes();
        final var upstreamTraceparent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01";

        stubFor(post(urlEqualTo("/deanonimus"))
                .withRequestBody(binaryEqualTo(payload))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("traceparent", upstreamTraceparent)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/x-thrift")
                        .withBody(responseBody)));

        final ResponseEntity<byte[]> response = restClient.post()
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
                    headers.set("origin", "https://iddqd.empayre.com");
                    headers.set("pragma", "no-cache");
                    headers.set("priority", "u=4");
                    headers.set("referer", "https://iddqd.empayre.com/");
                    headers.set("sec-fetch-dest", "empty");
                    headers.set("sec-fetch-mode", "cors");
                    headers.set("sec-fetch-site", "same-site");
                    headers.set("sec-gpc", "1");
                    headers.set("x-forwarded-proto", "https");

                    // Authentication and service
                    headers.set("Authorization", "Bearer " + generateSimpleJwtWithRoles());
                    headers.set("Service", "Deanonimus");

                    // Woody tracing headers
                    headers.set("x-woody-trace-id", traceId);
                    headers.set("x-woody-span-id", spanId);
                    headers.set("x-woody-parent-id", parentId);
                    headers.set("x-woody-meta-user-identity-id", "b54a93c4-415d-4f33-a5e9-3608fd043ff4");
                    headers.set("x-woody-meta-user-identity-username", "e.cherniak@empayre.com");
                    headers.set("x-woody-meta-user-identity-email", "e.cherniak@empayre.com");
                    headers.set("x-woody-meta-user-identity-realm", "internal");

                    // Request metadata
                    headers.set(X_REQUEST_ID, requestId);
                    headers.set(X_REQUEST_DEADLINE, deadline.toString());
                })
                .body(payload)
                .retrieve()
                .toEntity(byte[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(upstreamTraceparent, response.getHeaders().getFirst("traceparent"));
        assertArrayEquals(responseBody, response.getBody());

        // Verify upstream request headers
        List<LoggedRequest> requests = findAll(postRequestedFor(urlEqualTo("/deanonimus")));
        assertEquals(1, requests.size());
        LoggedRequest upstreamRequest = requests.get(0);

        // Verify woody tracing headers were sent
        assertEquals(traceId, upstreamRequest.getHeader(WOODY_TRACE_ID));
        assertEquals(spanId, upstreamRequest.getHeader(WOODY_SPAN_ID));
        assertEquals(parentId, upstreamRequest.getHeader(WOODY_PARENT_ID));
        assertTrue(upstreamRequest.containsHeader(WOODY_DEADLINE));

        // Verify user identity metadata was sent (from headers, not JWT because headers take precedence)
        assertEquals("b54a93c4-415d-4f33-a5e9-3608fd043ff4",
                upstreamRequest.getHeader(WOODY_META_USER_IDENTITY_PREFIX + "id"));
        assertEquals("e.cherniak@empayre.com", upstreamRequest.getHeader(WOODY_META_USER_IDENTITY_PREFIX + "username"));
        assertEquals("e.cherniak@empayre.com", upstreamRequest.getHeader(WOODY_META_USER_IDENTITY_PREFIX + "email"));
        assertEquals("internal", upstreamRequest.getHeader(WOODY_META_USER_IDENTITY_PREFIX + "realm"));

        // Verify OpenTelemetry traceparent was sent
        assertNotNull(upstreamRequest.getHeader(OTEL_TRACE_PARENT));
        assertTrue(upstreamRequest.getHeader(OTEL_TRACE_PARENT).matches(TRACEPARENT_PATTERN));

        // Verify request metadata was sent
        assertEquals(requestId, upstreamRequest.getHeader(X_REQUEST_ID));
        assertEquals(deadline.toString(), upstreamRequest.getHeader(X_REQUEST_DEADLINE));

        // Verify browser/CDN headers were NOT sent
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
                    headers.set("Authorization", "Bearer " + generateSimpleJwtWithRoles());
                    headers.set("Service", "Domain");

                    // Mixed woody and x-woody headers
                    headers.set("woody.trace-id", "GZvsthKQAAA");
                    headers.set("x-woody-span-id", "GZvsthKQBBB");
                    headers.set("woody.parent-id", "parent-woody");
                    headers.set("x-woody-deadline", deadline.toString());

                    // User identity in different formats
                    headers.set("woody.meta.user-identity.realm", "/woody-realm");
                    headers.set("x-woody-meta-user-identity-id", "header-user-id");

                    // Request metadata
                    headers.set(X_REQUEST_ID, "mixed-request-id");
                    headers.set(X_REQUEST_DEADLINE, deadline.toString());

                    // Traceparent
                    headers.set("traceparent", "00-abc123def456789012345678901234-fedcba9876543210-01");
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

        // User identity should combine headers and JWT data (header-user-id from header, others from JWT)
        assertEquals("header-user-id", upstreamRequest.getHeader(WOODY_META_USER_IDENTITY_PREFIX + "id"));
        assertEquals("Darth Vader", upstreamRequest.getHeader(WOODY_META_USER_IDENTITY_PREFIX + "username"));
        assertEquals("darkside-the-best@mail.com",
                upstreamRequest.getHeader(WOODY_META_USER_IDENTITY_PREFIX + "email"));
        assertEquals("/woody-realm", upstreamRequest.getHeader(WOODY_META_USER_IDENTITY_PREFIX + "realm"));

        // Traceparent should be preserved
        assertEquals("00-abc123def456789012345678901234-fedcba9876543210-01",
                upstreamRequest.getHeader(OTEL_TRACE_PARENT));

        // Request metadata should be preserved
        assertEquals("mixed-request-id", upstreamRequest.getHeader(X_REQUEST_ID));
        assertEquals(deadline.toString(), upstreamRequest.getHeader(X_REQUEST_DEADLINE));
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
                    headers.set(X_REQUEST_ID, UUID.randomUUID().toString());
                    headers.set(X_REQUEST_DEADLINE, deadline.toString());
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
                    headers.set(X_REQUEST_ID, UUID.randomUUID().toString());
                    headers.set(X_REQUEST_DEADLINE, deadline.toString());
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
                    headers.set(X_REQUEST_ID, UUID.randomUUID().toString());
                    headers.set(X_REQUEST_DEADLINE, deadline.toString());
                    headers.set(HttpHeaders.ORIGIN, origin);
                })
                .body(payload)
                .retrieve()
                .toEntity(byte[].class);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("*", response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertNull(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }
}
