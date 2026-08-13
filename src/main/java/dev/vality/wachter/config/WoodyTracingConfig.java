package dev.vality.wachter.config;

import dev.vality.wachter.security.ExternalPortRestrictingFilter;
import dev.vality.wachter.tracing.WoodyTracingFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WoodyTracingConfig {

    @Bean
    public FilterRegistrationBean<ExternalPortRestrictingFilter> externalPortRestrictingFilter(
            @Value("${server.port}") int apiPort) {
        var registration = new FilterRegistrationBean<>(new ExternalPortRestrictingFilter(apiPort));
        registration.setName("httpPortFilter");
        registration.setOrder(-100);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public WoodyTracingFilter woodyTracingFilter() {
        return new WoodyTracingFilter();
    }

    @Bean
    public FilterRegistrationBean<WoodyTracingFilter> woodyTracingFilterRegistration(
            WoodyTracingFilter woodyTracingFilter) {
        var registration = new FilterRegistrationBean<>(woodyTracingFilter);
        registration.setName("woodyTracingFilter");
        registration.setOrder(-50);
        registration.addUrlPatterns("/wachter");
        return registration;
    }
}
