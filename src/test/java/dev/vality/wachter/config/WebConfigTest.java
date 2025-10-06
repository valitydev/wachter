package dev.vality.wachter.config;

import dev.vality.wachter.constants.RequestAttributeNames;
import dev.vality.woody.api.trace.TraceData;
import dev.vality.woody.api.trace.context.TraceContext;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityEmailExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityIdExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityRealmExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityUsernameExtensionKit;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static dev.vality.wachter.constants.HeadersConstants.*;
import static dev.vality.woody.api.trace.ContextUtils.getCustomMetadataValue;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_STATUS_CODE;
import static org.junit.jupiter.api.Assertions.*;

class WebConfigTest {

    private final WebConfig webConfig = new WebConfig();

    private static final Locale LOCALE = Locale.ROOT;

    private static final String USER_ID_SUFFIX = "id";
    private static final String USER_USERNAME_SUFFIX = "username";
    private static final String USER_EMAIL_SUFFIX = "email";
    private static final String USER_REALM_SUFFIX = "realm";

    private static final String WOODY_USER_ID_KEY = WOODY_META_USER_IDENTITY_PREFIX + USER_ID_SUFFIX;
    private static final String WOODY_USER_USERNAME_KEY = WOODY_META_USER_IDENTITY_PREFIX + USER_USERNAME_SUFFIX;
    private static final String WOODY_USER_EMAIL_KEY = WOODY_META_USER_IDENTITY_PREFIX + USER_EMAIL_SUFFIX;
    private static final String WOODY_USER_REALM_KEY = WOODY_META_USER_IDENTITY_PREFIX + USER_REALM_SUFFIX;

    private static final String HEADER_X_WOODY_TRACE_ID = X_WOODY_TRACE_ID.toUpperCase(LOCALE);
    private static final String HEADER_X_WOODY_SPAN_ID = X_WOODY_SPAN_ID.toUpperCase(LOCALE);
    private static final String HEADER_X_WOODY_PARENT_ID = X_WOODY_PARENT_ID.toUpperCase(LOCALE);
    private static final String HEADER_X_WOODY_DEADLINE = X_WOODY_DEADLINE.toUpperCase(LOCALE);
    private static final String HEADER_X_WOODY_META_USER_IDENTITY_ID =
            (X_WOODY_META_USER_IDENTITY_PREFIX + USER_ID_SUFFIX).toUpperCase(LOCALE);
    private static final String HEADER_X_WOODY_META_USER_IDENTITY_USERNAME =
            (X_WOODY_META_USER_IDENTITY_PREFIX + USER_USERNAME_SUFFIX).toUpperCase(LOCALE);
    private static final String HEADER_X_WOODY_META_USER_IDENTITY_EMAIL =
            (X_WOODY_META_USER_IDENTITY_PREFIX + USER_EMAIL_SUFFIX).toUpperCase(LOCALE);
    private static final String HEADER_X_WOODY_META_USER_IDENTITY_REALM =
            (X_WOODY_META_USER_IDENTITY_PREFIX + USER_REALM_SUFFIX).toUpperCase(LOCALE);
    private static final String HEADER_TRACEPARENT = OTEL_TRACE_PARENT.toUpperCase(LOCALE);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TraceContext.setCurrentTraceData(null);
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void shouldNormalizeXWoodyHeadersAndTraceparent() {
        final var request = new MockHttpServletRequest();
        final var traceId = "trace";
        final var spanId = "span";
        final var parentId = "parent";
        final var deadline = "2030-01-01T00:00:00Z";
        final var userId = "meta-id";
        final var userName = "meta-user";
        final var userEmail = "meta@example.com";
        final var userRealm = "meta-realm";
        final var traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-abcdef1234567890-01";

        request.addHeader(HEADER_X_WOODY_TRACE_ID, traceId);
        request.addHeader(HEADER_X_WOODY_SPAN_ID, spanId);
        request.addHeader(HEADER_X_WOODY_PARENT_ID, parentId);
        request.addHeader(HEADER_X_WOODY_DEADLINE, deadline);
        request.addHeader(HEADER_X_WOODY_META_USER_IDENTITY_ID, userId);
        request.addHeader(HEADER_X_WOODY_META_USER_IDENTITY_USERNAME, userName);
        request.addHeader(HEADER_X_WOODY_META_USER_IDENTITY_EMAIL, userEmail);
        request.addHeader(HEADER_X_WOODY_META_USER_IDENTITY_REALM, userRealm);
        request.addHeader(HEADER_TRACEPARENT, traceparent);

        final Map<String, String> normalized = webConfig.normalizeWoodyHeaders(request);

        assertEquals(traceId, normalized.get(WOODY_TRACE_ID));
        assertEquals(spanId, normalized.get(WOODY_SPAN_ID));
        assertEquals(parentId, normalized.get(WOODY_PARENT_ID));
        assertEquals(deadline, normalized.get(WOODY_DEADLINE));
        assertEquals(userId, normalized.get(WOODY_USER_ID_KEY));
        assertEquals(userName, normalized.get(WOODY_USER_USERNAME_KEY));
        assertEquals(userEmail, normalized.get(WOODY_USER_EMAIL_KEY));
        assertEquals(userRealm, normalized.get(WOODY_USER_REALM_KEY));
        assertEquals(traceparent, normalized.get(OTEL_TRACE_PARENT));

        assertThrows(UnsupportedOperationException.class, () -> normalized.put("woody.new", "value"));
    }

