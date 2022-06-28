package dev.vality.wachter.service;

import dev.vality.bouncer.ctx.ContextFragment;
import dev.vality.orgmanagement.AuthContextProviderSrv;
import dev.vality.wachter.exeptions.OrgManagerException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrgManagerService {

    private final AuthContextProviderSrv.Iface orgManagerClient;

    public ContextFragment getUserAuthContext(String userId) {
        try {
            return orgManagerClient.getUserContext(userId);
        } catch (Exception e) {
            throw new OrgManagerException(
                    String.format("Can't get user auth context on orgManager call: userId = %s", userId),
                    e);
        }
    }
}
