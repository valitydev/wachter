package dev.vality.wachter.client;

import dev.vality.wachter.constants.RequestAttributeNames;
import dev.vality.wachter.http.HttpHeadersPolicy;
import dev.vality.woody.api.trace.TraceData;
import dev.vality.woody.api.trace.context.TraceContext;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityEmailExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityIdExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityRealmExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityUsernameExtensionKit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static dev.vality.wachter.constants.HeadersConstants.*;
import static dev.vality.woody.api.trace.ContextUtils.setCustomMetadataValue;
import static dev.vality.woody.api.trace.ContextUtils.setDeadline;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WachterRequestFactoryTest {

    private final HttpHeadersPolicy httpHeadersPolicy = new HttpHeadersPolicy();

    @AfterEach
    void tearDown() {
        TraceContext.setCurrentTraceData(null);
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldOverlayTraceContextHeadersAndMirrorLegacyVariants() {
        final var servletRequest = new MockHttpServletRequest();
        final var normalizedHeaders = new HashMap<>(Map.of(
                OTEL_TRACE_PARENT,
                "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
        ));
        servletRequest.setAttribute(RequestAttributeNames.NORMALIZED_WOODY_HEADERS, normalizedHeaders);

        final var traceData = new TraceData();
        TraceContext.setCurrentTraceData(traceData);
        final var serviceSpan = traceData.getServiceSpan().getSpan();
        serviceSpan.setTraceId("trace-id");
        serviceSpan.setId("span-id");
        serviceSpan.setParentId("parent-id");
        setDeadline(traceData.getServiceSpan(), Instant.parse("2030-01-01T00:00:00Z"));
        setCustomMetadataValue(UserIdentityIdExtensionKit.KEY, "ctx-id");
        setCustomMetadataValue(UserIdentityUsernameExtensionKit.KEY, "ctx-username");
        setCustomMetadataValue(UserIdentityEmailExtensionKit.KEY, "ctx@example.com");
        setCustomMetadataValue(UserIdentityRealmExtensionKit.KEY, "/realm");

        final var factory = new WachterRequestFactory(httpHeadersPolicy);
        HttpHeaders headers = factory.buildHeaders(servletRequest);

        assertEquals("trace-id", headers.getFirst(WOODY_TRACE_ID));
        assertEquals("trace-id", headers.getFirst(X_WOODY_TRACE_ID));
        assertEquals("span-id", headers.getFirst(WOODY_SPAN_ID));
        assertEquals("span-id", headers.getFirst(X_WOODY_SPAN_ID));
        assertEquals("parent-id", headers.getFirst(WOODY_PARENT_ID));
        assertEquals("parent-id", headers.getFirst(X_WOODY_PARENT_ID));
        assertEquals("2030-01-01T00:00:00Z", headers.getFirst(WOODY_DEADLINE));
        assertEquals("2030-01-01T00:00:00Z", headers.getFirst(X_WOODY_DEADLINE));
        final var idKey = WOODY_META_USER_IDENTITY_PREFIX + "id";
        final var mirroredIdKey = X_WOODY_META_USER_IDENTITY_PREFIX + "id";
        assertEquals("ctx-id", headers.getFirst(idKey));
        assertEquals("ctx-id", headers.getFirst(mirroredIdKey));

        final var usernameKey = WOODY_META_USER_IDENTITY_PREFIX + "username";
        final var mirroredUsernameKey = X_WOODY_META_USER_IDENTITY_PREFIX + "username";
        assertEquals("ctx-username", headers.getFirst(usernameKey));
        assertEquals("ctx-username", headers.getFirst(mirroredUsernameKey));

        final var emailKey = WOODY_META_USER_IDENTITY_PREFIX + "email";
        final var mirroredEmailKey = X_WOODY_META_USER_IDENTITY_PREFIX + "email";
        assertEquals("ctx@example.com", headers.getFirst(emailKey));
        assertEquals("ctx@example.com", headers.getFirst(mirroredEmailKey));

        final var realmKey = WOODY_META_USER_IDENTITY_PREFIX + "realm";
        final var mirroredRealmKey = X_WOODY_META_USER_IDENTITY_PREFIX + "realm";
        assertEquals("/realm", headers.getFirst(realmKey));
        assertEquals("/realm", headers.getFirst(mirroredRealmKey));
    }

    @Test
    void shouldFallbackToJwtDetailsWhenTraceContextMissingIdentity() {
        final var servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(RequestAttributeNames.NORMALIZED_WOODY_HEADERS, Map.of());

        TraceContext.setCurrentTraceData(new TraceData());

        final var jwtHeaders = new HashMap<String, Object>();
        jwtHeaders.put("alg", "none");
        final var jwtClaims = new HashMap<String, Object>();
        jwtClaims.put("sub", "jwt-subject");
        jwtClaims.put("preferred_username", "jwt-username");
        jwtClaims.put("email", "jwt@example.com");
        jwtClaims.put("iss", "http://issuer/tenant");
        final var jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), jwtHeaders, jwtClaims);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        final var factory = new WachterRequestFactory(httpHeadersPolicy);
        HttpHeaders headers = factory.buildHeaders(servletRequest);

        assertEquals("jwt-subject", headers.getFirst(WOODY_META_USER_IDENTITY_PREFIX + "id"));
        assertEquals("jwt-subject", headers.getFirst(X_WOODY_META_USER_IDENTITY_PREFIX + "id"));
        assertEquals("jwt-username", headers.getFirst(WOODY_META_USER_IDENTITY_PREFIX + "username"));
        assertEquals("jwt@example.com", headers.getFirst(WOODY_META_USER_IDENTITY_PREFIX + "email"));
        assertEquals("/tenant", headers.getFirst(WOODY_META_USER_IDENTITY_PREFIX + "realm"));
    }

    @Test
    void shouldPopulateHeadersFromJwtWhenTraceContextAbsent() {
        final var servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(RequestAttributeNames.NORMALIZED_WOODY_HEADERS, Map.of());

        final var jwtHeaders = new HashMap<String, Object>();
        jwtHeaders.put("alg", "none");
        final var jwtClaims = new HashMap<String, Object>();
        jwtClaims.put("sub", "jwt-subject");
        jwtClaims.put("preferred_username", "jwt-username");
        jwtClaims.put("email", "jwt@example.com");
        jwtClaims.put("iss", "http://issuer/tenant");
        final var jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), jwtHeaders, jwtClaims);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        final var factory = new WachterRequestFactory(httpHeadersPolicy);
        HttpHeaders headers = factory.buildHeaders(servletRequest);

        assertEquals("jwt-subject", headers.getFirst(WOODY_META_USER_IDENTITY_PREFIX + "id"));
        assertEquals("jwt-username", headers.getFirst(WOODY_META_USER_IDENTITY_PREFIX + "username"));
        assertEquals("jwt@example.com", headers.getFirst(WOODY_META_USER_IDENTITY_PREFIX + "email"));
        assertEquals("/tenant", headers.getFirst(WOODY_META_USER_IDENTITY_PREFIX + "realm"));
        assertEquals("jwt-subject", headers.getFirst(X_WOODY_META_USER_IDENTITY_PREFIX + "id"));
    }

    @Test
    void shouldPreferJwtValuesWhenTraceContextMetadataBlank() {
        final var servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(RequestAttributeNames.NORMALIZED_WOODY_HEADERS, Map.of());

        final var traceData = new TraceData();
        TraceContext.setCurrentTraceData(traceData);
        setCustomMetadataValue(UserIdentityIdExtensionKit.KEY, "   ");
        setCustomMetadataValue(UserIdentityUsernameExtensionKit.KEY, "");
        setCustomMetadataValue(UserIdentityRealmExtensionKit.KEY, "   ");

        final var jwtHeaders = new HashMap<String, Object>();
        jwtHeaders.put("alg", "none");
        final var jwtClaims = new HashMap<String, Object>();
        jwtClaims.put("sub", "jwt-subject");
        jwtClaims.put("preferred_username", "jwt-username");
        jwtClaims.put("email", "jwt@example.com");
        jwtClaims.put("iss", "http://issuer/tenant");
        final var jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), jwtHeaders, jwtClaims);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        final var factory = new WachterRequestFactory(httpHeadersPolicy);
        HttpHeaders headers = factory.buildHeaders(servletRequest);

        assertEquals("jwt-subject", headers.getFirst(WOODY_META_USER_IDENTITY_PREFIX + "id"));
        assertEquals("jwt-username", headers.getFirst(WOODY_META_USER_IDENTITY_PREFIX + "username"));
        assertEquals("jwt@example.com", headers.getFirst(WOODY_META_USER_IDENTITY_PREFIX + "email"));
        assertEquals("/tenant", headers.getFirst(WOODY_META_USER_IDENTITY_PREFIX + "realm"));
    }

    @Test
    void shouldMirrorCanonicalHeadersWhenOnlyAlternatePresent() {
        final var servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader(X_WOODY_TRACE_ID, "incoming-x-trace");
        servletRequest.setAttribute(RequestAttributeNames.NORMALIZED_WOODY_HEADERS, "unexpected");

        final var factory = new WachterRequestFactory(httpHeadersPolicy);
        HttpHeaders headers = factory.buildHeaders(servletRequest);

        assertEquals("incoming-x-trace", headers.getFirst(X_WOODY_TRACE_ID));
        assertEquals("incoming-x-trace", headers.getFirst(WOODY_TRACE_ID));
    }
}
