package dev.vality.wachter.auth;

import dev.vality.wachter.auth.utils.JwtTokenBuilder;
import dev.vality.wachter.auth.utils.KeycloakOpenIdStub;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakOpenIdTestConfiguration {

    @Bean
    @SneakyThrows
    public KeycloakOpenIdStub keycloakOpenIdStub(@Value("${wiremock.server.baseUrl}/auth") String keycloakAuthServerUrl,
                                                 @Value("${spring.security.oauth2.resourceserver.jwt.realm}") String keycloakRealm,
                                                 JwtTokenBuilder jwtTokenBuilder) {
        return new KeycloakOpenIdStub(keycloakAuthServerUrl, keycloakRealm, jwtTokenBuilder);
    }
}
