package dev.vality.wachter.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "wachter")
public class WachterProperties {

    private String serviceHeader;
    private Map<String, Service> services = new LinkedCaseInsensitiveMap<>();

    @Getter
    @Setter
    public static class Service {

        private String name;
        private String url;

    }

}
