package dev.vality.wachter.security;

import dev.vality.wachter.config.properties.WachterProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ForwardedRolesResolver {

    private static final String ROLES_DELIMITER = ",";

    public String resolveForwardedRoles(WachterProperties.Service service, List<String> tokenRoles) {
        var prefixes = service.getForwardedRolePrefixes();
        return tokenRoles.stream()
                .filter(role -> prefixes.stream().anyMatch(role::startsWith))
                .sorted()
                .collect(Collectors.joining(ROLES_DELIMITER));
    }
}
