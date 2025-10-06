package dev.vality.wachter.config.tracing;

import dev.vality.wachter.security.JwtTokenDetailsExtractor;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityEmailExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityIdExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityRealmExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityUsernameExtensionKit;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static dev.vality.wachter.constants.HeadersConstants.*;
import static dev.vality.wachter.utils.DeadlineUtil.*;

@Slf4j
public class WoodyHeadersNormalizer {

    public Map<String, String> normalize(HttpServletRequest request) {
        var normalized = new HashMap<String, String>();
        var headerNamesEnumeration = request.getHeaderNames();
        if (headerNamesEnumeration != null) {
            var headerNames = Collections.list(headerNamesEnumeration);
            normalizeWoodyHeaders(request, headerNames, normalized);
            normalizeOtelHeaders(request, normalized);
        }
        mergeJwtIntoHeaders(normalized);
        mergeRequestDeadline(request, normalized);
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    private void normalizeWoodyHeaders(HttpServletRequest request, List<String> headerNames,
                                       Map<String, String> headers) {
        for (var name : headerNames) {
            var lowerCase = name.toLowerCase(Locale.ROOT);
            if (!lowerCase.startsWith(WOODY_PREFIX) && !lowerCase.startsWith(X_WOODY_PREFIX)) {
                continue;
            }
            var value = request.getHeader(name);
            if (value == null) {
                continue;
            }
            if (lowerCase.startsWith(WOODY_PREFIX)) {
                headers.put(lowerCase, value);
            } else {
                var suffix = lowerCase.substring(X_WOODY_PREFIX.length());
                if (suffix.startsWith(WoodySuffixes.META_USER_IDENTITY)) {
                    var metaKey = suffix.substring(WoodySuffixes.META_USER_IDENTITY.length());
                    headers.put(WOODY_META_USER_IDENTITY_PREFIX + metaKey, value);
                } else {
                    headers.put(WOODY_PREFIX + suffix, value);
                }
            }
        }
    }

    private void normalizeOtelHeaders(HttpServletRequest request, Map<String, String> headers) {
        var traceParent = request.getHeader(OTEL_TRACE_PARENT);
        if (traceParent != null) {
            headers.put(OTEL_TRACE_PARENT, traceParent);
        }
    }

    private void mergeJwtIntoHeaders(Map<String, String> headers) {
        var tokenDetails = JwtTokenDetailsExtractor.extractFromContext(SecurityContextHolder
                .getContext()
                .getAuthentication());
        if (tokenDetails.isEmpty()) {
            return;
        }
        var details = tokenDetails.get();
        putJwtMetadata(headers, UserIdentityIdExtensionKit.KEY, details.subject());
        putJwtMetadata(headers, UserIdentityUsernameExtensionKit.KEY, details.preferredUsername());
        putJwtMetadata(headers, UserIdentityEmailExtensionKit.KEY, details.email());
        putJwtMetadata(headers, UserIdentityRealmExtensionKit.KEY, details.realm());
    }

    private void mergeRequestDeadline(HttpServletRequest request, Map<String, String> headers) {
        var requestDeadlineHeader = request.getHeader(X_REQUEST_DEADLINE);
        var requestIdHeader = request.getHeader(X_REQUEST_ID);
        if (requestDeadlineHeader == null) {
            return;
        }
        try {
            headers.putIfAbsent(WOODY_DEADLINE, getInstant(requestDeadlineHeader, requestIdHeader).toString());
        } catch (Exception e) {
            log.warn("Unable to parse 'X-Request-Deadline' header value '{}'", requestDeadlineHeader);
        }
    }

    private void putJwtMetadata(Map<String, String> headers, String extensionKey, String value) {
        var suffix = WoodySuffixes.userIdentitySuffix(extensionKey);
        if (suffix.isEmpty() || value == null || value.isEmpty()) {
            return;
        }
        headers.put(WOODY_META_USER_IDENTITY_PREFIX + suffix, value);
    }

    private Instant getInstant(String requestDeadlineHeader, String requestIdHeader) {
        if (containsRelativeValues(requestDeadlineHeader, requestIdHeader)) {
            return Instant.now()
                    .plus(extractMilliseconds(requestDeadlineHeader, requestIdHeader), ChronoUnit.MILLIS)
                    .plus(extractSeconds(requestDeadlineHeader, requestIdHeader), ChronoUnit.MILLIS)
                    .plus(extractMinutes(requestDeadlineHeader, requestIdHeader), ChronoUnit.MILLIS);
        }
        return Instant.parse(requestDeadlineHeader);
    }
}
