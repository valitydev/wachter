package dev.vality.wachter.config.tracing;

import dev.vality.woody.api.trace.context.TraceContext;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityEmailExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityIdExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityRealmExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityUsernameExtensionKit;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.function.Consumer;

import static dev.vality.wachter.constants.HeadersConstants.*;
import static dev.vality.woody.api.trace.ContextUtils.setCustomMetadataValue;
import static dev.vality.woody.api.trace.ContextUtils.setDeadline;

@Slf4j
public class WoodyTraceContextApplier {

    public void apply(Map<String, String> woodyHeaders) {
        if (woodyHeaders.isEmpty()) {
            return;
        }
        var traceData = TraceContext.getCurrentTraceData();
        var serviceSpan = traceData.getServiceSpan().getSpan();
        setIfPresent(woodyHeaders, WOODY_TRACE_ID, serviceSpan::setTraceId);
        setIfPresent(woodyHeaders, WOODY_SPAN_ID, serviceSpan::setId);
        setIfPresent(woodyHeaders, WOODY_PARENT_ID, serviceSpan::setParentId);
        var woodyDeadline = woodyHeaders.get(WOODY_DEADLINE);
        if (woodyDeadline != null && !woodyDeadline.isEmpty()) {
            try {
                setDeadline(traceData.getServiceSpan(), Instant.parse(woodyDeadline));
            } catch (DateTimeParseException e) {
                log.warn("Unable to parse 'woody.deadline' header value '{}'", woodyDeadline);
            }
        }
        applyUserIdentityHeader(woodyHeaders, UserIdentityIdExtensionKit.KEY,
                value -> setCustomMetadataValue(UserIdentityIdExtensionKit.KEY, value));
        applyUserIdentityHeader(woodyHeaders, UserIdentityUsernameExtensionKit.KEY,
                value -> setCustomMetadataValue(UserIdentityUsernameExtensionKit.KEY, value));
        applyUserIdentityHeader(woodyHeaders, UserIdentityEmailExtensionKit.KEY,
                value -> setCustomMetadataValue(UserIdentityEmailExtensionKit.KEY, value));
        applyUserIdentityHeader(woodyHeaders, UserIdentityRealmExtensionKit.KEY,
                value -> setCustomMetadataValue(UserIdentityRealmExtensionKit.KEY, value));
    }

    private void applyUserIdentityHeader(Map<String, String> headers,
                                         String extensionKey,
                                         Consumer<String> consumer) {
        var suffix = WoodySuffixes.userIdentitySuffix(extensionKey);
        if (suffix.isEmpty()) {
            return;
        }
        setIfPresent(headers, WOODY_META_USER_IDENTITY_PREFIX + suffix, consumer);
    }

    private void setIfPresent(Map<String, String> headers, String key, Consumer<String> consumer) {
        var value = headers.get(key);
        if (value != null && !value.isEmpty()) {
            consumer.accept(value);
        }
    }
}
