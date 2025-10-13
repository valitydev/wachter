package dev.vality.wachter.config;

import dev.vality.wachter.config.properties.TracingProperties;
import dev.vality.wachter.tracing.WoodyTraceLifecycleHandler;
import dev.vality.wachter.tracing.WoodyTracingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@Slf4j
public class WebConfig {

    @Value("${server.port}")
    private int serverPort;

    @Value("/${wachter.endpoint}")
    private String wachterEndpoint;

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> externalPortRestrictingFilter() {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                var requestPath = getRequestPath(request);
                if ((request.getLocalPort() == serverPort) && !requestPath.equals(wachterEndpoint)) {
                    var status = HttpServletResponse.SC_NOT_FOUND;
                    log.warn("<- Sent [redirecting {}]: Unknown address {}", status, requestPath);
                    response.sendError(status, "Unknown address");
                    return;
                }
                filterChain.doFilter(request, response);
            }
        };

        var filterRegistrationBean = new FilterRegistrationBean<OncePerRequestFilter>();
        filterRegistrationBean.setFilter(filter);
        filterRegistrationBean.setOrder(-100);
        filterRegistrationBean.setName("externalPortRestrictingFilter");
        filterRegistrationBean.addUrlPatterns("/*");
        return filterRegistrationBean;
    }

    @Bean
    public FilterRegistrationBean<WoodyTracingFilter> woodyTracingFilter(TracingProperties tracingProperties) {
        var lifecycleHandler = new WoodyTraceLifecycleHandler();
        var filter = new WoodyTracingFilter(serverPort, wachterEndpoint, tracingProperties, lifecycleHandler);
        var registrationBean = new FilterRegistrationBean<>(filter);
        registrationBean.setOrder(-50);
        registrationBean.setName("woodyTracingFilter");
        registrationBean.addUrlPatterns(wachterEndpoint);
        return registrationBean;
    }

    public static String getRequestPath(HttpServletRequest request) {
        var servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        var requestPath = request.getRequestURI();
        if (requestPath != null && !requestPath.isBlank()) {
            return requestPath;
        }
        return "";
    }
}
