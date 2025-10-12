package dev.vality.wachter.client;

import dev.vality.wachter.constants.TraceHeadersConstants;
import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UtilityClass
public class ProxyHeadersExtractor {

    private static final Set<String> HOP_BY_HOP_HEADERS = Stream.of(
            HttpHeaders.CONNECTION,
            HttpHeaders.PROXY_AUTHENTICATE,
            HttpHeaders.PROXY_AUTHORIZATION,
            HttpHeaders.TE,
            HttpHeaders.TRAILER,
            HttpHeaders.TRANSFER_ENCODING,
            HttpHeaders.UPGRADE,
            "keep-alive",
            "proxy-connection"
    ).map(header -> header.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

    private static final Set<String> EXCLUDED_HEADERS = Stream.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_LENGTH,
            HttpHeaders.HOST,
            HttpHeaders.COOKIE,
            HttpHeaders.SET_COOKIE,
            HttpHeaders.SET_COOKIE2,
            HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
            HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
            HttpHeaders.CACHE_CONTROL,
            HttpHeaders.PRAGMA,
            HttpHeaders.ORIGIN,
            HttpHeaders.REFERER,
            "dnt",
            "priority",
            "service",
            TraceHeadersConstants.OTEL_TRACE_PARENT,
            TraceHeadersConstants.OTEL_TRACE_PARENT,
            TraceHeadersConstants.ExternalHeaders.X_REQUEST_ID,
            TraceHeadersConstants.ExternalHeaders.X_REQUEST_DEADLINE,
            TraceHeadersConstants.ExternalHeaders.X_INVOICE_ID
    ).map(header -> header.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "cf-",
            "cdn-",
            "sec-",
            TraceHeadersConstants.WOODY_PREFIX,
            TraceHeadersConstants.ExternalHeaders.X_WOODY_PREFIX
    );

    public HttpHeaders extractHeaders(HttpServletRequest request) {
        var collected = new HttpHeaders();
        var headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return collected;
        }
        while (headerNames.hasMoreElements()) {
            var headerName = headerNames.nextElement();
            var lowerCaseName = headerName.toLowerCase(Locale.ROOT);
            if (shouldSkip(lowerCaseName)) {
                continue;
            }
            var values = request.getHeaders(headerName);
            while (values.hasMoreElements()) {
                var value = values.nextElement();
                if (StringUtils.hasText(value)) {
                    collected.add(headerName, value);
                }
            }
        }
        return collected;
    }

    private boolean shouldSkip(String lowerCaseHeaderName) {
        if (HOP_BY_HOP_HEADERS.contains(lowerCaseHeaderName) || EXCLUDED_HEADERS.contains(lowerCaseHeaderName)) {
            return true;
        }
        for (var prefix : EXCLUDED_PREFIXES) {
            if (lowerCaseHeaderName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
