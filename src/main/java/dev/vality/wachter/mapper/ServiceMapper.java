package dev.vality.wachter.mapper;

import dev.vality.wachter.config.properties.WachterProperties;
import dev.vality.wachter.exceptions.NotFoundException;
import dev.vality.wachter.exceptions.WachterException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServiceMapper {

    private final WachterProperties wachterProperties;

    public WachterProperties.Service getService(HttpServletRequest request) {
        if (request.getHeader(wachterProperties.getServiceHeader()) == null) {
            throw new WachterException(
                    String.format("Header \"%s\" must be set", wachterProperties.getServiceHeader()));
        }
        WachterProperties.Service service = wachterProperties.getServices()
                .get(request.getHeader(wachterProperties.getServiceHeader()));

        if (service == null) {
            throw new NotFoundException(
                    String.format("Service \"%s\" not found in configuration",
                            request.getHeader(wachterProperties.getServiceHeader())));
        }
        return service;
    }

}
