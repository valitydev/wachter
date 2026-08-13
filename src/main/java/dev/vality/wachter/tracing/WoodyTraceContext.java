package dev.vality.wachter.tracing;

import dev.vality.woody.api.flow.WFlow;
import dev.vality.woody.api.trace.TraceData;
import dev.vality.woody.api.trace.context.TraceContext;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import static dev.vality.wachter.tracing.TraceHeaders.*;

@UtilityClass
public class WoodyTraceContext {

    public TraceData restore(HttpHeaders headers) {
        var traceData = TraceContext.initNewServiceTrace(
                new TraceData(), WFlow.createDefaultIdGenerator(), WFlow.createDefaultIdGenerator());
        var span = traceData.getActiveSpan().getSpan();
        var traceId = headers.getFirst(WOODY_TRACE_ID);
        var spanId = headers.getFirst(WOODY_SPAN_ID);
        setIfPresent(traceId, span::setTraceId);
        if (!Objects.equals(traceId, spanId)) {
            setIfPresent(spanId, span::setId);
        }
        var parentId = headers.getFirst(WOODY_PARENT_ID);
        if (!"undefined".equals(parentId)) {
            setIfPresent(parentId, span::setParentId);
        }
        setIfPresent(headers, WOODY_DEADLINE, value -> span.setDeadline(Instant.parse(value)));
        span.setTimestamp(0);
        span.setDuration(0);

        var customMetadata = traceData.getActiveSpan().getCustomMetadata();
        headers.forEach((name, values) -> {
            var normalizedName = name.toLowerCase(Locale.ROOT);
            if (normalizedName.startsWith(WOODY_META_PREFIX) && !values.isEmpty()) {
                customMetadata.putValue(
                        normalizedName.substring(WOODY_META_PREFIX.length()), values.getFirst());
            }
        });
        return traceData;
    }

    public HttpHeaders extractHeaders() {
        var traceData = Objects.requireNonNull(
                TraceContext.getCurrentTraceData(), "Woody TraceData must be initialized for the request");
        var activeSpan = traceData.getActiveSpan();
        var span = activeSpan.getSpan();
        var headers = new HttpHeaders();
        setIfNotNull(headers, WOODY_TRACE_ID, span.getTraceId());
        setIfNotNull(headers, WOODY_SPAN_ID, span.getId());
        setIfNotNull(headers, WOODY_PARENT_ID, span.getParentId());
        setIfNotNull(headers, WOODY_DEADLINE,
                span.getDeadline() == null ? null : span.getDeadline().toString());
        var customMetadata = activeSpan.getCustomMetadata();
        customMetadata.getKeys().forEach(key ->
                setIfNotNull(headers, WOODY_META_PREFIX + key, customMetadata.getValue(key)));
        return headers;
    }

    private void setIfPresent(HttpHeaders headers, String name, Consumer<String> setter) {
        setIfPresent(headers.getFirst(name), setter);
    }

    private void setIfPresent(String value, Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    private void setIfNotNull(HttpHeaders headers, String name, Object value) {
        if (value != null) {
            var stringValue = value.toString();
            if (!stringValue.isBlank()) {
                headers.set(name, stringValue);
            }
        }
    }
}
