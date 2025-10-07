package dev.vality.wachter.http;

import org.apache.hc.core5.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class HttpHeadersPolicy {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            HttpHeaders.HOST.toLowerCase(Locale.ROOT),
            HttpHeaders.CONTENT_LENGTH.toLowerCase(Locale.ROOT),
            HttpHeaders.TRANSFER_ENCODING.toLowerCase(Locale.ROOT),
            HttpHeaders.CONNECTION.toLowerCase(Locale.ROOT),
            HttpHeaders.TE.toLowerCase(Locale.ROOT),
            "proxy-connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "trailer",
            "upgrade"
    );

    private static final Set<String> OUTBOUND_SANITIZED_HEADERS = Set.of(
            HttpHeaders.CONNECTION,
            "proxy-connection",
            "keep-alive",
            HttpHeaders.TE,
            HttpHeaders.TRANSFER_ENCODING
    );

    public boolean isHopByHopHeader(String headerName) {
        return headerName != null && HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase(Locale.ROOT));
    }

    public Set<String> getOutboundSanitizedHeaders() {
        return OUTBOUND_SANITIZED_HEADERS;
    }

    public List<String> getCorsAllowedHeaders() {
        return Collections.singletonList("*");
    }
}
