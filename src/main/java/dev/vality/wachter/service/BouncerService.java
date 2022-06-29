package dev.vality.wachter.service;

import dev.vality.bouncer.decisions.ArbiterSrv;
import dev.vality.bouncer.decisions.Resolution;
import dev.vality.wachter.config.properties.BouncerProperties;
import dev.vality.wachter.exeptions.BouncerException;
import dev.vality.wachter.security.AccessData;
import dev.vality.wachter.security.BouncerContextFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BouncerService {

    private final BouncerProperties bouncerProperties;
    private final BouncerContextFactory bouncerContextFactory;
    private final ArbiterSrv.Iface bouncerClient;

    public Resolution getResolution(AccessData accessData) {
        log.debug("Check access with bouncer context");
        var context = bouncerContextFactory.buildContext(accessData);
        log.debug("Built thrift context: {}", context);
        try {
            var judge = bouncerClient.judge(bouncerProperties.getRuleSetId(), context);
            log.debug("Have judge: {}", judge);
            var resolution = judge.getResolution();
            log.debug("Resolution: {}", resolution);
            return resolution;
        } catch (TException e) {
            throw new BouncerException("Error while call bouncer", e);
        }
    }
}
