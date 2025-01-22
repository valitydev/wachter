package dev.vality.wachter.security.converter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String principleAttribute = "preferred_username";
    private static final String resourceAttribute = "resource_access";
    private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter =
            new JwtGrantedAuthoritiesConverter();
    @Value("${spring.security.oauth2.resourceserver.jwt.resource-id}")
    private String resourceId;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                jwtGrantedAuthoritiesConverter.convert(jwt).stream(),
                extractResourceRoles(jwt).stream()
        ).collect(Collectors.toSet());
        return new JwtAuthenticationToken(
                jwt,
                authorities,
                getPrincipleClaimName(jwt)
        );
    }

    private Collection<? extends GrantedAuthority> extractResourceRoles(Jwt token) {
        if (token.getClaim(resourceAttribute) == null) {
            return Set.of();
        }
        Map<String, Object> resourceAccess = token.getClaim(resourceAttribute);
        if (resourceAccess.isEmpty()) {
            return Set.of();
        }

        return resourceAccess.values().stream()
                .map(resourceAccessInfo -> (Map<String, Object>) resourceAccessInfo)
                .flatMap(resourceAccessInfo -> ((Collection<String>) resourceAccessInfo.get("roles")).stream())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    private String getPrincipleClaimName(Jwt jwt) {
        return jwt.getClaim(principleAttribute);
    }
}
