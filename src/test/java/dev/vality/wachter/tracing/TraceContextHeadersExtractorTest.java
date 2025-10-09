package dev.vality.wachter.tracing;

import dev.vality.woody.api.flow.WFlow;
import dev.vality.woody.api.trace.TraceData;
import dev.vality.woody.api.trace.context.TraceContext;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityEmailExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityIdExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityRealmExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityUsernameExtensionKit;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static dev.vality.wachter.constants.TraceHeadersConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class TraceContextHeadersExtractorTest {

    private SdkTracerProvider tracerProvider;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        GlobalOpenTelemetry.resetForTest();
        tracerProvider = SdkTracerProvider.builder().build();
        final var openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GlobalOpenTelemetry.set(openTelemetry);
        tracer = openTelemetry.getTracer("test");
    }

    @AfterEach
    void tearDown() {
        TraceContext.setCurrentTraceData(null);
        GlobalOpenTelemetry.resetForTest();
        if (tracerProvider != null) {
            tracerProvider.close();
        }
    }

    @Test
    void shouldExtractWoodyHeadersFromTraceContext() {
        final var traceData = new TraceData();
        final var otelSpan = tracer.spanBuilder("test-span").startSpan();
        traceData.setOtelSpan(otelSpan);
        TraceContext.setCurrentTraceData(traceData);

        final var activeSpan = traceData.getActiveSpan();
        final var span = activeSpan.getSpan();
        span.setTraceId("trace-id");
        span.setId("span-id");
        span.setParentId("parent-id");
        span.setDeadline(Instant.parse("2030-01-01T00:00:00Z"));
        activeSpan.getCustomMetadata().putValue(UserIdentityIdExtensionKit.KEY, "user-id");
        activeSpan.getCustomMetadata().putValue(UserIdentityUsernameExtensionKit.KEY, "username");
        activeSpan.getCustomMetadata().putValue(UserIdentityEmailExtensionKit.KEY, "user@example.com");
        activeSpan.getCustomMetadata().putValue(UserIdentityRealmExtensionKit.KEY, "/realm");

        final Map<String, String> headers = TraceContextHeadersExtractor.extractHeaders();

        assertNotNull(headers);
        assertTrue(headers.containsKey(WOODY_TRACE_ID));
        assertTrue(headers.containsKey(WOODY_SPAN_ID));
        assertTrue(headers.containsKey(OTEL_TRACE_PARENT));

        otelSpan.end();
    }

    @Test
    void shouldExtractOnlyAvailableHeaders() {
        final var traceData = new TraceData();
        final var otelSpan = tracer.spanBuilder("test-span").startSpan();
        traceData.setOtelSpan(otelSpan);
        TraceContext.setCurrentTraceData(traceData);

        final var activeSpan = traceData.getActiveSpan();
        final var span = activeSpan.getSpan();
        span.setTraceId("trace-id");
        span.setId("span-id");

        final Map<String, String> headers = TraceContextHeadersExtractor.extractHeaders();

        assertEquals("trace-id", headers.get(WOODY_TRACE_ID));
        assertEquals("span-id", headers.get(WOODY_SPAN_ID));
        assertNull(headers.get(WOODY_PARENT_ID));
        assertNull(headers.get(WOODY_DEADLINE));
        assertNull(headers.get(WOODY_META_USER_IDENTITY_PREFIX + "id"));
        assertNotNull(headers.get(OTEL_TRACE_PARENT));

        otelSpan.end();
    }

    @Test
    void shouldIncludeRequestMetadata() {
        final var traceData = new TraceData();
        final var otelSpan = tracer.spanBuilder("test-span").startSpan();
        traceData.setOtelSpan(otelSpan);
        TraceContext.setCurrentTraceData(traceData);

        final var serviceSpan = traceData.getServiceSpan().getSpan();
        serviceSpan.setTraceId("trace-id");
        serviceSpan.setId("span-id");
        traceData.getActiveSpan().getCustomMetadata().putValue(X_REQUEST_ID, "request-123");
        traceData.getActiveSpan().getCustomMetadata().putValue(X_REQUEST_DEADLINE, "2030-12-31T23:59:59Z");

        final Map<String, String> headers = TraceContextHeadersExtractor.extractHeaders();

        assertEquals("request-123", headers.get(WOODY_META_REQUEST_ID));
        assertEquals("2030-12-31T23:59:59Z", headers.get(WOODY_META_REQUEST_DEADLINE));

        otelSpan.end();
    }

    @Test
    void shouldThrowWhenSpanContextIsInvalid() {
        final var traceData = new TraceData();
        traceData.setOtelSpan(Span.getInvalid());
        TraceContext.setCurrentTraceData(traceData);

        try {
            TraceContextHeadersExtractor.extractHeaders();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // Expected
        }
    }

    @Test
    void shouldNotIncludeEmptyValues() {
        final var traceData = new TraceData();
        final var otelSpan = tracer.spanBuilder("test-span").startSpan();
        traceData.setOtelSpan(otelSpan);
        TraceContext.setCurrentTraceData(traceData);

        final var activeSpan = traceData.getActiveSpan();
        final var span = activeSpan.getSpan();
        span.setTraceId("trace-id");
        span.setId("span-id");
        activeSpan.getCustomMetadata().putValue(UserIdentityIdExtensionKit.KEY, "");
        activeSpan.getCustomMetadata().putValue(UserIdentityUsernameExtensionKit.KEY, null);

        final Map<String, String> headers = TraceContextHeadersExtractor.extractHeaders();

        assertFalse(headers.containsKey(WOODY_META_USER_IDENTITY_PREFIX + "id"));
        assertFalse(headers.containsKey(WOODY_META_USER_IDENTITY_PREFIX + "username"));

        otelSpan.end();
    }

    @Test
    void shouldExtractAllUserIdentityMetadata() {
        final var traceData = new TraceData();
        final var otelSpan = tracer.spanBuilder("test-span").startSpan();
        traceData.setOtelSpan(otelSpan);
        TraceContext.setCurrentTraceData(traceData);

        final var activeSpan = traceData.getActiveSpan();
        final var span = activeSpan.getSpan();
        span.setTraceId("GZvsthKQAAA");
        span.setId("GZvsthKQBBB");
        span.setParentId("undefined");

        activeSpan.getCustomMetadata().putValue(UserIdentityIdExtensionKit.KEY, "b54a93c4-415d-4f33-a5e9-3608fd043ff4");
        activeSpan.getCustomMetadata().putValue(UserIdentityUsernameExtensionKit.KEY, "noreply@empayre.com");
        activeSpan.getCustomMetadata().putValue(UserIdentityEmailExtensionKit.KEY, "noreply@empayre.com");
        activeSpan.getCustomMetadata().putValue(UserIdentityRealmExtensionKit.KEY, "/internal");

        final Map<String, String> headers = TraceContextHeadersExtractor.extractHeaders();

        assertEquals("b54a93c4-415d-4f33-a5e9-3608fd043ff4", headers.get(WOODY_META_USER_IDENTITY_PREFIX + "id"));
        assertEquals("noreply@empayre.com", headers.get(WOODY_META_USER_IDENTITY_PREFIX + "username"));
        assertEquals("noreply@empayre.com", headers.get(WOODY_META_USER_IDENTITY_PREFIX + "email"));
        assertEquals("/internal", headers.get(WOODY_META_USER_IDENTITY_PREFIX + "realm"));

        otelSpan.end();
    }

    @Test
    void shouldGenerateValidTraceparent() {
        final var traceData = new TraceData();
        final var otelSpan = tracer.spanBuilder("test-span").startSpan();
        traceData.setOtelSpan(otelSpan);
        TraceContext.setCurrentTraceData(traceData);

        final var span = traceData.getActiveSpan().getSpan();
        span.setTraceId("trace-id");
        span.setId("span-id");

        final Map<String, String> headers = TraceContextHeadersExtractor.extractHeaders();

        final String traceparent = headers.get(OTEL_TRACE_PARENT);
        assertNotNull(traceparent);
        assertTrue(traceparent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[0-1]"));

        otelSpan.end();
    }

    @Test
    void shouldExtractComplexScenarioWithAllHeaders() {
        final var traceData = new TraceData();
        TraceContext.initNewServiceTrace(traceData, WFlow.createDefaultIdGenerator(), WFlow.createDefaultIdGenerator());

        final var otelSpan = tracer.spanBuilder("test-span").startSpan();
        traceData.setOtelSpan(otelSpan);
        TraceContext.setCurrentTraceData(traceData);

        final var activeSpan = traceData.getActiveSpan();
        final var span = activeSpan.getSpan();
        span.setTraceId("GZyWNGugAAA");
        span.setId("GZyWNGugBBB");
        span.setParentId("undefined");
        span.setDeadline(Instant.parse("2030-01-01T00:00:00Z"));

        final var metadata = activeSpan.getCustomMetadata();
        metadata.putValue(UserIdentityIdExtensionKit.KEY, "b54a93c4-415d-4f33-a5e9-3608fd043ff4");
        metadata.putValue(UserIdentityUsernameExtensionKit.KEY, "noreply@empayre.com");
        metadata.putValue(UserIdentityEmailExtensionKit.KEY, "noreply@empayre.com");
        metadata.putValue(UserIdentityRealmExtensionKit.KEY, "/internal");
        metadata.putValue(X_REQUEST_ID, "req-12345");
        metadata.putValue(X_REQUEST_DEADLINE, "2030-01-01T00:00:00Z");

        final Map<String, String> headers = TraceContextHeadersExtractor.extractHeaders();

        assertEquals("GZyWNGugAAA", headers.get(WOODY_TRACE_ID));
        assertEquals("GZyWNGugBBB", headers.get(WOODY_SPAN_ID));
        assertEquals("undefined", headers.get(WOODY_PARENT_ID));
        assertEquals("2030-01-01T00:00:00Z", headers.get(WOODY_DEADLINE));
        assertEquals("b54a93c4-415d-4f33-a5e9-3608fd043ff4", headers.get(WOODY_META_USER_IDENTITY_PREFIX + "id"));
        assertEquals("noreply@empayre.com", headers.get(WOODY_META_USER_IDENTITY_PREFIX + "username"));
        assertEquals("noreply@empayre.com", headers.get(WOODY_META_USER_IDENTITY_PREFIX + "email"));
        assertEquals("/internal", headers.get(WOODY_META_USER_IDENTITY_PREFIX + "realm"));
        assertEquals("req-12345", headers.get(WOODY_META_REQUEST_ID));
        assertEquals("2030-01-01T00:00:00Z", headers.get(WOODY_META_REQUEST_DEADLINE));
        assertNotNull(headers.get(OTEL_TRACE_PARENT));

        otelSpan.end();
    }

    @Test
    void shouldThrowWhenTraceDataIsNull() {
        TraceContext.setCurrentTraceData(null);
        TraceContext.getCurrentTraceData().setOtelSpan(null);

        assertThrows(NullPointerException.class, () -> {
            TraceContextHeadersExtractor.extractHeaders();
        });
    }

    @Test
    void shouldThrowWhenOtelSpanIsNull() {
        final var traceData = new TraceData();
        traceData.setOtelSpan(null);
        TraceContext.setCurrentTraceData(traceData);

        assertThrows(NullPointerException.class, () -> {
            TraceContextHeadersExtractor.extractHeaders();
        });
    }
}
