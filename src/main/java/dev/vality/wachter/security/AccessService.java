package dev.vality.wachter.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class AccessService {

    @Value("${wachter.auth.enabled}")
    private boolean authEnabled;

    private final RoleAccessService roleAccessService;

    public void checkUserAccess(AccessData accessData) {
        log.info("Check the {} rights to perform the operation {} in service {} for roles {} with trace_id {}",
                accessData.getUserEmail(),
                accessData.getMethodName(),
                accessData.getServiceName(),
                accessData.getTokenRoles(),
                accessData.getTraceId());
        if (authEnabled) {
            roleAccessService.checkRolesAccess(accessData);
        } else {
            log.warn("Authorization disabled. Access check was not performed for user {} " +
                            "to method {} in service {} with trace_id {}",
                    accessData.getUserEmail(),
                    accessData.getMethodName(),
                    accessData.getServiceName(),
                    accessData.getTraceId());
        }
    }

}