    @Test
    void shouldPreferWoodyHeadersOverPrefixedVariants() {
        final var request = new MockHttpServletRequest();
        final var legacyTraceId = "legacy-trace";
        final var legacySpanId = "legacy-span";
        final var legacyParentId = "legacy-parent";
        final var legacyUserId = "legacy-meta";

        final var primaryTraceId = "primary-trace";
        final var primarySpanId = "primary-span";
        final var primaryParentId = "primary-parent";
        final var primaryUserId = "primary-meta";

        request.addHeader(X_WOODY_TRACE_ID, legacyTraceId);
        request.addHeader(X_WOODY_SPAN_ID, legacySpanId);
        request.addHeader(X_WOODY_PARENT_ID, legacyParentId);
        request.addHeader(X_WOODY_META_USER_IDENTITY_PREFIX + USER_ID_SUFFIX, legacyUserId);

        request.addHeader("Woody.Trace-Id", primaryTraceId);
        request.addHeader("WOODY.SPAN-ID", primarySpanId);
        request.addHeader("WOODY.PARENT-ID", primaryParentId);
        request.addHeader(WOODY_USER_ID_KEY, primaryUserId);

        final Map<String, String> normalized = webConfig.normalizeWoodyHeaders(request);

        assertEquals(primaryTraceId, normalized.get(WOODY_TRACE_ID));
        assertEquals(primarySpanId, normalized.get(WOODY_SPAN_ID));
        assertEquals(primaryParentId, normalized.get(WOODY_PARENT_ID));
        assertEquals(primaryUserId, normalized.get(WOODY_USER_ID_KEY));
    }

