package dev.vality.wachter.service;

import dev.vality.wachter.client.WachterClient;
import dev.vality.wachter.mapper.ServiceMapper;
import dev.vality.wachter.security.AccessData;
import dev.vality.wachter.security.AccessService;
import dev.vality.wachter.security.JwtTokenDetailsExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

import static dev.vality.wachter.client.WachterClient.WachterClientResponse;

@RequiredArgsConstructor
@Service
public class WachterService {

    private final AccessService accessService;
    private final WachterClient wachterClient;
    private final ServiceMapper serviceMapper;
    private final MethodNameReaderService methodNameReaderService;

    @SneakyThrows
    public WachterClientResponse process(HttpServletRequest request) {
        var contentData = getContentData(request);
        var methodName = methodNameReaderService.getMethodName(contentData);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var tokenDetails = JwtTokenDetailsExtractor.extract(authentication)
                .orElseThrow(() -> new IllegalStateException("JWT authentication is required"));
        var service = serviceMapper.getService(request);
        accessService.checkUserAccess(AccessData.builder()
                .methodName(methodName)
                .userEmail(tokenDetails.email())
                .serviceName(service.getName())
                .tokenRoles(tokenDetails.roles())
                .build());
        return wachterClient.send(request, contentData, service.getUrl());
    }

    @SneakyThrows
    private byte[] getContentData(HttpServletRequest request) {
        var baos = new ByteArrayOutputStream();
        IOUtils.copy(request.getInputStream(), baos);
        return baos.toByteArray();
    }
}
