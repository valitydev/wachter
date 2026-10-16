package dev.vality.wachter.security;

import dev.vality.wachter.config.properties.WachterProperties;
import dev.vality.wachter.tracing.TraceHeaders;
import dev.vality.woody.api.trace.ContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class UserRolesForwarder {

    private static final String ROLES_DELIMITER = ",";

    public void forwardRoles(WachterProperties.Service service, List<String> tokenRoles) {
        var prefixes = service.getForwardedRolePrefixes();
        if (prefixes.isEmpty()) {
            return;
        }
        var forwardedRoles = tokenRoles.stream()
                .filter(role -> prefixes.stream().anyMatch(role::startsWith))
                .sorted()
                .collect(Collectors.joining(ROLES_DELIMITER));
        if (forwardedRoles.isEmpty()) {
            return;
        }
        log.debug("Forwarding roles to service {}: {}", service.getName(), forwardedRoles);
        ContextUtils.setCustomMetadataValue(TraceHeaders.USER_IDENTITY_ROLES, forwardedRoles);
    }
}
