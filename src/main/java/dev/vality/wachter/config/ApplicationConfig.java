package dev.vality.wachter.config;

import dev.vality.wachter.client.WachterClient;
import dev.vality.wachter.client.WachterRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ApplicationConfig {

    @Bean
    public WachterClient wachterClient(RestClient restClient, WachterRequestFactory requestFactory) {
        return new WachterClient(restClient, requestFactory);
    }
}
