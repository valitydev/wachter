package dev.vality.wachter.auth;

import dev.vality.wachter.auth.utils.JwtTokenBuilder;
import dev.vality.wachter.auth.utils.KeycloakOpenIdStub;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Properties;

@Configuration
public class JwtTokenTestConfiguration {

    @Bean
    public JwtTokenBuilder jwtTokenBuilder(KeyPair keyPair) {
        return new JwtTokenBuilder(keyPair);
    }

    @Bean
    public KeyPair keyPair() throws GeneralSecurityException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }
}
