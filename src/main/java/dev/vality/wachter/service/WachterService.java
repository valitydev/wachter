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
        var partyID = keycloakService.getPartyId();
        var token = keycloakService.getAccessToken();
        var service = serviceMapper.getService(request);
        accessService.checkUserAccess(AccessData.builder()
                .operationId(methodName)
                .partyId(partyID)
                .tokenExpirationSec(token.getExp())
                .tokenId(token.getId())
                .userId(token.getSubject())
                .userEmail(token.getEmail())
                .service(service)
                .build());
        return wachterClient.send(request, contentData, service);
    }

    @SneakyThrows
    private byte[] getContentData(HttpServletRequest request) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(request.getInputStream(), baos);
        return baos.toByteArray();
    }
}
