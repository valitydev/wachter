package dev.vality.wachter.config.tracing;

import dev.vality.woody.api.flow.WFlow;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.*;
import java.util.stream.Collectors;

import static dev.vality.wachter.config.WebConfig.getRequestPath;

@Slf4j
@RequiredArgsConstructor
public final class WoodyTracingFilter extends OncePerRequestFilter {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION.toLowerCase(Locale.ROOT),
            HttpHeaders.COOKIE.toLowerCase(Locale.ROOT),
            HttpHeaders.SET_COOKIE.toLowerCase(Locale.ROOT)
    );

    private final TraceContextHeadersNormalizer traceContextHeadersNormalizer = new TraceContextHeadersNormalizer();
    private final TraceContextRestorer traceContextApplier = new TraceContextRestorer();
    private final int serverPort;
    private final String wachterEndpoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        var requestPath = getRequestPath(request);
        if ((request.getLocalPort() == serverPort) && requestPath.equals(wachterEndpoint)) {
            var normalized = traceContextHeadersNormalizer.normalize(request);
            log.info("-> Received {} {} | params: {}, headers: {}",
                    request.getMethod(), getRequestPath(request), extractParams(request), sanitizeHeaders(request));
            var restoredTraceData = traceContextApplier.restoreTraceData(normalized);
            WFlow.create(() -> doFilter(request, response, filterChain), restoredTraceData)
                    .run();
            log.info("<- Sent {} {} | status: {}, headers: {}",
                    request.getMethod(), getRequestPath(request), response.getStatus(),
                    sanitizeResponseHeaders(response));
            return;
        }
        doFilter(request, response, filterChain);
    }

    @SneakyThrows
    private void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        filterChain.doFilter(request, response);
    }

    private static String extractParams(HttpServletRequest servletRequest) {
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
