package dev.vality.wachter.tracing;

import dev.vality.wachter.security.JwtTokenDetailsExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

import static dev.vality.wachter.tracing.TraceHeaders.*;

@UtilityClass
public class TraceHeaderNormalizer {

    public HttpHeaders normalizeRequest(HttpServletRequest request) {
        var normalized = new HashMap<String, String>();
        var names = request.getHeaderNames();
        if (names != null) {
            Collections.list(names).forEach(name -> normalizeHeader(request, normalized, name));
        }
        copy(normalized, WOODY_META_REQUEST_ID, request.getHeader(ExternalHeaders.X_REQUEST_ID));
        copy(normalized, WOODY_META_REQUEST_INVOICE_ID, request.getHeader(ExternalHeaders.X_INVOICE_ID));
        normalizeDeadline(request, normalized);
        mergeJwt(normalized);
        var headers = new HttpHeaders();
        normalized.forEach(headers::set);
        return headers;
    }

    public HttpHeaders normalizeResponse(HttpHeaders responseHeaders) {
        var normalized = new HttpHeaders();
        responseHeaders.forEach((name, values) -> {
            var lowerName = name.toLowerCase(Locale.ROOT);
            if (lowerName.startsWith(WOODY_PREFIX)) {
                normalized.addAll(toExternalName(lowerName), values);
            }
        });
        return normalized;
    }

    private void normalizeHeader(HttpServletRequest request, HashMap<String, String> headers, String name) {
        var lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.startsWith(ExternalHeaders.X_WOODY_META_PREFIX)) {
            var suffix = lowerName.substring(ExternalHeaders.X_WOODY_META_PREFIX.length());
            var metadata = suffix.startsWith("user-identity-")
                    ? "user-identity." + suffix.substring("user-identity-".length())
                    : suffix;
            copy(headers, WOODY_META_PREFIX + metadata, request.getHeader(name));
        } else if (lowerName.startsWith(ExternalHeaders.X_WOODY_PREFIX)) {
            copy(headers, WOODY_PREFIX + lowerName.substring(ExternalHeaders.X_WOODY_PREFIX.length()),
                    request.getHeader(name));
        } else if (lowerName.startsWith(WOODY_PREFIX)) {
            copy(headers, lowerName, request.getHeader(name));
        }
    }

    private void normalizeDeadline(HttpServletRequest request, HashMap<String, String> headers) {
        var deadline = request.getHeader(ExternalHeaders.X_REQUEST_DEADLINE);
        if (deadline == null || deadline.isBlank()) {
            return;
        }
        var normalized = parseDeadline(deadline).toString();
        headers.putIfAbsent(WOODY_DEADLINE, normalized);
        headers.put(WOODY_META_REQUEST_DEADLINE, normalized);
    }

    private Instant parseDeadline(String value) {
        if (value.matches("\\d+(ms|s|m)")) {
            var unit = value.endsWith("ms") ? "ms" : value.substring(value.length() - 1);
            var number = Long.parseLong(value.substring(0, value.length() - unit.length()));
            var milliseconds = switch (unit) {
                case "m" -> number * 60_000;
                case "s" -> number * 1_000;
                default -> number;
            };
            return Instant.now().plusMillis(milliseconds);
        }
        return Instant.parse(value);
    }

    private void mergeJwt(HashMap<String, String> headers) {
        JwtTokenDetailsExtractor.extract(SecurityContextHolder.getContext().getAuthentication())
                .ifPresent(details -> {
                    copy(headers, WOODY_META_ID, details.subject());
                    copy(headers, WOODY_META_USERNAME, details.preferredUsername());
                    copy(headers, WOODY_META_EMAIL, details.email());
                    copy(headers, WOODY_META_REALM, details.realm());
                });
    }

    private String toExternalName(String woodyName) {
        if (!woodyName.startsWith(WOODY_META_PREFIX)) {
            return ExternalHeaders.X_WOODY_PREFIX + woodyName.substring(WOODY_PREFIX.length());
        }
        var metadata = woodyName.substring(WOODY_META_PREFIX.length());
        if (metadata.equals("user-identity.x-request-id")) {
            return ExternalHeaders.X_REQUEST_ID;
        }
        if (metadata.equals("user-identity.x-request-deadline")) {
            return ExternalHeaders.X_REQUEST_DEADLINE;
        }
        if (metadata.equals("user-identity.x-invoice-id")) {
            return ExternalHeaders.X_INVOICE_ID;
        }
        return ExternalHeaders.X_WOODY_META_PREFIX + metadata.replace("user-identity.", "user-identity-");
    }

    private void copy(HashMap<String, String> headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.put(name, value);
        }
    }
}
