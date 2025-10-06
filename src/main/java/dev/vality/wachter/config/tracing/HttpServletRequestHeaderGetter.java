package dev.vality.wachter.config.tracing;

import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;

final class HttpServletRequestHeaderGetter implements TextMapGetter<HttpServletRequest> {

    static final HttpServletRequestHeaderGetter INSTANCE = new HttpServletRequestHeaderGetter();

    private HttpServletRequestHeaderGetter() {
    }

    @Override
    public Iterable<String> keys(HttpServletRequest carrier) {
        if (carrier == null) {
            return Collections.emptyList();
        }
        var headerNames = carrier.getHeaderNames();
        return headerNames == null ? Collections.emptyList() : Collections.list(headerNames);
    }

    @Override
    public String get(HttpServletRequest carrier, String key) {
        if (carrier == null || key == null) {
            return null;
        }
        return carrier.getHeader(key);
    }
}
