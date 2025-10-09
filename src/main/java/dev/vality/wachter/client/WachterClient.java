package dev.vality.wachter.client;

import dev.vality.wachter.tracing.TraceContextHeadersExtractor;
import dev.vality.wachter.tracing.TraceContextHeadersNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class WachterClient {

    private final RestClient restClient;

    private static final byte[] EMPTY_BODY = new byte[0];

    public WachterClientResponse send(HttpServletRequest servletRequest, byte[] contentData, String url) {
        var httpMethod = resolveMethod(servletRequest);

        var proxyHeaders = ProxyHeadersExtractor.extractHeaders(servletRequest);
        var traceHeaders = TraceContextHeadersExtractor.extractHeaders();

        var httpHeaders = new HttpHeaders();
        proxyHeaders.forEach(httpHeaders::addAll);
        traceHeaders.forEach(httpHeaders::set);

        log.info("-> Send request to {} {} | headers: {}", httpMethod, url, httpHeaders);

        var requestSpec = restClient.method(httpMethod)
                .uri(url)
                .headers(h -> h.addAll(httpHeaders));

        if (!ObjectUtils.isEmpty(contentData)) {
            requestSpec = requestSpec.body(contentData);
        }

        return requestSpec.exchange((request, response) -> {
            var status = response.getStatusCode();
            log.info("<- Receive response from {} {} | status: {}, headers: {}", httpMethod, url, status,
                    response.getHeaders());
            var responseBody = Objects.requireNonNullElse(response.bodyTo(byte[].class), EMPTY_BODY);
            var responseHeaders = TraceContextHeadersNormalizer.normalizeResponseHeaders(response.getHeaders());
            return new WachterClientResponse(status, responseHeaders, responseBody);
        });
    }

    private LinkedHashMap<String, Object> getLoggedHeaders(HttpHeaders proxyHeaders,
                                                           Map<String, String> traceHeaders) {
        var loggedHeaders = new LinkedHashMap<String, Object>();
        proxyHeaders.forEach((name, values) -> {
            if (values == null || values.isEmpty()) {
                return;
            }
            loggedHeaders.put(name, values.size() == 1 ? values.getFirst() : values);
        });
        loggedHeaders.putAll(traceHeaders);
        return loggedHeaders;
    }

    private HttpMethod resolveMethod(HttpServletRequest servletRequest) {
        try {
            return HttpMethod.valueOf(servletRequest.getMethod());
        } catch (IllegalArgumentException ex) {
            return HttpMethod.POST;
        }
    }
}
