package dev.vality.wachter.config.tracing;

import dev.vality.wachter.tracing.WoodyTracingFilter;
import dev.vality.woody.api.trace.context.TraceContext;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static dev.vality.wachter.constants.TraceHeadersConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class WoodyTracingFilterTest {

    private SdkTracerProvider tracerProvider;
    private WoodyTracingFilter filter;

    @BeforeEach
    void setUp() {
        GlobalOpenTelemetry.resetForTest();
        tracerProvider = SdkTracerProvider.builder().build();
        final var openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GlobalOpenTelemetry.set(openTelemetry);
        filter = new WoodyTracingFilter(8080, "/wachter");
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
    void shouldInitializeTraceContext() throws Exception {
        final var request = new MockHttpServletRequest("POST", "/wachter");
        final var response = new MockHttpServletResponse();
        request.setLocalPort(8080);
        request.addHeader(X_WOODY_TRACE_ID, "test-trace");
        request.addHeader(X_WOODY_SPAN_ID, "test-span");

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldHandleRequestCorrectly() throws Exception {
        final var request = new MockHttpServletRequest("GET", "/wachter");
        final var response = new MockHttpServletResponse();
        request.setLocalPort(8080);

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldSetSpanStatusErrorForServerError() throws Exception {
        final var request = new MockHttpServletRequest("GET", "/wachter");
        final var response = new MockHttpServletResponse();
        request.setLocalPort(8080);
        final FilterChain chain = (req, res) -> {
            ((MockHttpServletResponse) res).setStatus(503);
        };

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
    }
}
