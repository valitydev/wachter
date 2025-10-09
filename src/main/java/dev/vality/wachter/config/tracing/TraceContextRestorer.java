package dev.vality.wachter.config.tracing;

import dev.vality.woody.api.flow.WFlow;
import dev.vality.woody.api.trace.TraceData;
import dev.vality.woody.api.trace.context.TraceContext;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityEmailExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityIdExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityRealmExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityUsernameExtensionKit;
import dev.vality.woody.thrift.impl.http.TraceParentUtils;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

import static dev.vality.wachter.constants.HeadersConstants.*;

@Slf4j
public class TraceContextRestorer {

    public TraceData restoreTraceData(Map<String, String> headers) {
        var traceData = new TraceData();

        TraceContext.initNewServiceTrace(traceData, WFlow.createDefaultIdGenerator(), WFlow.createDefaultIdGenerator());

        if (headers.isEmpty()) {
            return traceData;
        }

        var serviceSpan = traceData.getServiceSpan().getSpan();
        setIfPresent(headers, WOODY_TRACE_ID, serviceSpan::setTraceId);
        setIfPresent(headers, WOODY_SPAN_ID, serviceSpan::setId);
        setIfPresent(headers, WOODY_PARENT_ID, serviceSpan::setParentId);
        setIfPresent(headers, WOODY_DEADLINE, value -> serviceSpan.setDeadline(Instant.parse(value)));
        var customMetadata = traceData.getActiveSpan().getCustomMetadata();
        applyUserIdentityHeader(headers, UserIdentityIdExtensionKit.KEY,
                value -> customMetadata.putValue(UserIdentityIdExtensionKit.KEY, value));
        applyUserIdentityHeader(headers, UserIdentityUsernameExtensionKit.KEY,
                value -> customMetadata.putValue(UserIdentityUsernameExtensionKit.KEY, value));
        applyUserIdentityHeader(headers, UserIdentityEmailExtensionKit.KEY,
                value -> customMetadata.putValue(UserIdentityEmailExtensionKit.KEY, value));
        applyUserIdentityHeader(headers, UserIdentityRealmExtensionKit.KEY,
                value -> customMetadata.putValue(UserIdentityRealmExtensionKit.KEY, value));
        setIfPresent(headers, OTEL_TRACE_PARENT, value -> {
            var otelSpan = initSpan(value);
            otelSpan.makeCurrent();
            traceData.setOtelSpan(otelSpan);
        });
        setIfPresent(headers, X_REQUEST_ID, value -> customMetadata.putValue(X_REQUEST_ID, value));
        setIfPresent(headers, X_REQUEST_DEADLINE, value -> customMetadata.putValue(X_REQUEST_DEADLINE, value));
        return traceData;
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
            try {
                consumer.accept(value);
            } catch (Exception e) {
                log.warn("Unable to set header with  key '{}' value '{}'", key, value);
            }
        }
    }

    private Span initSpan(String traceparent) {
        return GlobalOpenTelemetry.getTracer(TraceData.WOODY)
                .spanBuilder(TraceData.OTEL_CLIENT)
                .setSpanKind(SpanKind.SERVER)
                .setParent(
                        Context.current().with(
                                Span.wrap(
                                        SpanContext.createFromRemoteParent(
                                                TraceParentUtils.parseTraceId(traceparent),
                                                TraceParentUtils.parseSpanId(traceparent),
                                                TraceFlags.getSampled(),
                                                TraceState.builder().build()
                                        )
                                )
                        )
                )
                .startSpan();
    }
}
