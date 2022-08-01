package dev.vality.wachter.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotEmpty;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "bouncer")
public class BouncerProperties {

    @NotEmpty
    private String deploymentId;
    @NotEmpty
    private String authMethod;
    @NotEmpty
    private String realm;
    @NotEmpty
    private String ruleSetId;
    @NotEmpty
    private String contextFragmentId;
}
