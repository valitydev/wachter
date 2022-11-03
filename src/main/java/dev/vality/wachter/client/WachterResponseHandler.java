package dev.vality.wachter.client;

import dev.vality.woody.api.flow.error.WUnavailableResultException;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class WachterResponseHandler implements ResponseHandler<byte[]> {

    @Override
    public byte[] handleResponse(HttpResponse httpResponse) throws IOException {
        log.info("Get response {}", httpResponse);
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        HttpStatus httpStatus = HttpStatus.valueOf(statusCode);
        if (httpStatus.is2xxSuccessful()) {
            HttpEntity httpEntity = httpResponse.getEntity();
            return EntityUtils.toByteArray(httpEntity);
        } else if (httpStatus.is5xxServerError()) {
            throw new WUnavailableResultException(String.format("Received 5xx error code: %s (response: %s)",
                    httpStatus, httpResponse));
        } else {
            throw new RuntimeException(String.format("Wrong status was received: %s (response: %s)",
                    httpStatus, httpResponse));
        }
    }
}
