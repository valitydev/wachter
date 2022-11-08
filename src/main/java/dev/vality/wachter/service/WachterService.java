package dev.vality.wachter.service;

import dev.vality.wachter.client.WachterClient;
import dev.vality.wachter.mapper.ServiceMapper;
import dev.vality.wachter.security.AccessData;
import dev.vality.wachter.security.AccessService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static dev.vality.wachter.constants.HeadersConstants.WOODY_TRACE_ID;

@RequiredArgsConstructor
@Service
public class WachterService {

    private final KeycloakService keycloakService;
    private final AccessService accessService;
    private final WachterClient wachterClient;
    private final ServiceMapper serviceMapper;
    private final MethodNameReaderService methodNameReaderService;

    @SneakyThrows
    public byte[] process(HttpServletRequest request) {
        byte[] contentData = getContentData(request);
        var methodName = methodNameReaderService.getMethodName(contentData);
        var token = keycloakService.getAccessToken();
        var resources = token.getResourceAccess();
        List<String> tokenRoles = new ArrayList<>();
        resources.forEach((id, role) -> tokenRoles.addAll(role.getRoles()));
        var service = serviceMapper.getService(request);
        accessService.checkUserAccess(AccessData.builder()
                .methodName(methodName)
                .userEmail(token.getEmail())
                .serviceName(service.getName())
                .tokenRoles(tokenRoles)
                .traceId(request.getHeader(WOODY_TRACE_ID))
                .build());
        return wachterClient.send(request, contentData, service.getUrl());
    }

    @SneakyThrows
    private byte[] getContentData(HttpServletRequest request) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(request.getInputStream(), baos);
        return baos.toByteArray();
    }
}
