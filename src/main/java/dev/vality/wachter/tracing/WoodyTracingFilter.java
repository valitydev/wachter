package dev.vality.wachter.tracing;

import dev.vality.wachter.config.properties.TracingProperties;
import dev.vality.woody.api.flow.WFlow;
import dev.vality.woody.api.flow.error.WRuntimeException;
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

@Slf4j
@RequiredArgsConstructor
public final class WoodyTracingFilter extends OncePerRequestFilter {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION.toLowerCase(Locale.ROOT),
            HttpHeaders.COOKIE.toLowerCase(Locale.ROOT),
            HttpHeaders.SET_COOKIE.toLowerCase(Locale.ROOT)
    );

    private final int defaultServerPort;
    private final String defaultEndpoint;
    private final TracingProperties tracingProperties;
    private final WoodyTraceLifecycleHandler woodyTraceLifecycleHandler;

    @Override
    @SneakyThrows
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        if (matchesConfiguredEndpoint(request)) {
            switch (tracingProperties.getRequestHeaderMode()) {
                case OFF -> handle(request, response, filterChain);
                case WOODY_OR_X_WOODY -> handleWithTraceRestore(request, response, filterChain);
            }
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean matchesConfiguredEndpoint(HttpServletRequest request) {
        var port = request.getLocalPort();
        var path = getRequestPath(request);
        var endpoints = tracingProperties.getEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            var matched = port == defaultServerPort && path.equals(defaultEndpoint);
            log.debug("Tracing filter endpoint match (default) port={} path={} matched={}", port, path,
                    matched);
            return matched;
        }
        endpoints.forEach(endpoint -> log.debug("Tracing filter endpoint candidate port={} path={} -> "
                        + "portMatch={} pathMatch={}",
                endpoint.getPort(), endpoint.getPath(), matchesPort(endpoint.getPort(), port),
                matchesPath(endpoint.getPath(), path)));
        var matched = endpoints.stream().anyMatch(endpoint -> matchesPort(endpoint.getPort(), port)
                && matchesPath(endpoint.getPath(), path));
        log.debug("Tracing filter endpoint match port={} path={} matched={} endpoints={}", port, path, matched,
                endpoints);
        return matched;
    }

    private void handleWithTraceRestore(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) {
        var normalized = TraceContextHeadersNormalizer.normalize(request);
        var headersForTrace = TraceContextHeadersValidation.validate(normalized);
        var restoredTraceData = TraceContextRestorer.restoreTraceData(headersForTrace);
        WFlow.create(() -> {
                    logReceived(request);
                    doFilterWithTraceHandling(request, response, filterChain);
                    logSent(request, response);
                }, restoredTraceData)
                .run();
    }

    private void handle(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        new WFlow().createServiceFork(() -> {
                    logReceived(request);
                    doFilterWithTraceHandling(request, response, filterChain);
                    logSent(request, response);
                })
                .run();
    }

    @SneakyThrows
    private void doFilterWithTraceHandling(HttpServletRequest request,
                                           HttpServletResponse response,
                                           FilterChain filterChain) {
        try {
            filterChain.doFilter(request, response);
            woodyTraceLifecycleHandler.handleSuccess(response);
        } catch (WRuntimeException woodyError) {
            log.warn("Handled Woody exception during request processing", woodyError);
            woodyTraceLifecycleHandler.recordOtelSpanException(woodyError);
            if (tracingProperties.shouldPropagateErrors()) {
                throw woodyError;
            }
            woodyTraceLifecycleHandler.handleWoodyException(response, woodyError);
        } catch (Throwable unexpected) {
            log.error("Unhandled exception during request processing", unexpected);
            woodyTraceLifecycleHandler.recordOtelSpanException(unexpected);
            if (tracingProperties.shouldPropagateErrors()) {
                throw unexpected;
            }
            woodyTraceLifecycleHandler.handleUnexpectedError(response, unexpected);
        }
    }

    private void logReceived(HttpServletRequest request) {
        log.info("-> Received {} {} | params: {}, headers: {}", request.getMethod(), getRequestPath(request),
                extractParams(request), sanitizeHeaders(request));
    }

    private void logSent(HttpServletRequest request, HttpServletResponse response) {
        log.info("<- Sent {} {} | status: {}, headers: {}", request.getMethod(), getRequestPath(request),
                response.getStatus(), sanitizeResponseHeaders(response));
    }

    private boolean matchesPort(Integer configuredPort, int actualPort) {
        return configuredPort == null || configuredPort == actualPort;
    }

    private boolean matchesPath(String configuredPath, String actualPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return true;
        }
        return actualPath.equals(configuredPath);
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
