package dev.vality.wachter.client;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
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
        HttpEntity httpEntity = response.getEntity();
        log.info("Get response with entity: {}", httpEntity);
        return EntityUtils.toByteArray(httpEntity);
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
