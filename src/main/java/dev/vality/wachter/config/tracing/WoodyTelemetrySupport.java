package dev.vality.wachter.config.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static dev.vality.wachter.constants.HeadersConstants.OTEL_TRACE_PARENT;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.*;

public class WoodyTelemetrySupport {

    private static final String INSTRUMENTATION_NAME = "dev.vality.wachter.http";

    public ServerSpanContext startServerSpan(HttpServletRequest request) {
        var openTelemetry = GlobalOpenTelemetry.get();
        var propagator = openTelemetry.getPropagators().getTextMapPropagator();
        var parentContext = propagator.extract(Context.current(), request, HttpServletRequestHeaderGetter.INSTANCE);
        var tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);
        var spanBuilder = tracer.spanBuilder(buildSpanName(request))
                .setSpanKind(SpanKind.SERVER)
                .setParent(parentContext);
        var method = request.getMethod();
        if (method != null) {
            spanBuilder.setAttribute(HTTP_METHOD, method);
        }
        var target = request.getRequestURI();
        if (target != null) {
            spanBuilder.setAttribute(HTTP_TARGET, target);
        }
        var host = request.getHeader("Host");
        if (host != null && !host.isEmpty()) {
            spanBuilder.setAttribute(NET_HOST_NAME, host);
        }
        var span = spanBuilder.startSpan();
        var scope = span.makeCurrent();
        return new ServerSpanContext(span, scope, propagator);
    }

    private String buildSpanName(HttpServletRequest request) {
        var method = request.getMethod();
        var target = request.getRequestURI();
        if (method == null && (target == null || target.isEmpty())) {
            return "HTTP request";
        }
        if (method == null) {
            return target;
        }
        if (target == null || target.isEmpty()) {
            return method;
        }
        return method + " " + target;
    }

    static final class ServerSpanContext implements AutoCloseable {

        private final Span span;
        private final Scope scope;
        private final TextMapPropagator propagator;

        private ServerSpanContext(Span span, Scope scope, TextMapPropagator propagator) {
            this.span = span;
            this.scope = scope;
            this.propagator = propagator;
        }

        Map<String, String> ensureTraceparent(Map<String, String> headers) {
            if (headers.containsKey(OTEL_TRACE_PARENT)) {
                return headers;
            }
            var mutable = new HashMap<>(headers);
            propagator.inject(Context.current(), mutable, MapHeaderSetter.INSTANCE);
            var traceparent = mutable.get(OTEL_TRACE_PARENT);
            if (traceparent == null || traceparent.isEmpty()) {
                return headers;
            }
            return Map.copyOf(mutable);
        }

        void recordResponse(HttpServletResponse response) {
            var status = response.getStatus();
            if (status > 0) {
                span.setAttribute(HTTP_STATUS_CODE, status);
                span.setStatus(status >= 500 ? StatusCode.ERROR : StatusCode.OK);
            } else {
                span.setStatus(StatusCode.OK);
            }
        }

        void recordException(HttpServletResponse response, Throwable t) {
            var status = response.getStatus();
            if (status > 0) {
                span.setAttribute(HTTP_STATUS_CODE, status);
            }
            span.recordException(t);
            span.setStatus(StatusCode.ERROR);
        }

        @Override
        public void close() {
            scope.close();
            span.end();
        }
    }
}
