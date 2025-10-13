package dev.vality.wachter.config;

import dev.vality.wachter.config.properties.TracingProperties;
import dev.vality.wachter.tracing.WoodyTraceResponseHandler;
import dev.vality.wachter.tracing.WoodyTracingFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class WebConfig {

    @Bean
    public FilterRegistrationBean<WoodyTracingFilter> woodyTracingFilter(TracingProperties tracingProperties) {
        var woodyTraceResponseHandler = new WoodyTraceResponseHandler();
        var filter = new WoodyTracingFilter(tracingProperties, woodyTraceResponseHandler);
        var registrationBean = new FilterRegistrationBean<>(filter);
        registrationBean.setOrder(-50);
        registrationBean.setName("woodyTracingFilter");
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }
}
