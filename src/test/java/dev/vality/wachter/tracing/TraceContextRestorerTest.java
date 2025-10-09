package dev.vality.wachter.tracing;

import dev.vality.woody.api.trace.TraceData;
import dev.vality.woody.api.trace.context.TraceContext;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityEmailExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityIdExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityRealmExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityUsernameExtensionKit;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static dev.vality.wachter.constants.TraceHeadersConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class TraceContextRestorerTest {

    private SdkTracerProvider tracerProvider;

    @BeforeEach
    void setUp() {
        GlobalOpenTelemetry.resetForTest();
        tracerProvider = SdkTracerProvider.builder().build();
        var openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GlobalOpenTelemetry.set(openTelemetry);
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
    void shouldRestoreWoodyTraceHeaders() {
        var headers = Map.of(
                WOODY_TRACE_ID, "GZyWNGugAAA",
                WOODY_SPAN_ID, "GZyWNGugBBB",
                WOODY_PARENT_ID, "undefined",
                WOODY_DEADLINE, "2030-01-01T00:00:00Z"
        );

        TraceData traceData = TraceContextRestorer.restoreTraceData(headers);

        assertNotNull(traceData);
        var span = traceData.getServiceSpan().getSpan();
        assertEquals("GZyWNGugAAA", span.getTraceId());
        assertEquals("GZyWNGugBBB", span.getId());
        assertEquals("undefined", span.getParentId());
        assertEquals(Instant.parse("2030-01-01T00:00:00Z"), span.getDeadline());
        assertNotNull(traceData.getServiceSpan().getSpan().getTraceId());
        assertNotNull(traceData.getServiceSpan().getSpan().getId());
        assertTrue(traceData.getOtelSpan().getSpanContext().isValid());
        assertNotNull(traceData.getOtelSpan().getSpanContext().getTraceId());
    }

    @Test
    void shouldRestoreUserIdentityMetadata() {
        var headers = new HashMap<String, String>();
        headers.put(WOODY_TRACE_ID, "trace-123");
        headers.put(WOODY_META_USER_IDENTITY_PREFIX + "id", "b54a93c4-415d-4f33-a5e9-3608fd043ff4");
        headers.put(WOODY_META_USER_IDENTITY_PREFIX + "username", "noreply@empayre.com");
        headers.put(WOODY_META_USER_IDENTITY_PREFIX + "email", "noreply@empayre.com");
        headers.put(WOODY_META_USER_IDENTITY_PREFIX + "realm", "/internal");

        TraceData traceData = TraceContextRestorer.restoreTraceData(headers);

        var metadata = traceData.getActiveSpan().getCustomMetadata();
        assertEquals("b54a93c4-415d-4f33-a5e9-3608fd043ff4", metadata.getValue(UserIdentityIdExtensionKit.KEY));
        assertEquals("noreply@empayre.com", metadata.getValue(UserIdentityUsernameExtensionKit.KEY));
        assertEquals("noreply@empayre.com", metadata.getValue(UserIdentityEmailExtensionKit.KEY));
        assertEquals("/internal", metadata.getValue(UserIdentityRealmExtensionKit.KEY));
        assertNotNull(traceData.getServiceSpan().getSpan().getTraceId());
        assertNotNull(traceData.getServiceSpan().getSpan().getId());
        assertTrue(traceData.getOtelSpan().getSpanContext().isValid());
        assertNotNull(traceData.getOtelSpan().getSpanContext().getTraceId());
    }

    @Test
    void shouldRestoreRequestMetadata() {
        var headers = Map.of(
                WOODY_TRACE_ID, "trace-123",
                X_REQUEST_ID, "req-456",
                X_REQUEST_DEADLINE, "2030-12-31T23:59:59Z"
        );

        TraceData traceData = TraceContextRestorer.restoreTraceData(headers);

        var metadata = traceData.getActiveSpan().getCustomMetadata();
        assertEquals("req-456", metadata.getValue(X_REQUEST_ID));
        assertEquals("2030-12-31T23:59:59Z", metadata.getValue(X_REQUEST_DEADLINE));
        assertNotNull(traceData.getServiceSpan().getSpan().getTraceId());
        assertNotNull(traceData.getServiceSpan().getSpan().getId());
        assertTrue(traceData.getOtelSpan().getSpanContext().isValid());
        assertNotNull(traceData.getOtelSpan().getSpanContext().getTraceId());
    }

    @Test
    void shouldRestoreTraceparentAndCreateOtelSpan() {
        var traceId = "cfa3d3072a4e3e99fc14829a65311819";
        var headers = Map.of(
                WOODY_TRACE_ID, "trace-123",
                OTEL_TRACE_PARENT, "00-" + traceId + "-6e4609576fa4d077-01"
        );

        TraceData traceData = TraceContextRestorer.restoreTraceData(headers);

        assertEquals(traceId, traceData.getOtelSpan().getSpanContext().getTraceId());
        assertNotNull(traceData.getServiceSpan().getSpan().getTraceId());
        assertNotNull(traceData.getServiceSpan().getSpan().getId());
        assertTrue(traceData.getOtelSpan().getSpanContext().isValid());
        assertNotNull(traceData.getOtelSpan().getSpanContext().getTraceId());
    }

    @Test
    void shouldHandleEmptyHeaders() {
        TraceData traceData = TraceContextRestorer.restoreTraceData(Map.of());

        assertNotNull(traceData.getServiceSpan().getSpan().getTraceId());
        assertNotNull(traceData.getServiceSpan().getSpan().getId());
        assertTrue(traceData.getOtelSpan().getSpanContext().isValid());
        assertNotNull(traceData.getOtelSpan().getSpanContext().getTraceId());
    }

    @Test
    void shouldHandlePartialHeaders() {
        var headers = Map.of(
                WOODY_TRACE_ID, "trace-123"
        );

        TraceData traceData = TraceContextRestorer.restoreTraceData(headers);

        assertEquals("trace-123", traceData.getServiceSpan().getSpan().getTraceId());
        assertNull(traceData.getServiceSpan().getSpan().getDeadline());
        assertNotNull(traceData.getServiceSpan().getSpan().getTraceId());
        assertNotNull(traceData.getServiceSpan().getSpan().getId());
        assertTrue(traceData.getOtelSpan().getSpanContext().isValid());
        assertNotNull(traceData.getOtelSpan().getSpanContext().getTraceId());
    }

    @Test
    void shouldHandleInvalidDeadline() {
        var headers = Map.of(
                WOODY_TRACE_ID, "trace-123",
                WOODY_DEADLINE, "invalid-date"
        );

        TraceData traceData = TraceContextRestorer.restoreTraceData(headers);

        assertEquals("trace-123", traceData.getServiceSpan().getSpan().getTraceId());
        assertNull(traceData.getServiceSpan().getSpan().getDeadline());
        assertNotNull(traceData.getServiceSpan().getSpan().getTraceId());
        assertNotNull(traceData.getServiceSpan().getSpan().getId());
        assertTrue(traceData.getOtelSpan().getSpanContext().isValid());
        assertNotNull(traceData.getOtelSpan().getSpanContext().getTraceId());
    }

    @Test
    void shouldHandleComplexScenarioWithAllData() {
        var headers = new HashMap<String, String>();
        headers.put(WOODY_TRACE_ID, "GZvsthKQAAA");
        headers.put(WOODY_SPAN_ID, "GZvsthKQBBB");
        headers.put(WOODY_PARENT_ID, "parent-123");
        headers.put(WOODY_DEADLINE, "2030-06-15T12:30:00Z");
        var otelTraceId = "3d8202ad198e4d37771c995246e1b356";
        headers.put(OTEL_TRACE_PARENT, "00-" + otelTraceId + "-9cfa814ae977266e-01");
        headers.put(WOODY_META_USER_IDENTITY_PREFIX + "id", "user-uuid");
        headers.put(WOODY_META_USER_IDENTITY_PREFIX + "username", "john.doe");
        headers.put(WOODY_META_USER_IDENTITY_PREFIX + "email", "john@example.com");
        headers.put(WOODY_META_USER_IDENTITY_PREFIX + "realm", "/external");
        headers.put(X_REQUEST_ID, "complex-request-id");
        headers.put(X_REQUEST_DEADLINE, "2030-06-15T13:00:00Z");

        TraceData traceData = TraceContextRestorer.restoreTraceData(headers);

        var span = traceData.getServiceSpan().getSpan();
        assertEquals("GZvsthKQAAA", span.getTraceId());
        assertEquals("GZvsthKQBBB", span.getId());
        assertEquals("parent-123", span.getParentId());
        assertEquals(Instant.parse("2030-06-15T12:30:00Z"), span.getDeadline());

        var metadata = traceData.getActiveSpan().getCustomMetadata();
        assertEquals("user-uuid", metadata.getValue(UserIdentityIdExtensionKit.KEY));
        assertEquals("john.doe", metadata.getValue(UserIdentityUsernameExtensionKit.KEY));
        assertEquals("john@example.com", metadata.getValue(UserIdentityEmailExtensionKit.KEY));
        assertEquals("/external", metadata.getValue(UserIdentityRealmExtensionKit.KEY));
        assertEquals("complex-request-id", metadata.getValue(X_REQUEST_ID));
        assertEquals("2030-06-15T13:00:00Z", metadata.getValue(X_REQUEST_DEADLINE));

        assertEquals(otelTraceId, traceData.getOtelSpan().getSpanContext().getTraceId());
        assertNotNull(traceData.getServiceSpan().getSpan().getTraceId());
        assertNotNull(traceData.getServiceSpan().getSpan().getId());
        assertTrue(traceData.getOtelSpan().getSpanContext().isValid());
        assertNotNull(traceData.getOtelSpan().getSpanContext().getTraceId());
    }

    @Test
    void shouldHandleNullAndEmptyValues() {
        var headers = new HashMap<String, String>();
        headers.put(WOODY_TRACE_ID, "trace-123");
        headers.put(WOODY_SPAN_ID, "");
        headers.put(WOODY_PARENT_ID, null);
        headers.put(WOODY_META_USER_IDENTITY_PREFIX + "id", "");
        headers.put(WOODY_META_USER_IDENTITY_PREFIX + "username", null);

        TraceData traceData = TraceContextRestorer.restoreTraceData(headers);

        var span = traceData.getServiceSpan().getSpan();
        assertEquals("trace-123", span.getTraceId());
        assertNotNull(span.getId());
        assertNotNull(span.getParentId());

        var metadata = traceData.getActiveSpan().getCustomMetadata();
        assertNull(metadata.getValue(UserIdentityIdExtensionKit.KEY));
        assertNull(metadata.getValue(UserIdentityUsernameExtensionKit.KEY));
        assertNotNull(traceData.getServiceSpan().getSpan().getTraceId());
        assertNotNull(traceData.getServiceSpan().getSpan().getId());
        assertTrue(traceData.getOtelSpan().getSpanContext().isValid());
        assertNotNull(traceData.getOtelSpan().getSpanContext().getTraceId());
    }

    @Test
    void shouldHandleInvalidTraceparentGracefully() {
        var headers = Map.of(
                WOODY_TRACE_ID, "trace-123",
                OTEL_TRACE_PARENT, "invalid-traceparent"
        );

        TraceData traceData = TraceContextRestorer.restoreTraceData(headers);

        assertEquals("trace-123", traceData.getServiceSpan().getSpan().getTraceId());
        assertNotNull(traceData.getServiceSpan().getSpan().getTraceId());
        assertNotNull(traceData.getServiceSpan().getSpan().getId());
        assertTrue(traceData.getOtelSpan().getSpanContext().isValid());
        assertNotNull(traceData.getOtelSpan().getSpanContext().getTraceId());
    }

    @Test
    void shouldRestoreMetadataWithSpecialCharacters() {
        var headers = new HashMap<String, String>();
        headers.put(WOODY_TRACE_ID, "trace-123");
        headers.put(WOODY_META_USER_IDENTITY_PREFIX + "username", "user@domain.com");
        headers.put(WOODY_META_USER_IDENTITY_PREFIX + "email", "user+test@domain.com");
        headers.put(WOODY_META_USER_IDENTITY_PREFIX + "realm", "/realm/with/slashes");
        headers.put(X_REQUEST_ID, "req-with-dashes-123");

        TraceData traceData = TraceContextRestorer.restoreTraceData(headers);

        var metadata = traceData.getActiveSpan().getCustomMetadata();
        assertEquals("user@domain.com", metadata.getValue(UserIdentityUsernameExtensionKit.KEY));
        assertEquals("user+test@domain.com", metadata.getValue(UserIdentityEmailExtensionKit.KEY));
        assertEquals("/realm/with/slashes", metadata.getValue(UserIdentityRealmExtensionKit.KEY));
        assertEquals("req-with-dashes-123", metadata.getValue(X_REQUEST_ID));
    }
}
