package dev.vality.wachter.tracing;

import dev.vality.woody.api.flow.WFlow;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class WoodyTracingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws IOException {
        var normalizedHeaders = TraceHeaderNormalizer.normalizeRequest(request);
        var traceData = WoodyTraceContext.restore(normalizedHeaders);
        WFlow.create(() -> continueFilterChain(request, response, filterChain), traceData).run();
    }

    @SneakyThrows
    private void continueFilterChain(
            ServletRequest request,
            ServletResponse response,
            FilterChain filterChain) {
        filterChain.doFilter(request, response);
    }
}
