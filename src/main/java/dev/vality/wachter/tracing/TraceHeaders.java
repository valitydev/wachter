package dev.vality.wachter.tracing;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TraceHeaders {

    public static final String WOODY_PREFIX = "woody.";
    public static final String WOODY_TRACE_ID = "woody.trace-id";
    public static final String WOODY_SPAN_ID = "woody.span-id";
    public static final String WOODY_PARENT_ID = "woody.parent-id";
    public static final String WOODY_DEADLINE = "woody.deadline";
    public static final String WOODY_META_PREFIX = "woody.meta.";
    public static final String WOODY_META_ID = WOODY_META_PREFIX + "user-identity.id";
    public static final String WOODY_META_USERNAME = WOODY_META_PREFIX + "user-identity.username";
    public static final String WOODY_META_EMAIL = WOODY_META_PREFIX + "user-identity.email";
    public static final String WOODY_META_REALM = WOODY_META_PREFIX + "user-identity.realm";
    public static final String WOODY_META_REQUEST_ID = WOODY_META_PREFIX + "user-identity.X-Request-ID";
    public static final String WOODY_META_REQUEST_DEADLINE =
            WOODY_META_PREFIX + "user-identity.X-Request-Deadline";
    public static final String WOODY_META_REQUEST_INVOICE_ID =
            WOODY_META_PREFIX + "user-identity.X-Invoice-ID";
    public static final String OTEL_TRACE_PARENT = "traceparent";
    public static final String OTEL_TRACE_STATE = "tracestate";

    @UtilityClass
    public static class ExternalHeaders {

        public static final String X_REQUEST_ID = "X-Request-ID";
        public static final String X_REQUEST_DEADLINE = "X-Request-Deadline";
        public static final String X_INVOICE_ID = "X-Invoice-ID";
        public static final String X_WOODY_PREFIX = "x-woody-";
        public static final String X_WOODY_TRACE_ID = X_WOODY_PREFIX + "trace-id";
        public static final String X_WOODY_SPAN_ID = X_WOODY_PREFIX + "span-id";
        public static final String X_WOODY_PARENT_ID = X_WOODY_PREFIX + "parent-id";
        public static final String X_WOODY_DEADLINE = X_WOODY_PREFIX + "deadline";
        public static final String X_WOODY_META_PREFIX = X_WOODY_PREFIX + "meta-";
        public static final String X_WOODY_META_ID = X_WOODY_META_PREFIX + "user-identity-id";
        public static final String X_WOODY_META_USERNAME = X_WOODY_META_PREFIX + "user-identity-username";
        public static final String X_WOODY_META_EMAIL = X_WOODY_META_PREFIX + "user-identity-email";
        public static final String X_WOODY_META_REALM = X_WOODY_META_PREFIX + "user-identity-realm";
    }
}
