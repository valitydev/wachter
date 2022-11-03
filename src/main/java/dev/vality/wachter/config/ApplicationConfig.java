package dev.vality.wachter.config;

import dev.vality.wachter.config.properties.HttpClientProperties;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    @Bean
    public PoolingHttpClientConnectionManager poolingHttpClientConnectionManager(HttpClientProperties properties) {
        PoolingHttpClientConnectionManager result = new PoolingHttpClientConnectionManager();
        result.setMaxTotal(properties.getMaxTotalPooling());
        result.setDefaultMaxPerRoute(properties.getDefaultMaxPerRoute());
        return result;
    }

    @Bean
    public HttpClient httpclient(PoolingHttpClientConnectionManager manager, HttpClientProperties properties) {
        return HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig
                        .custom()
                        .setConnectTimeout(properties.getConnectTimeout())
                        .setConnectionRequestTimeout(properties.getConnectionRequestTimeout())
                        .setSocketTimeout(properties.getSocketTimeout())
                        .build())
                .addInterceptorFirst(new ContentLengthHeaderRemover())
                .setConnectionManager(manager)
                .build();
    }

    private static class ContentLengthHeaderRemover implements HttpRequestInterceptor {
        @Override
        public void process(HttpRequest request, HttpContext context) {
            request.removeHeaders(HTTP.CONTENT_LEN);
        }
    }

}
