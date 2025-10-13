package dev.vality.wachter.tracing;

import dev.vality.woody.api.flow.error.WErrorDefinition;
import dev.vality.woody.api.flow.error.WErrorSource;
import dev.vality.woody.api.flow.error.WErrorType;
import dev.vality.woody.api.flow.error.WRuntimeException;
import dev.vality.woody.api.trace.ContextSpan;
import dev.vality.woody.api.trace.MetadataProperties;
import dev.vality.woody.api.trace.TraceData;
import dev.vality.woody.api.trace.context.TraceContext;
import dev.vality.woody.thrift.impl.http.THMetadataProperties;
import dev.vality.woody.thrift.impl.http.THResponseInfo;
import dev.vality.woody.thrift.impl.http.error.THProviderErrorMapper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.HttpAttributes;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.vality.wachter.constants.TraceHeadersConstants.*;

@Slf4j
public final class WoodyTraceLifecycleHandler {

    private final THProviderErrorMapper errorMapper = new THProviderErrorMapper();

    void handleSuccess(HttpServletResponse response) {
        var traceData = TraceContext.getCurrentTraceData();
        updateSpanStatus(traceData, response.getStatus());
        applyHeaders(response, traceData, null);
    }

    void handleWoodyException(HttpServletResponse response, Throwable throwable) {
        var traceData = TraceContext.getCurrentTraceData();
        var responseInfo = resolveResponseInfo(traceData, throwable);
        applyResponseInfo(response, responseInfo);
        recordException(traceData, response, throwable);
        applyHeaders(response, traceData, responseInfo);
        flushQuietly(response);
    }

    void handleUnexpectedError(HttpServletResponse response, Throwable throwable) {
        var traceData = TraceContext.getCurrentTraceData();
        var responseInfo = resolveResponseInfo(traceData, fallbackDefinition(throwable));
        applyResponseInfo(response, responseInfo);
        recordException(traceData, response, throwable);
        applyHeaders(response, traceData, responseInfo);
        flushQuietly(response);
    }

