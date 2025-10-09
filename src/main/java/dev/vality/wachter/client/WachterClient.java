package dev.vality.wachter.client;

import dev.vality.wachter.tracing.TraceContextHeadersExtractor;
import dev.vality.wachter.tracing.TraceContextHeadersNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
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

        var headers = TraceContextHeadersExtractor.extractHeaders();

        log.info("-> Send request to {} {} | headers: {}", httpMethod, url, headers);

        var requestSpec = restClient.method(httpMethod)
                .uri(url)
                .headers(httpHeaders -> headers.forEach(httpHeaders::set));

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
}
