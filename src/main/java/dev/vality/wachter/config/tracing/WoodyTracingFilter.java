package dev.vality.wachter.config.tracing;

import dev.vality.wachter.constants.RequestAttributeNames;
import dev.vality.woody.api.flow.WFlow;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

public final class WoodyTracingFilter extends OncePerRequestFilter {

    private final WFlow woodyFlow;
    private final WoodyHeadersNormalizer headersNormalizer;
    private final WoodyTraceContextApplier traceContextApplier;
    private final WoodyTelemetrySupport telemetrySupport;

    public WoodyTracingFilter(WFlow woodyFlow,
                              WoodyHeadersNormalizer headersNormalizer,
                              WoodyTraceContextApplier traceContextApplier,
                              WoodyTelemetrySupport telemetrySupport) {
        this.woodyFlow = woodyFlow;
        this.headersNormalizer = headersNormalizer;
        this.traceContextApplier = traceContextApplier;
        this.telemetrySupport = telemetrySupport;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        var telemetry = telemetrySupport.startServerSpan(request);
        try {
            var normalizedWoodyHeaders = telemetry.ensureTraceparent(headersNormalizer.normalize(request));
            request.setAttribute(RequestAttributeNames.NORMALIZED_WOODY_HEADERS, normalizedWoodyHeaders);
            runWithWoodyContext(request, response, filterChain, normalizedWoodyHeaders);
            telemetry.recordResponse(response);
        } catch (Throwable t) {
            telemetry.recordException(response, t);
            throw t;
        } finally {
            telemetry.close();
        }
    }

    private void runWithWoodyContext(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain,
                                     Map<String, String> normalizedWoodyHeaders) {
        woodyFlow.createServiceFork(() -> {
                    try {
                        traceContextApplier.apply(normalizedWoodyHeaders);
                        filterChain.doFilter(request, response);
                    } catch (IOException | ServletException e) {
                        sneakyThrow(e);
                    }
                }
        ).run();
    }

    private <E extends Throwable, T> T sneakyThrow(Throwable t) throws E {
        throw (E) t;
    }
}
