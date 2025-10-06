package dev.vality.wachter.client;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@Slf4j
public record WachterClient(RestClient restClient, WachterRequestFactory requestFactory) {

    private static final byte[] EMPTY_BODY = new byte[0];

    public WachterClientResponse send(HttpServletRequest servletRequest, byte[] contentData, String url) {
        var httpMethod = resolveMethod(servletRequest);
        var params = requestFactory.extract(servletRequest);
        log.info("-> Send request to {} {} | params: {}", httpMethod, url, params);

        var headers = requestFactory.buildHeaders(servletRequest);

        var requestSpec = restClient.method(httpMethod)
                .uri(url)
                .headers(httpHeaders -> httpHeaders.addAll(headers));

        if (!ObjectUtils.isEmpty(contentData)) {
            requestSpec = requestSpec.body(contentData);
        }

        var result = requestSpec.exchange((request, response) -> {
            var status = response.getStatusCode();
            var responseBody = Objects.requireNonNullElse(response.bodyTo(byte[].class), EMPTY_BODY);
            var responseHeaders = new HttpHeaders();
            responseHeaders.putAll(response.getHeaders());
            return new WachterClientResponse(status, responseHeaders, responseBody);
        });

        log.info("<- Receive response from {} {} | status: {} | params: {}",
                httpMethod, url, result.statusCode(), params);
        return result;
    }

    private HttpMethod resolveMethod(HttpServletRequest servletRequest) {
        try {
            return HttpMethod.valueOf(servletRequest.getMethod());
        } catch (IllegalArgumentException ex) {
            return HttpMethod.POST;
        }
    }
}
