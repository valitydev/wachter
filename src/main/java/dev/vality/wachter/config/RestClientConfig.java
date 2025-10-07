package dev.vality.wachter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.wachter.config.properties.HttpProperties;
import dev.vality.wachter.config.http.HttpHeadersPolicy;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class RestClientConfig {

    private final HttpProperties httpProperties;
    private final HttpHeadersPolicy httpHeadersPolicy;

    @Bean
    public SSLContext sslContext() throws Exception {
        return SSLContextBuilder.create()
                .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                .build();
    }

    @Bean
    public PoolingHttpClientConnectionManager connectionManager(SSLContext sslContext) {
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(new DefaultClientTlsStrategy(
                        sslContext,
                        HostnameVerificationPolicy.CLIENT,
                        NoopHostnameVerifier.INSTANCE
                ))
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(Timeout.ofMilliseconds(httpProperties.getRequestTimeout()))
                        .build())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(httpProperties.getConnectionTimeout()))
                        .setSocketTimeout(Timeout.ofMilliseconds(httpProperties.getRequestTimeout()))
                        .build())
                .setMaxConnTotal(httpProperties.getMaxTotalPooling())
                .setMaxConnPerRoute(httpProperties.getDefaultMaxPerRoute())
                .build();
    }

    @Bean
    public RequestConfig requestConfig() {
        return RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(httpProperties.getPoolTimeout()))
                .setResponseTimeout(Timeout.ofMilliseconds(httpProperties.getRequestTimeout()))
                .build();
    }

    @Bean
    public CloseableHttpClient httpClient(
            PoolingHttpClientConnectionManager connectionManager,
            RequestConfig requestConfig) {
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .addRequestInterceptorLast((request, entity, context) ->
                        httpHeadersPolicy.getOutboundSanitizedHeaders()
                                .forEach(request::removeHeaders))
                .setConnectionManagerShared(true)
                .build();
    }

    @Bean
    public HttpComponentsClientHttpRequestFactory requestFactory(HttpClient httpClient) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(Duration.ofMillis(httpProperties.getConnectionTimeout()));
        factory.setConnectionRequestTimeout(Duration.ofMillis(httpProperties.getPoolTimeout()));
        factory.setReadTimeout(Duration.ofMillis(httpProperties.getRequestTimeout()));
        return factory;
    }

    @Bean
    public RestClient restClient(ClientHttpRequestFactory requestFactory, ObjectMapper objectMapper) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .messageConverters(converters -> updateObjectMapper(converters, objectMapper))
                .build();
    }

    private void updateObjectMapper(List<HttpMessageConverter<?>> converters, ObjectMapper objectMapper) {
        converters.stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .forEach(converter -> converter.setObjectMapper(objectMapper));
    }
}
