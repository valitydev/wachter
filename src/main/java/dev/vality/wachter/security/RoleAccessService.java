package dev.vality.wachter.security;

import dev.vality.wachter.exeptions.AuthorizationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class RoleAccessService {

    private static final String ROLE_DELIMITER = ":";

    public void checkRoles(AccessData accessData) {
        if (accessData.getTokenRoles().isEmpty()) {
            throw new AuthorizationException(
                    String.format("User %s don't have roles", accessData.getUserEmail()));
        }
        for (String role : accessData.getTokenRoles()) {
            if (role.equalsIgnoreCase(getServiceAndMethodName(accessData))) {
                log.info("Rights allowed in service {} and method {} for user {}",
                        accessData.getServiceName(),
                        accessData.getMethodName(),
                        accessData.getUserEmail());
                System.out.println("1");
            } else if (role.equalsIgnoreCase(getServiceName(accessData))) {
                log.info("Rights allowed in all service {} for user {}",
                        accessData.getServiceName(),
                        accessData.getUserEmail());
                System.out.println("2");
            } else {
                System.out.println("3");
                throw new AuthorizationException(
                        String.format("User %s don't have access to %s in service %s",
                                accessData.getUserEmail(),
                                accessData.getMethodName(),
                                accessData.getServiceName()));
            }
        }
    }

    private String getServiceName(AccessData accessData) {
        return accessData.getServiceName();
    }

    private String getServiceAndMethodName(AccessData accessData) {
        return String.join(
                ROLE_DELIMITER,
                accessData.getServiceName(),
                accessData.getMethodName());
    }

}