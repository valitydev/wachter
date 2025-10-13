package dev.vality.wachter.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "woody-http-bridge.tracing")
public class TracingProperties {

    private boolean traceRestore = true;

}
