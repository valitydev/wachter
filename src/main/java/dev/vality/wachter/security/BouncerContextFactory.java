package dev.vality.wachter.security;

import dev.vality.bouncer.base.Entity;
import dev.vality.bouncer.context.v1.*;
import dev.vality.bouncer.ctx.ContextFragmentType;
import dev.vality.bouncer.decisions.Context;
import dev.vality.wachter.config.properties.BouncerProperties;
import dev.vality.wachter.service.KeycloakService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.thrift.TSerializer;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class BouncerContextFactory {

    private final BouncerProperties bouncerProperties;
    private final KeycloakService keycloakService;

    @SneakyThrows
    public Context buildContext(AccessData accessData) {
        var contextFragment = buildContextFragment(accessData);
        var serializer = new TSerializer();
        var fragment = new dev.vality.bouncer.ctx.ContextFragment()
                .setType(ContextFragmentType.v1_thrift_binary)
                .setContent(serializer.serialize(contextFragment));
        var context = new Context();
        context.putToFragments(bouncerProperties.getContextFragmentId(), fragment);
        return context;
    }

    private ContextFragment buildContextFragment(AccessData accessData) {
        var env = buildEnvironment();
        return new ContextFragment()
                .setAuth(buildAuth(accessData))
                .setEnv(env)
                .setWachter(buildWachterContext(accessData));
    }

    private Auth buildAuth(AccessData accessData) {
        var auth = new Auth();
        Set<AuthScope> authScopeSet = new HashSet<>();
        authScopeSet.add(new AuthScope()
                .setParty(new Entity().setId(accessData.getPartyId())));
        return auth.setToken(new Token().setId(accessData.getTokenId()))
                .setMethod(bouncerProperties.getAuthMethod())
                .setExpiration(Instant.ofEpochSecond(accessData.getTokenExpirationSec()).toString())
                .setScope(authScopeSet);
    }

    private Environment buildEnvironment() {
        var deployment = new Deployment()
                .setId(bouncerProperties.getDeploymentId());
        return new Environment()
                .setDeployment(deployment)
                .setNow(Instant.now().toString());
    }

    private ContextWachter buildWachterContext(AccessData accessData) {
        var resource = keycloakService.getAccessToken().getResourceAccess();
        Set<Access> access = new HashSet<>();
        resource.forEach((id, roles) -> access.add(new Access().setId(id).setRoles(roles.getRoles())));
        return new ContextWachter()
                .setOp(new WachterOperation()
                        .setId(accessData.getOperationId())
                        .setServiceName(accessData.getService().getName())
                        .setParty(new Entity().setId(accessData.getPartyId()))
                        .setAccess(access));
    }
}
