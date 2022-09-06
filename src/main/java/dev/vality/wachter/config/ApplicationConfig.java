package dev.vality.wachter.config;

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    @Bean
    public HttpClient httpclient(@Value("${http-client.connectTimeout}") int connectTimeout,
                                 @Value("${http-client.connectionRequestTimeout}") int connectionRequestTimeout,
                                 @Value("${http-client.socketTimeout}") int socketTimeout) {
        return HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig
                        .custom()
                        .setConnectTimeout(connectTimeout)
                        .setConnectionRequestTimeout(connectionRequestTimeout)
                        .setSocketTimeout(socketTimeout)
                        .build())
                .addInterceptorFirst(new ContentLengthHeaderRemover())
                .build();
    }

    private static class ContentLengthHeaderRemover implements HttpRequestInterceptor {
        @Override
        public void process(HttpRequest request, HttpContext context) {
            request.removeHeaders(HTTP.CONTENT_LEN);
        }
    }

}
