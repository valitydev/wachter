package dev.vality.wachter.config;

import dev.vality.wachter.config.properties.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Configuration
@ConditionalOnProperty(value = "auth.enabled", havingValue = "true")
public class KeycloakConfig {

    private final KeycloakProperties keycloakProperties;

    @Bean
    public KeycloakConfigResolver keycloakConfigResolver() {
        return facade -> {
            KeycloakDeployment deployment = KeycloakDeploymentBuilder.build(adapterConfig());
            deployment.setNotBefore(keycloakProperties.getNotBefore());
            return deployment;
        };
    }

    private AdapterConfig adapterConfig() {
        if (StringUtils.hasLength(keycloakProperties.getRealmPublicKeyPath())) {
            keycloakProperties.setRealmPublicKey(readKeyFromFile(keycloakProperties.getRealmPublicKeyPath()));
        }

        AdapterConfig adapterConfig = new AdapterConfig();
        adapterConfig.setRealm(keycloakProperties.getRealm());
        adapterConfig.setRealmKey(keycloakProperties.getRealmPublicKey());
        adapterConfig.setResource(keycloakProperties.getResource());
        adapterConfig.setAuthServerUrl(keycloakProperties.getAuthServerUrl());
        adapterConfig.setUseResourceRoleMappings(true);
        adapterConfig.setBearerOnly(true);
        adapterConfig.setSslRequired(keycloakProperties.getSslRequired());
        return adapterConfig;
    }

    @SneakyThrows
    private String readKeyFromFile(String filePath) {
        List<String> strings = Files.readAllLines(Paths.get(filePath));
        strings.remove(strings.size() - 1);
        strings.remove(0);
        return strings.stream().map(String::trim).collect(Collectors.joining());
    }

}
