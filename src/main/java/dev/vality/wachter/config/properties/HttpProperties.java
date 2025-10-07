package dev.vality.wachter.config.properties;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "http")
public class HttpProperties {

    @NotNull
    private int maxTotalPooling;

    @NotNull
    private int defaultMaxPerRoute;

    @NotNull
    private long requestTimeout;

    @NotNull
    private long poolTimeout;

    @NotNull
    private long connectionTimeout;

}
