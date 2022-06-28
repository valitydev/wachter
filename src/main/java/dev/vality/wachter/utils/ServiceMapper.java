package dev.vality.wachter.utils;

import dev.vality.wachter.config.properties.WachterProperties;
import dev.vality.wachter.exeptions.WachterException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

@Component
@RequiredArgsConstructor
public class ServiceMapper {

    private final WachterProperties wachterProperties;

    public WachterProperties.Services getService(HttpServletRequest request) {
        if (request.getHeader(wachterProperties.getServiceHeader()) == null) {
            throw new WachterException(
                    String.format("Header \"%s\" must be set", wachterProperties.getServiceHeader()));
        }
        WachterProperties.Services service = wachterProperties.getServices()
                .get(request.getHeader(wachterProperties.getServiceHeader()));

        if (service == null) {
            throw new WachterException(
                    String.format("Service \"%s\" not found in configuration",
                            request.getHeader(wachterProperties.getServiceHeader())));
        }
        return service;
    }

}
