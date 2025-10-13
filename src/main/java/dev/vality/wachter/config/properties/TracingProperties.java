package dev.vality.wachter.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "woody-http-bridge.tracing")
public class TracingProperties {

    private boolean traceRestore = true;
    private ResponseHeaderMode responseHeaderMode = ResponseHeaderMode.OFF;
    private List<Endpoint> endpoints = new ArrayList<>();
    private Boolean propagateErrors;

    @Getter
    @Setter
    public static class Endpoint {

        private Integer port;
        private String path;

    }

    public boolean shouldPropagateErrors() {
        if (propagateErrors != null) {
            return propagateErrors;
        }
        return responseHeaderMode == ResponseHeaderMode.OFF;
    }

    public enum ResponseHeaderMode {
        WOODY,
        X_WOODY,
        HTTP,
        OFF
    }
}
