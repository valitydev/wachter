package dev.vality.wachter.client;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static dev.vality.wachter.constants.HeadersConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WachterClient {

    private final HttpClient httpclient;
    private final WachterResponseHandler responseHandler;

    @SneakyThrows
    public byte[] send(HttpServletRequest request, byte[] contentData, String url) {
        HttpPost httppost = new HttpPost(url);
        setHeader(request, httppost);
        httppost.setEntity(new ByteArrayEntity(contentData));
        log.info("Send request to url {} with trace_id: {}", url, getTraceId(request));
        return httpclient.execute(httppost, responseHandler);
    }

    private void setHeader(HttpServletRequest request, HttpPost httppost) {
        var headerNames = request.getHeaderNames();
        var headers = new HashMap<String, String>();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String next = headerNames.nextElement();
                headers.put(next, request.getHeader(next));
            }
        }
        var woodyUserIdentityDeprecatedHeaders = headers.entrySet().stream()
                .filter(s -> s.getKey().startsWith(X_WOODY_META_USER_IDENTITY_PREFIX))
                .map(s -> Map.entry(
                        s.getKey().replaceAll(
                                X_WOODY_META_USER_IDENTITY_PREFIX,
                                WOODY_META_USER_IDENTITY_DEPRECATED_PREFIX),
                        s.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
        var woodyDeprecatedHeaders = headers.entrySet().stream()
                .filter(s -> s.getKey().startsWith(X_WOODY_PREFIX))
                .map(s -> Map.entry(s.getKey().replaceAll(X_WOODY_PREFIX, WOODY_DEPRECATED_PREFIX), s.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
        headers.putAll(woodyUserIdentityDeprecatedHeaders);
        headers.putAll(woodyDeprecatedHeaders);
        for (var entry : headers.entrySet()) {
            httppost.setHeader(entry.getKey(), entry.getValue());
        }
    }

    private String getTraceId(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(X_WOODY_TRACE_ID))
                .orElse(request.getHeader(WOODY_TRACE_ID_DEPRECATED));
    }
}
