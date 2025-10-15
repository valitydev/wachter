package dev.vality.wachter.client;

import dev.vality.woody.http.bridge.tracing.TraceContextExtractor;
import dev.vality.woody.http.bridge.tracing.TraceContextHeadersNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class WachterClient {

    private final RestClient restClient;

    private static final byte[] EMPTY_BODY = new byte[0];

    public WachterClientResponse send(HttpServletRequest servletRequest, byte[] contentData, String url) {
        var httpMethod = resolveMethod(servletRequest);

        var proxyHeaders = ProxyHeadersExtractor.extractHeaders(servletRequest);
        var traceHeaders = TraceContextExtractor.extractHeaders();

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

    private HttpMethod resolveMethod(HttpServletRequest servletRequest) {
        try {
            return HttpMethod.valueOf(servletRequest.getMethod());
        } catch (IllegalArgumentException ex) {
            return HttpMethod.POST;
        }
    }

    public record WachterClientResponse(HttpStatusCode statusCode, HttpHeaders headers, byte[] body) {
    }
}
