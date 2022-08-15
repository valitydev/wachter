package dev.vality.wachter.config;

import dev.vality.bouncer.decisions.ArbiterSrv;
import dev.vality.orgmanagement.AuthContextProviderSrv;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityEmailExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityIdExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityRealmExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityUsernameExtensionKit;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
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
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

@Configuration
public class ApplicationConfig {

    @Bean
    public ArbiterSrv.Iface bouncerClient(
            @Value("${bouncer.url}") Resource resource,
            @Value("${bouncer.networkTimeout}") int networkTimeout) throws IOException {
        return new THSpawnClientBuilder()
                .withNetworkTimeout(networkTimeout)
                .withAddress(resource.getURI())
                .build(ArbiterSrv.Iface.class);
    }

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
