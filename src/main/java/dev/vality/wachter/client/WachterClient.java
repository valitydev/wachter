package dev.vality.wachter.client;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;

@Service
@RequiredArgsConstructor
public class WachterClient {

    private final HttpClient httpclient;

    @SneakyThrows
    public byte[] send(HttpServletRequest request, byte[] contentData, String url) {
        HttpPost httppost = new HttpPost(url);
        setHeader(request, httppost);
        httppost.setEntity(new ByteArrayEntity(contentData));
        HttpResponse response = httpclient.execute(httppost);
        return EntityUtils.toByteArray(response.getEntity());
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
