package dev.vality.wachter.tracing;

import dev.vality.woody.api.trace.Metadata;
import dev.vality.woody.api.trace.context.TraceContext;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityEmailExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityIdExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityRealmExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityUsernameExtensionKit;
import dev.vality.woody.thrift.impl.http.TraceParentUtils;
import io.opentelemetry.api.trace.Span;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static dev.vality.wachter.constants.TraceHeadersConstants.*;

@Slf4j
@UtilityClass
public class TraceContextHeadersExtractor {

    public Map<String, String> extractHeaders() {
        var traceData = TraceContext.getCurrentTraceData();
        Objects.requireNonNull(traceData);
        Objects.requireNonNull(traceData.getOtelSpan());
        Objects.requireNonNull(traceData.getOtelSpan().getSpanContext());
        if (!traceData.getOtelSpan().getSpanContext().isValid()) {
            throw new IllegalStateException("SpanContext must be valid");
        }

        var span = traceData.getActiveSpan().getSpan();
        var headers = new HashMap<String, String>();
        putIfNotNull(headers, WOODY_TRACE_ID, span.getTraceId());
        putIfNotNull(headers, WOODY_SPAN_ID, span.getId());
        putIfNotNull(headers, WOODY_PARENT_ID, span.getParentId());
        putIfNotNull(headers, WOODY_DEADLINE,
                Optional.ofNullable(span.getDeadline()).map(Instant::toString).orElse(null));
        putIfNotNull(headers, OTEL_TRACE_PARENT, initParentTraceFromSpan(traceData.getOtelSpan()));

        var customMetadata = traceData.getActiveSpan().getCustomMetadata();
        extractUserIdentityHeader(headers, customMetadata, UserIdentityIdExtensionKit.KEY);
        extractUserIdentityHeader(headers, customMetadata, UserIdentityUsernameExtensionKit.KEY);
        extractUserIdentityHeader(headers, customMetadata, UserIdentityEmailExtensionKit.KEY);
        extractUserIdentityHeader(headers, customMetadata, UserIdentityRealmExtensionKit.KEY);
        putMetadataValue(headers, customMetadata, X_REQUEST_ID, WOODY_META_REQUEST_ID);
        putMetadataValue(headers, customMetadata, X_REQUEST_DEADLINE, WOODY_META_REQUEST_DEADLINE);
        return headers;
    }

    private void extractUserIdentityHeader(Map<String, String> headers, Metadata customMetadata, String extensionKey) {
        var suffix = WoodySuffixes.userIdentitySuffix(extensionKey);
        if (suffix.isEmpty()) {
            return;
        }

        var value = (String) customMetadata.getValue(extensionKey);
        putIfNotNull(headers, WOODY_META_USER_IDENTITY_PREFIX + suffix, value);
    }

    private void putMetadataValue(Map<String, String> headers,
                                  Metadata customMetadata,
                                  String metadataKey,
                                  String headerKey) {
        var value = (String) customMetadata.getValue(metadataKey);
        putIfNotNull(headers, headerKey, value);
    }

    private void putIfNotNull(Map<String, String> headers,
                              String key,
                              String value) {
        if (value != null && !value.isEmpty()) {
            headers.put(key, value);
        }
    }

    private String initParentTraceFromSpan(Span otelSpan) {
        var spanContext = otelSpan.getSpanContext();
        return TraceParentUtils.initParentTrace(
                TraceParentUtils.DEFAULT_VERSION,
                spanContext.getTraceId(),
                spanContext.getSpanId(),
                spanContext.getTraceFlags().asHex()
        );
    }
}
