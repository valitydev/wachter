package dev.vality.wachter.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties("http-client")
public class HttpClientProperties {

    @NotNull
    private int maxTotalPooling;

    @NotNull
    private int defaultMaxPerRoute;

    @NotNull
    private int socketTimeout;

    @NotNull
    private int connectionRequestTimeout;

    @NotNull
    private int connectTimeout;

}
