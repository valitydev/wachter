package dev.vality.wachter.service;

import dev.vality.wachter.client.WachterClient;
import dev.vality.wachter.config.properties.WachterProperties;
import dev.vality.wachter.security.AccessData;
import dev.vality.wachter.security.AccessService;
import dev.vality.wachter.utils.ServiceMapper;
import lombok.RequiredArgsConstructor;
import org.apache.thrift.TException;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static dev.vality.wachter.utils.MethodNameReader.getMethodName;

@RequiredArgsConstructor
@Service
public class WachterService {

    private final KeycloakService keycloakService;
    private final AccessService accessService;
    private final WachterClient wachterClient;
    private final ServiceMapper serviceMapper;


    public byte[] process(HttpServletRequest request) throws IOException, TException {
        byte[] contentData = getContentData(request);
        var methodName = getMethodName(contentData);
        var partyID = keycloakService.getPartyId();
        var token = keycloakService.getAccessToken();
        var service = serviceMapper.getService(request);
        accessService.checkUserAccess(AccessData.builder()
                .operationId(methodName)
                .partyId(partyID)
                .tokenExpiration(token.getExp())
                .tokenId(token.getId())
                .userId(token.getSubject())
                .userEmail(token.getEmail())
                .service(service)
                .build());
        return wachterClient.send(request, contentData, service);
    }

    private byte[] getContentData(HttpServletRequest request) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(request.getInputStream(), baos);
        return baos.toByteArray();
    }

}
