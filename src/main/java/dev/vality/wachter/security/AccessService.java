package dev.vality.wachter.security;

import dev.vality.wachter.exeptions.AuthorizationException;
import dev.vality.wachter.exeptions.BouncerException;
import dev.vality.wachter.service.BouncerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class AccessService {

    private final BouncerService bouncerService;

    @Value("${bouncer.auth.enabled}")
    private boolean authEnabled;

    public void checkUserAccess(AccessData accessData) {
        {
            log.info("Check the {} rights to perform the operation {} in service {}",
                    accessData.getUserEmail(),
                    accessData.getOperationId(),
                    accessData.getService().getName());
            var resolution = bouncerService.getResolution(accessData);
            switch (resolution.getSetField()) {
                case FORBIDDEN, RESTRICTED -> {
                    if (authEnabled) {
                        throw new AuthorizationException(
                                String.format("No rights for %s to perform %s in service %s",
                                        accessData.getUserEmail(),
                                        accessData.getOperationId(),
                                        accessData.getService().getName()));
                    } else {
                        log.warn("No rights for {} to perform {} in service {}",
                                accessData.getUserEmail(),
                                accessData.getOperationId(),
                                accessData.getService().getName());
                    }
                }
                case ALLOWED -> log.info("Rights for {} to perform {} in service {} are allowed",
                        accessData.getUserEmail(),
                        accessData.getOperationId(),
                        accessData.getService().getName());
                default -> throw new BouncerException(String.format("Resolution %s cannot be processed", resolution));
            }
        }
    }
}
