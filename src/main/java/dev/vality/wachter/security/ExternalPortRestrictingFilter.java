package dev.vality.wachter.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ExternalPortRestrictingFilter extends OncePerRequestFilter {

    private static final String WACHTER_ENDPOINT = "/wachter";

    private final int apiPort;

    public ExternalPortRestrictingFilter(int apiPort) {
        this.apiPort = apiPort;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (request.getLocalPort() == apiPort && !WACHTER_ENDPOINT.equals(request.getServletPath())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown address");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
