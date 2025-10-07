package dev.vality.wachter.client;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Slf4j
public record WachterClient(RestClient restClient, WachterRequestFactory requestFactory) {

    private static final byte[] EMPTY_BODY = new byte[0];
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION.toLowerCase(Locale.ROOT),
            HttpHeaders.COOKIE.toLowerCase(Locale.ROOT),
            HttpHeaders.SET_COOKIE.toLowerCase(Locale.ROOT)
    );

    public WachterClientResponse send(HttpServletRequest servletRequest, byte[] contentData, String url) {
        var httpMethod = resolveMethod(servletRequest);
        var params = requestFactory.extract(servletRequest);

        var headers = requestFactory.buildHeaders(servletRequest);

        log.info("-> Send request to {} {} | params: {} | headers: {}",
                httpMethod, url, params, sanitizeHeaders(headers));

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

        log.info("<- Receive response from {} {} | status: {} | params: {} | headers: {}",
                httpMethod, url, result.statusCode(), params, sanitizeHeaders(result.headers()));
        return result;
    }

    private HttpMethod resolveMethod(HttpServletRequest servletRequest) {
        try {
            return HttpMethod.valueOf(servletRequest.getMethod());
        } catch (IllegalArgumentException ex) {
            return HttpMethod.POST;
        }
    }

    private HttpHeaders sanitizeHeaders(HttpHeaders headers) {
        var sanitized = new HttpHeaders();
        headers.forEach((name, values) -> {
            if (isSensitive(name)) {
                sanitized.put(name, java.util.List.of("***"));
            } else {
                sanitized.put(name, new ArrayList<>(values));
            }
        });
        return sanitized;
    }

    private boolean isSensitive(String headerName) {
        return headerName != null && SENSITIVE_HEADERS.contains(headerName.toLowerCase(Locale.ROOT));
    }
}
