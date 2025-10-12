package dev.vality.wachter.tracing;

import dev.vality.woody.api.flow.WFlow;
import dev.vality.woody.api.trace.context.TraceContext;
import io.opentelemetry.semconv.HttpAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.vality.wachter.config.WebConfig.getRequestPath;
import static io.opentelemetry.api.trace.StatusCode.ERROR;
import static io.opentelemetry.api.trace.StatusCode.OK;

@Slf4j
@RequiredArgsConstructor
public final class WoodyTracingFilter extends OncePerRequestFilter {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION.toLowerCase(Locale.ROOT),
            HttpHeaders.COOKIE.toLowerCase(Locale.ROOT),
            HttpHeaders.SET_COOKIE.toLowerCase(Locale.ROOT)
    );

    private final int serverPort;
    private final String wachterEndpoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        var requestPath = getRequestPath(request);
        if ((request.getLocalPort() == serverPort) && requestPath.equals(wachterEndpoint)) {
            log.info("-> Received {} {} | params: {}, headers: {}", request.getMethod(), getRequestPath(request),
                    extractParams(request), sanitizeHeaders(request));
            var normalized = TraceContextHeadersNormalizer.normalize(request);
            var validated = TraceContextHeadersValidation.validate(normalized);
            var restoredTraceData = TraceContextRestorer.restoreTraceData(validated);
            WFlow.create(() -> doFilterWithTraceHandling(request, response, filterChain), restoredTraceData).run();
            log.info("<- Sent {} {} | status: {}, headers: {}", request.getMethod(), getRequestPath(request),
                    response.getStatus(), sanitizeResponseHeaders(response));
            return;
        }
        doFilter(request, response, filterChain);
    }

    @SneakyThrows
    private void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        filterChain.doFilter(request, response);
    }

    @SneakyThrows
    private void doFilterWithTraceHandling(HttpServletRequest request,
                                           HttpServletResponse response,
                                           FilterChain filterChain) {
        var traceData = TraceContext.getCurrentTraceData();
        var span = traceData != null ? traceData.getOtelSpan() : null;
        try {
            filterChain.doFilter(request, response);
            recordResponse(span, response);
        } catch (Throwable t) {
            recordException(span, response, t);
            throw t;
        }
    }

    private void recordResponse(io.opentelemetry.api.trace.Span span, HttpServletResponse response) {
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }
        var status = response.getStatus();
        if (status > 0) {
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, status);
            span.setStatus(status >= 500 ? ERROR : OK);
        } else {
            span.setStatus(OK);
        }
    }

    private void recordException(io.opentelemetry.api.trace.Span span,
                                 HttpServletResponse response,
                                 Throwable throwable) {
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }
        var status = response.getStatus();
        if (status > 0) {
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, status);
        }
        span.recordException(throwable);
        span.setStatus(ERROR);
    }


    public static String extractParams(HttpServletRequest servletRequest) {
        return servletRequest.getParameterMap().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private static HttpHeaders sanitizeHeaders(HttpServletRequest request) {
        var headers = new HttpHeaders();
        var collectedHeaders = collectHeaders(request);
        collectedHeaders.forEach((name, value) -> {
            if (isSensitive(name)) {
                headers.add(name, "***");
            } else {
                headers.add(name, value);
            }
        });
        return headers;
    }

    private static Map<String, String> collectHeaders(HttpServletRequest request) {
        var headers = new LinkedHashMap<String, String>();
        var headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                var name = headerNames.nextElement();
                var value = request.getHeader(name);
                if (value != null) {
                    headers.put(name, value);
                }
            }
        }
        return headers;
    }

    private static boolean isSensitive(String headerName) {
        return SENSITIVE_HEADERS.contains(headerName.toLowerCase(Locale.ROOT));
    }

    private static HttpHeaders sanitizeResponseHeaders(HttpServletResponse response) {
        var headers = new HttpHeaders();
        response.getHeaderNames().forEach(name -> {
            if (isSensitive(name)) {
                headers.add(name, "***");
            } else {
                response.getHeaders(name).forEach(value -> headers.add(name, value));
            }
        });
        return headers;
    }
}