    private void updateSpanStatus(TraceData traceData, int status) {
        var span = extractSpan(traceData);
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }
        if (status > 0) {
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, status);
            span.setStatus(status >= 500 ? StatusCode.ERROR : StatusCode.OK);
        } else {
            span.setStatus(StatusCode.OK);
        }
    }

    private void recordException(TraceData traceData, HttpServletResponse response, Throwable throwable) {
        var span = extractSpan(traceData);
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }
        var status = response.getStatus();
        if (status > 0) {
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, status);
        }
        span.recordException(throwable);
        span.setStatus(StatusCode.ERROR);
    }

    private void applyHeaders(HttpServletResponse response, TraceData traceData, THResponseInfo responseInfo) {
        if (response == null || response.isCommitted() || traceData == null) {
            return;
        }
        var serviceSpan = traceData.getServiceSpan();
        if (serviceSpan == null || !serviceSpan.isFilled()) {
            return;
        }

        var headers = new HttpHeaders();
        var span = serviceSpan.getSpan();
        addHeader(headers, WOODY_TRACE_ID, span.getTraceId());
        addHeader(headers, WOODY_SPAN_ID, span.getId());
        addHeader(headers, WOODY_PARENT_ID, span.getParentId());
        var deadline = span.getDeadline();
        if (deadline != null) {
            addHeader(headers, WOODY_DEADLINE, deadline.toString());
        }
        serviceSpan.getCustomMetadata().getKeys()
                .forEach(key -> addHeader(headers, WOODY_META_PREFIX + key,
                        serviceSpan.getCustomMetadata().getValue(key)));

        if (responseInfo != null) {
            addHeader(headers, WOODY_ERROR_CLASS, responseInfo.getErrClass());
            addHeader(headers, WOODY_ERROR_REASON, responseInfo.getErrReason());
        }

        addHeader(headers, OTEL_TRACE_PARENT, traceData.getInboundTraceParent());
        addHeader(headers, OTEL_TRACE_STATE, traceData.getInboundTraceState());

        headers.forEach((name, values) -> {
            if (values != null) {
                response.setHeader(name, join(values));
            }
        });
    }

    private THResponseInfo resolveResponseInfo(TraceData traceData, Throwable throwable) {
        if (traceData == null) {
            return fallbackResponseInfo(fallbackDefinition(throwable));
        }
        var serviceSpan = traceData.getServiceSpan();
        if (serviceSpan == null) {
            return fallbackResponseInfo(fallbackDefinition(throwable));
        }
        serviceSpan.getMetadata().putValue(MetadataProperties.CALL_ERROR, throwable);
        var definition = extractDefinition(serviceSpan, throwable);
        serviceSpan.getMetadata().putValue(MetadataProperties.ERROR_DEFINITION, definition);
        var responseInfo = THProviderErrorMapper.getResponseInfo(serviceSpan);
        serviceSpan.getMetadata().putValue(THMetadataProperties.TH_RESPONSE_INFO, responseInfo);
        return responseInfo;
    }

    private THResponseInfo resolveResponseInfo(TraceData traceData, WErrorDefinition definition) {
        if (traceData == null) {
            return fallbackResponseInfo(definition);
        }
        var serviceSpan = traceData.getServiceSpan();
        if (serviceSpan == null) {
            return fallbackResponseInfo(definition);
        }
        serviceSpan.getMetadata().putValue(MetadataProperties.ERROR_DEFINITION, definition);
        var responseInfo = THProviderErrorMapper.getResponseInfo(serviceSpan);
        serviceSpan.getMetadata().putValue(THMetadataProperties.TH_RESPONSE_INFO, responseInfo);
        return responseInfo;
    }

    private WErrorDefinition extractDefinition(ContextSpan serviceSpan, Throwable throwable) {
        if (throwable instanceof WRuntimeException runtime) {
            return runtime.getErrorDefinition();
        }
        var mapped = errorMapper.mapToDef(throwable, serviceSpan);
        if (mapped != null) {
            return mapped;
        }
        return fallbackDefinition(throwable);
    }

    private WErrorDefinition fallbackDefinition(Throwable throwable) {
        var definition = new WErrorDefinition(WErrorSource.INTERNAL);
        definition.setErrorType(WErrorType.UNEXPECTED_ERROR);
        definition.setErrorSource(WErrorSource.INTERNAL);
        if (throwable != null) {
            definition.setErrorReason(Objects.toString(throwable.getMessage(), WErrorType.UNEXPECTED_ERROR.getKey()));
            definition.setErrorName(throwable.getClass().getSimpleName());
            definition.setErrorMessage(throwable.getMessage());
        } else {
            definition.setErrorReason(WErrorType.UNEXPECTED_ERROR.getKey());
            definition.setErrorName(WErrorType.UNEXPECTED_ERROR.getKey());
            definition.setErrorMessage(WErrorType.UNEXPECTED_ERROR.getKey());
        }
        return definition;
    }

    private void applyResponseInfo(HttpServletResponse response, THResponseInfo responseInfo) {
        if (response == null || response.isCommitted() || responseInfo == null) {
            return;
        }
        if (responseInfo.getStatus() > 0) {
            response.setStatus(responseInfo.getStatus());
        }
    }

    private THResponseInfo fallbackResponseInfo(WErrorDefinition definition) {
        var status = definition != null && definition.getErrorType() == WErrorType.BUSINESS_ERROR
                ? HttpServletResponse.SC_OK
                : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        var errClass = definition != null && definition.getErrorType() != null
                ? definition.getErrorType().getKey()
                : WErrorType.UNEXPECTED_ERROR.getKey();
        var errReason = definition != null ? definition.getErrorReason() : WErrorType.UNEXPECTED_ERROR.getKey();
        return new THResponseInfo(status, errClass, errReason);
    }

    private Span extractSpan(TraceData traceData) {
        return traceData == null ? null : traceData.getOtelSpan();
    }

    private static void addHeader(HttpHeaders headers, String name, String value) {
        if (name != null && value != null && !value.isEmpty()) {
            headers.set(name, value);
        }
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.getFirst();
        }
        return String.join(",", new ArrayList<>(values));
    }

    private static void flushQuietly(HttpServletResponse response) {
        if (response == null) {
            return;
        }
        try {
            response.flushBuffer();
        } catch (Exception exception) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to flush response buffer", exception);
            }
        }
    }
}
