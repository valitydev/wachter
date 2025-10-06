package dev.vality.wachter.config;

import dev.vality.wachter.config.tracing.WoodyHeadersNormalizer;
import dev.vality.wachter.config.tracing.WoodyTelemetrySupport;
import dev.vality.wachter.config.tracing.WoodyTraceContextApplier;
import dev.vality.wachter.config.tracing.WoodyTracingFilter;
import dev.vality.woody.api.flow.WFlow;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class WebConfig {

    private final WoodyHeadersNormalizer headersNormalizer = new WoodyHeadersNormalizer();
    private final WoodyTraceContextApplier traceContextApplier = new WoodyTraceContextApplier();
    private final WoodyTelemetrySupport telemetrySupport = new WoodyTelemetrySupport();

    @Bean
    public FilterRegistrationBean<WoodyTracingFilter> woodyFilter() {
        var filter = new WoodyTracingFilter(new WFlow(), headersNormalizer, traceContextApplier, telemetrySupport);
        var registrationBean = new FilterRegistrationBean<>(filter);
        registrationBean.setOrder(-50);
        registrationBean.setName("woodyFilter");
        registrationBean.addUrlPatterns("*");
        return registrationBean;
    }

    public Map<String, String> normalizeWoodyHeaders(HttpServletRequest request) {
        return headersNormalizer.normalize(request);
    }

    public void applyWoodyHeadersToTraceContext(Map<String, String> woodyHeaders) {
        traceContextApplier.apply(woodyHeaders);
    }
}
