package dev.vality.wachter.tracing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.DateTimeException;

import static dev.vality.wachter.tracing.TraceHeaders.*;
import static org.junit.jupiter.api.Assertions.*;

class TraceHeaderNormalizerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNormalizeWoodyAndRequestHeadersWithoutManualOtelPropagation() {
        var request = new MockHttpServletRequest();
        request.addHeader(ExternalHeaders.X_WOODY_TRACE_ID, "trace-id");
        request.addHeader(ExternalHeaders.X_WOODY_META_PREFIX + "user-identity-id", "header-user");
        request.addHeader(ExternalHeaders.X_REQUEST_ID, "request-id");
        request.addHeader(ExternalHeaders.X_INVOICE_ID, "invoice-id");
        request.addHeader("traceparent", "00-11111111111111111111111111111111-2222222222222222-01");

        var headers = TraceHeaderNormalizer.normalizeRequest(request);

        assertAll(
                () -> assertEquals("trace-id", headers.getFirst(WOODY_TRACE_ID)),
                () -> assertEquals("header-user", headers.getFirst(WOODY_META_ID)),
                () -> assertEquals("request-id", headers.getFirst(WOODY_META_REQUEST_ID)),
                () -> assertEquals("invoice-id", headers.getFirst(WOODY_META_REQUEST_INVOICE_ID)),
                () -> assertFalse(headers.containsHeader("traceparent")));
    }

    @Test
    void shouldNormalizeAbsoluteDeadline() {
        var request = new MockHttpServletRequest();
        request.addHeader(ExternalHeaders.X_REQUEST_DEADLINE, "2030-01-02T03:04:05Z");

        var headers = TraceHeaderNormalizer.normalizeRequest(request);

        assertAll(
                () -> assertEquals("2030-01-02T03:04:05Z", headers.getFirst(WOODY_DEADLINE)),
                () -> assertEquals("2030-01-02T03:04:05Z", headers.getFirst(WOODY_META_REQUEST_DEADLINE)));
    }

    @Test
    void shouldFailFastForInvalidDeadline() {
        var request = new MockHttpServletRequest();
        request.addHeader(ExternalHeaders.X_REQUEST_DEADLINE, "tomorrow");

        assertThrows(DateTimeException.class, () -> TraceHeaderNormalizer.normalizeRequest(request));
    }

    @Test
    void shouldMergeJwtIdentityOverIncomingMetadata() {
        var request = new MockHttpServletRequest();
        request.addHeader(ExternalHeaders.X_WOODY_META_ID, "header-user");
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("jwt-user")
                .claim("preferred_username", "john")
                .claim("email", "john@example.com")
                .issuer("https://auth.example.com/realms/merchant")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        var headers = TraceHeaderNormalizer.normalizeRequest(request);

        assertAll(
                () -> assertEquals("jwt-user", headers.getFirst(WOODY_META_ID)),
                () -> assertEquals("john", headers.getFirst(WOODY_META_USERNAME)),
                () -> assertEquals("john@example.com", headers.getFirst(WOODY_META_EMAIL)),
                () -> assertEquals("merchant", headers.getFirst(WOODY_META_REALM)));
    }

    @Test
    void shouldNormalizeWoodyResponseHeadersOnly() {
        var response = new HttpHeaders();
        response.add(WOODY_TRACE_ID, "trace-id");
        response.add(WOODY_META_REQUEST_ID, "request-id");
        response.add(WOODY_META_EMAIL, "john@example.com");
        response.add("traceparent", "00-11111111111111111111111111111111-2222222222222222-01");
        response.add("content-type", "application/json");

        var headers = TraceHeaderNormalizer.normalizeResponse(response);

        assertAll(
                () -> assertEquals("trace-id", headers.getFirst(ExternalHeaders.X_WOODY_TRACE_ID)),
                () -> assertEquals("request-id", headers.getFirst(ExternalHeaders.X_REQUEST_ID)),
                () -> assertEquals("john@example.com", headers.getFirst(ExternalHeaders.X_WOODY_META_EMAIL)),
                () -> assertFalse(headers.containsHeader("traceparent")),
                () -> assertFalse(headers.containsHeader("content-type")));
    }
}
