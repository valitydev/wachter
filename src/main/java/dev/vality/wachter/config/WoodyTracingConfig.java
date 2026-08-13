package dev.vality.wachter.config;

import dev.vality.wachter.tracing.WoodyTracingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WoodyTracingConfig {

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
