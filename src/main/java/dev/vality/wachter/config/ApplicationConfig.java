package dev.vality.wachter.config;

import dev.vality.wachter.client.WachterClient;
import dev.vality.wachter.client.WachterRequestFactory;
import dev.vality.wachter.config.properties.HttpClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ApplicationConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder, HttpClientProperties properties) {
        var connectTimeout = Duration.ofMillis(properties.getConnectTimeout());
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getSocketTimeout()));

        return builder.requestFactory(requestFactory).build();
    }

    @Bean
    public WachterClient wachterClient(RestClient restClient, WachterRequestFactory requestFactory) {
        return new WachterClient(restClient, requestFactory);
    }
}
