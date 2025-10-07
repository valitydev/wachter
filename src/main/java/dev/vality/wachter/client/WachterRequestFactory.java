package dev.vality.wachter.client;

import dev.vality.wachter.constants.RequestAttributeNames;
import dev.vality.wachter.security.JwtTokenDetailsExtractor;
import dev.vality.woody.api.trace.context.TraceContext;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityEmailExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityIdExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityRealmExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityUsernameExtensionKit;
import dev.vality.wachter.config.http.HttpHeadersPolicy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.vality.wachter.constants.HeadersConstants.*;
import static dev.vality.woody.api.trace.ContextUtils.getCustomMetadataValue;

@Component
@RequiredArgsConstructor
public class WachterRequestFactory {

    private final HttpHeadersPolicy httpHeadersPolicy;

    public HttpHeaders buildHeaders(HttpServletRequest servletRequest) {
        var headers = collectHeaders(servletRequest);
        mergeNormalizedWoodyHeaders(servletRequest, headers);
        mergeTraceContextHeaders(headers);

        var woodyIdentityMirrors = createPrefixMirrors(headers,
                X_WOODY_META_USER_IDENTITY_PREFIX, WOODY_META_USER_IDENTITY_PREFIX);
        var woodyMirrors = createPrefixMirrors(headers, X_WOODY_PREFIX, WOODY_PREFIX);
        var alternateIdentityMirrors = createPrefixMirrors(headers,
                WOODY_META_USER_IDENTITY_PREFIX, X_WOODY_META_USER_IDENTITY_PREFIX);
        var alternateWoodyMirrors = createPrefixMirrors(headers, WOODY_PREFIX, X_WOODY_PREFIX);

        headers.putAll(woodyIdentityMirrors);
        headers.putAll(woodyMirrors);
        headers.putAll(alternateIdentityMirrors);
        headers.putAll(alternateWoodyMirrors);

        var httpHeaders = new HttpHeaders();
        headers.forEach(httpHeaders::set);
        return httpHeaders;
    }

    public String extract(HttpServletRequest servletRequest) {
        return servletRequest.getParameterMap().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + String.join(", ", entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private Map<String, String> collectHeaders(HttpServletRequest servletRequest) {
        var headers = new LinkedHashMap<String, String>();
        var headerNames = servletRequest.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            var name = headerNames.nextElement();
            if (httpHeadersPolicy.shouldExcludeFromRequest(name)) {
                continue;
            }
            var value = servletRequest.getHeader(name);
            if (value != null) {
                headers.put(name, value);
            }
        }
        return headers;
    }

    private void mergeNormalizedWoodyHeaders(HttpServletRequest servletRequest, Map<String, String> headers) {
        var normalized = getNormalizedWoodyHeaders(servletRequest);
        if (!normalized.isEmpty()) {
            headers.putAll(normalized);
        }
    }

    private void mergeTraceContextHeaders(Map<String, String> headers) {
        var traceData = TraceContext.getCurrentTraceData();
        var jwtDetails = JwtTokenDetailsExtractor
                .extractFromContext(SecurityContextHolder.getContext().getAuthentication())
                .orElse(null);
        if (traceData == null) {
            mergeUserIdentityFromJwt(headers, jwtDetails);
            return;
        }
        var serviceSpan = traceData.getServiceSpan().getSpan();
        setDualPrefixHeader(headers, WOODY_TRACE_ID, X_WOODY_TRACE_ID, serviceSpan.getTraceId());
        setDualPrefixHeader(headers, WOODY_SPAN_ID, X_WOODY_SPAN_ID, serviceSpan.getId());
        setDualPrefixHeader(headers, WOODY_PARENT_ID, X_WOODY_PARENT_ID, serviceSpan.getParentId());
        Instant deadline = serviceSpan.getDeadline();
        if (deadline != null) {
            setDualPrefixHeader(headers, WOODY_DEADLINE, X_WOODY_DEADLINE, deadline.toString());
        }
        var userId = getCustomMetadataValue(String.class, UserIdentityIdExtensionKit.KEY);
        if (isBlank(userId) && jwtDetails != null) {
            userId = jwtDetails.subject();
        }
        setDualPrefixUserIdentityHeader(headers, UserIdentityIdExtensionKit.KEY, userId);

        var username = getCustomMetadataValue(String.class, UserIdentityUsernameExtensionKit.KEY);
        if (isBlank(username) && jwtDetails != null) {
            username = jwtDetails.preferredUsername();
        }
        setDualPrefixUserIdentityHeader(headers, UserIdentityUsernameExtensionKit.KEY, username);

        var email = getCustomMetadataValue(String.class, UserIdentityEmailExtensionKit.KEY);
        if (isBlank(email) && jwtDetails != null) {
            email = jwtDetails.email();
        }
        setDualPrefixUserIdentityHeader(headers, UserIdentityEmailExtensionKit.KEY, email);

        var realm = getCustomMetadataValue(String.class, UserIdentityRealmExtensionKit.KEY);
        if (isBlank(realm) && jwtDetails != null) {
            realm = jwtDetails.realm();
        }
        setDualPrefixUserIdentityHeader(headers, UserIdentityRealmExtensionKit.KEY, realm);
    }

    private void mergeUserIdentityFromJwt(Map<String, String> headers,
                                          JwtTokenDetailsExtractor.JwtTokenDetails jwtDetails) {
        if (jwtDetails == null) {
            return;
        }
        setDualPrefixUserIdentityHeader(headers, UserIdentityIdExtensionKit.KEY, jwtDetails.subject());
        setDualPrefixUserIdentityHeader(headers, UserIdentityUsernameExtensionKit.KEY,
                jwtDetails.preferredUsername());
        setDualPrefixUserIdentityHeader(headers, UserIdentityEmailExtensionKit.KEY, jwtDetails.email());
        setDualPrefixUserIdentityHeader(headers, UserIdentityRealmExtensionKit.KEY, jwtDetails.realm());
    }

    private void setDualPrefixHeader(Map<String, String> headers,
                                     String canonicalKey,
                                     String alternateKey,
                                     String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        headers.put(canonicalKey, value);
        headers.put(alternateKey, value);
    }

    private void setDualPrefixUserIdentityHeader(Map<String, String> headers,
                                                 String extensionKey,
                                                 String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        var suffix = WoodySuffixes.userIdentitySuffix(extensionKey);
        if (suffix.isEmpty()) {
            return;
        }
        headers.put(WOODY_META_USER_IDENTITY_PREFIX + suffix, value);
        headers.put(X_WOODY_META_USER_IDENTITY_PREFIX + suffix, value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getNormalizedWoodyHeaders(HttpServletRequest servletRequest) {
        var attribute = servletRequest.getAttribute(RequestAttributeNames.NORMALIZED_WOODY_HEADERS);
        if (attribute instanceof Map<?, ?> map) {
            return (Map<String, String>) map;
        }
        return Map.of();
    }

    private Map<String, String> createPrefixMirrors(Map<String, String> headers,
                                                    String sourcePrefix,
                                                    String targetPrefix) {
        var mirroredHeaders = new HashMap<String, String>();
        headers.forEach((key, value) -> {
            if (key.startsWith(sourcePrefix)) {
                mirroredHeaders.put(key.replace(sourcePrefix, targetPrefix), value);
            }
        });
        return mirroredHeaders;
    }

    private String truncate(String value, int limit) {
        return value.length() > limit ? value.substring(0, limit) : value;
    }
}
