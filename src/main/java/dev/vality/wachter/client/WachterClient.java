package dev.vality.wachter.client;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static dev.vality.wachter.constants.HeadersConstants.WOODY_TRACE_ID;
import static dev.vality.wachter.constants.HeadersConstants.WOODY_TRACE_ID_DEPRECATED;

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
        var woodyDeprecatedHeaders = headers.entrySet().stream()
                .filter(s -> s.getKey().startsWith("woody-"))
                .map(s -> Map.entry(s.getKey().replaceAll("woody-", "woody."), s.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
        headers.putAll(woodyDeprecatedHeaders);
        for (var entry : headers.entrySet()) {
            httppost.setHeader(entry.getKey(), entry.getValue());
        }
    }

    private String getTraceId(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(WOODY_TRACE_ID))
                .orElse(request.getHeader(WOODY_TRACE_ID_DEPRECATED));
    }
}
