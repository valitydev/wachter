package dev.vality.wachter.service;

import dev.vality.wachter.client.WachterClient;
import dev.vality.wachter.mapper.ServiceMapper;
import dev.vality.wachter.security.AccessData;
import dev.vality.wachter.security.AccessService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static dev.vality.wachter.constants.HeadersConstants.WOODY_TRACE_ID_DEPRECATED;
import static dev.vality.wachter.constants.HeadersConstants.X_WOODY_TRACE_ID;

@RequiredArgsConstructor
@Service
public class WachterService {

    private final AccessService accessService;
    private final WachterClient wachterClient;
    private final ServiceMapper serviceMapper;
    private final MethodNameReaderService methodNameReaderService;

    @SneakyThrows
    public byte[] process(HttpServletRequest request) {
        byte[] contentData = getContentData(request);
        var methodName = methodNameReaderService.getMethodName(contentData);
        var token = (JwtAuthenticationToken)SecurityContextHolder.getContext().getAuthentication();
        var service = serviceMapper.getService(request);
        accessService.checkUserAccess(AccessData.builder()
                .methodName(methodName)
                .userEmail(((Jwt)token.getPrincipal()).getClaim("email"))
                .serviceName(service.getName())
                .tokenRoles(token.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).toList())
                .traceId(getTraceId(request))
                .build());
        return wachterClient.send(request, contentData, service.getUrl());
    }

    @SneakyThrows
    private byte[] getContentData(HttpServletRequest request) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(request.getInputStream(), baos);
        return baos.toByteArray();
    }

    private String getTraceId(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(X_WOODY_TRACE_ID))
                .orElse(request.getHeader(WOODY_TRACE_ID_DEPRECATED));
    }
}
