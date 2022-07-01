package dev.vality.wachter.service;

import org.keycloak.KeycloakPrincipal;
import org.keycloak.representations.AccessToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class KeycloakService {

    public String getPartyId() {
        return ((KeycloakPrincipal) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal())
                .getName();
    }

    public AccessToken getAccessToken() {
        return ((KeycloakPrincipal) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal())
                .getKeycloakSecurityContext()
                .getToken();
    }
}
