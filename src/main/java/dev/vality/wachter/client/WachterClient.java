package dev.vality.wachter.client;

import dev.vality.woody.api.flow.error.WUnavailableResultException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;

@Slf4j
@Service
@RequiredArgsConstructor
public class WachterClient {

    private final HttpClient httpclient;

    @SneakyThrows
    public byte[] send(HttpServletRequest request, byte[] contentData, String url) {
        HttpPost httppost = new HttpPost(url);
        setHeader(request, httppost);
        httppost.setEntity(new ByteArrayEntity(contentData));
        log.info("Send request to url {}", url);
        HttpResponse response = httpclient.execute(httppost);
        log.info("Get response {}", response);
        HttpEntity httpEntity = response.getEntity();
        int statusCode = response.getStatusLine().getStatusCode();
        HttpStatus httpStatus = HttpStatus.valueOf(statusCode);
        if (httpStatus.is2xxSuccessful()) {
            return EntityUtils.toByteArray(httpEntity);
        } else if (httpStatus.is5xxServerError()) {
            throw new WUnavailableResultException(String.format("Received 5xx error code: %s (response: %s)",
                    httpStatus, response));
        } else {
            throw new RuntimeException(String.format("Wrong status was received: %s (response: %s)",
                    httpStatus, response));
        }
    }

    private void setHeader(HttpServletRequest request, HttpPost httppost) {
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String next = headerNames.nextElement();
                httppost.setHeader(next, request.getHeader(next));
            }
        }
    }
}
