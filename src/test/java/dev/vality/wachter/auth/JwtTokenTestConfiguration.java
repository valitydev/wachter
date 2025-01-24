package dev.vality.wachter.auth;

import dev.vality.wachter.auth.utils.JwtTokenBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

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