    @Test
    void shouldMergeJwtClaimsAndOverrideUserIdentityHeaders() {
        final var request = new MockHttpServletRequest();
        final var headerUserId = "header-id";
        final var jwtUserId = "jwt-id";
        final var jwtUsername = "jwt-username";
        final var jwtEmail = "jwt@example.com";
        final var issuer = "http://issuer/realm";
        final var realm = "/realm";

        request.addHeader(X_WOODY_META_USER_IDENTITY_PREFIX + USER_ID_SUFFIX, headerUserId);

        final var headers = new HashMap<String, Object>();
        headers.put("alg", "none");
        final var claims = new HashMap<String, Object>();
        claims.put(JwtClaimNames.SUB, jwtUserId);
        claims.put("preferred_username", jwtUsername);
        claims.put("email", jwtEmail);
        claims.put("iss", issuer);
        final var jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), headers, claims);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        final Map<String, String> normalized = webConfig.normalizeWoodyHeaders(request);

        assertEquals(jwtUserId, normalized.get(WOODY_USER_ID_KEY));
        assertEquals(jwtUsername, normalized.get(WOODY_USER_USERNAME_KEY));
        assertEquals(jwtEmail, normalized.get(WOODY_USER_EMAIL_KEY));
        assertEquals(realm, normalized.get(WOODY_USER_REALM_KEY));
    }

    @Test
    void shouldConvertRequestDeadlineWhenWoodyDeadlineMissing() {
        final var request = new MockHttpServletRequest();
        final var deadline = Instant.parse("2035-06-01T12:30:00Z");
        request.addHeader(X_REQUEST_DEADLINE, deadline.toString());

        final Map<String, String> normalized = webConfig.normalizeWoodyHeaders(request);

        assertEquals(deadline.toString(), normalized.get(WOODY_DEADLINE));
    }

    @Test
    void shouldNotOverrideExistingWoodyDeadlineWithRequestHeader() {
        final var request = new MockHttpServletRequest();
        final var existingDeadline = "2035-06-01T12:30:00Z";
        final var requestDeadline = "2035-07-01T12:30:00Z";

        request.addHeader(WOODY_DEADLINE, existingDeadline);
        request.addHeader(X_REQUEST_DEADLINE, requestDeadline);

        final Map<String, String> normalized = webConfig.normalizeWoodyHeaders(request);

        assertEquals(existingDeadline, normalized.get(WOODY_DEADLINE));
    }

    @Test
    void shouldHandlePartialWoodyHeaders() {
        final var request = new MockHttpServletRequest();
        final var onlySpanId = "only-span";

        request.addHeader(X_WOODY_SPAN_ID, onlySpanId);
        request.addHeader("random-header", "value");

        final Map<String, String> normalized = webConfig.normalizeWoodyHeaders(request);

        assertEquals(onlySpanId, normalized.get(WOODY_SPAN_ID));
        assertFalse(normalized.containsKey(WOODY_TRACE_ID));
        assertEquals(1, normalized.size());
    }

    @Test
    void shouldReturnEmptyMapWhenHeadersAbsent() {
        final var request = new MockHttpServletRequest();

        final Map<String, String> normalized = webConfig.normalizeWoodyHeaders(request);

        assertTrue(normalized.isEmpty());
    }

    @Test
    void shouldApplyWoodyHeadersToTraceContext() {
        TraceContext.setCurrentTraceData(new TraceData());

        final var traceId = "trace";
        final var spanId = "span";
        final var parentId = "parent";
        final var deadline = "2040-12-12T10:15:30Z";
        final var userId = "identity-id";
        final var username = "identity-username";
        final var email = "identity@example.com";
        final var realm = "identity-realm";

        final var headers = new HashMap<String, String>();
        headers.put(WOODY_TRACE_ID, traceId);
        headers.put(WOODY_SPAN_ID, spanId);
        headers.put(WOODY_PARENT_ID, parentId);
        headers.put(WOODY_DEADLINE, deadline);
        headers.put(WOODY_USER_ID_KEY, userId);
        headers.put(WOODY_USER_USERNAME_KEY, username);
        headers.put(WOODY_USER_EMAIL_KEY, email);
        headers.put(WOODY_USER_REALM_KEY, realm);

        webConfig.applyWoodyHeadersToTraceContext(headers);

        final var traceData = TraceContext.getCurrentTraceData();
        final var serviceSpan = traceData.getServiceSpan().getSpan();
        assertEquals(traceId, serviceSpan.getTraceId());
        assertEquals(spanId, serviceSpan.getId());
        assertEquals(parentId, serviceSpan.getParentId());
        assertEquals(Instant.parse(deadline), serviceSpan.getDeadline());
        assertEquals(userId, getCustomMetadataValue(String.class, UserIdentityIdExtensionKit.KEY));
        assertEquals(username, getCustomMetadataValue(String.class, UserIdentityUsernameExtensionKit.KEY));
        assertEquals(email, getCustomMetadataValue(String.class, UserIdentityEmailExtensionKit.KEY));
        assertEquals(realm, getCustomMetadataValue(String.class, UserIdentityRealmExtensionKit.KEY));
    }

    @Test
    void shouldSkipApplyingContextWhenHeadersEmpty() {
        TraceContext.setCurrentTraceData(new TraceData());

        webConfig.applyWoodyHeadersToTraceContext(Map.of());

        final var traceData = TraceContext.getCurrentTraceData();
        final var serviceSpan = traceData.getServiceSpan().getSpan();
        assertNull(serviceSpan.getTraceId());
        assertNull(serviceSpan.getId());
        assertNull(serviceSpan.getParentId());
        assertNull(getCustomMetadataValue(String.class, UserIdentityIdExtensionKit.KEY));
    }

    @Test
    void shouldGenerateTraceparentWhenMissing() throws Exception {
        final var tracerProvider = SdkTracerProvider.builder().build();
        final var openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(openTelemetry);

        try {
            final var filter = webConfig.woodyFilter().getFilter();
            final var request = new MockHttpServletRequest("GET", "/wachter");
            final var response = new MockHttpServletResponse();
            final var chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            @SuppressWarnings("unchecked") final var normalized = (Map<String, String>) request.getAttribute(
                    RequestAttributeNames.NORMALIZED_WOODY_HEADERS);
            assertNotNull(normalized);
            assertTrue(normalized.containsKey(OTEL_TRACE_PARENT));
            assertFalse(normalized.get(OTEL_TRACE_PARENT).isBlank());
        } finally {
            tracerProvider.close();
        }
    }

    @Test
    void shouldPreserveExistingTraceparent() throws Exception {
        final var tracerProvider = SdkTracerProvider.builder().build();
        final var openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(openTelemetry);

        final var existingTraceparent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01";

        try {
            final var filter = webConfig.woodyFilter().getFilter();
            final var request = new MockHttpServletRequest("POST", "/wachter");
            final var response = new MockHttpServletResponse();
            request.addHeader(HEADER_TRACEPARENT, existingTraceparent);

            filter.doFilter(request, response, new MockFilterChain());

            @SuppressWarnings("unchecked") final var normalized = (Map<String, String>) request.getAttribute(
                    RequestAttributeNames.NORMALIZED_WOODY_HEADERS);
            assertNotNull(normalized);
            assertEquals(existingTraceparent, normalized.get(OTEL_TRACE_PARENT));
        } finally {
            tracerProvider.close();
        }
    }

    @Test
    void shouldMarkSpanAsErrorForServerErrorResponse() throws Exception {
        final var exporter = new CapturingSpanExporter();
        final var tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        final var openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(openTelemetry);

        try {
            final var filter = webConfig.woodyFilter().getFilter();
            final var request = new MockHttpServletRequest("GET", "/wachter");
            final var response = new MockHttpServletResponse();

            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                ((MockHttpServletResponse) servletResponse).setStatus(503);
            });

            final var spans = exporter.getSpans();
            assertEquals(1, spans.size());
            final var span = spans.getFirst();
            assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
            assertEquals(503L, span.getAttributes().get(HTTP_STATUS_CODE));
        } finally {
            tracerProvider.close();
        }
    }

    @Test
    void shouldRecordExceptionWhenFilterChainThrows() throws Exception {
        final var exporter = new CapturingSpanExporter();
        final var tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        final var openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(openTelemetry);

        try {
            final var filter = webConfig.woodyFilter().getFilter();
            final var request = new MockHttpServletRequest("GET", "/wachter");
            final var response = new MockHttpServletResponse();

            assertThrows(ServletException.class, () -> filter.doFilter(request, response,
                    (servletRequest, servletResponse) -> {
                        ((MockHttpServletResponse) servletResponse).setStatus(500);
                        throw new ServletException("boom");
                    }));

            final var spans = exporter.getSpans();
            assertEquals(1, spans.size());
            final var span = spans.getFirst();
            assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
            assertEquals(500L, span.getAttributes().get(HTTP_STATUS_CODE));
            assertTrue(span.getEvents().stream().anyMatch(event -> "exception".equals(event.getName())));
        } finally {
            tracerProvider.close();
        }
    }

    private static final class CapturingSpanExporter implements SpanExporter {

        private final List<SpanData> spans = new CopyOnWriteArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            this.spans.addAll(spans);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            spans.clear();
            return CompletableResultCode.ofSuccess();
        }

        List<SpanData> getSpans() {
            return spans;
        }
    }
}
