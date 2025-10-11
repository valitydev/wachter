package dev.vality.wachter.constants;

import dev.vality.woody.thrift.impl.http.transport.THttpHeader;

public class TraceHeadersConstants {

    public static final String X_REQUEST_ID = "X-Request-ID";
    public static final String X_REQUEST_DEADLINE = "X-Request-Deadline";
    public static final String X_WOODY_PREFIX = "x-woody-";
    public static final String X_WOODY_TRACE_ID = X_WOODY_PREFIX + "trace-id";
    public static final String X_WOODY_SPAN_ID = X_WOODY_PREFIX + "span-id";
    public static final String X_WOODY_PARENT_ID = X_WOODY_PREFIX + "parent-id";
    public static final String X_WOODY_DEADLINE = X_WOODY_PREFIX + "deadline";
    public static final String X_WOODY_ERROR_CLASS = X_WOODY_PREFIX + "error-class";
    public static final String X_WOODY_ERROR_REASON = X_WOODY_PREFIX + "error-reason";
    public static final String X_WOODY_META_USER_IDENTITY_PREFIX =
            X_WOODY_PREFIX + WoodySuffixes.META_USER_IDENTITY_SUFFIX;
    public static final String WOODY_PREFIX = "woody.";
    public static final String WOODY_TRACE_ID = THttpHeader.TRACE_ID.getKey();
    public static final String WOODY_SPAN_ID = THttpHeader.SPAN_ID.getKey();
    public static final String WOODY_PARENT_ID = THttpHeader.PARENT_ID.getKey();
    public static final String WOODY_DEADLINE = THttpHeader.DEADLINE.getKey();
    public static final String WOODY_ERROR_CLASS = THttpHeader.ERROR_CLASS.getKey();
    public static final String WOODY_ERROR_REASON = THttpHeader.ERROR_REASON.getKey();
    public static final String WOODY_META_USER_IDENTITY_PREFIX =
            WOODY_PREFIX + WoodySuffixes.META_USER_IDENTITY_DOT_SUFFIX;
    public static final String WOODY_META_REQUEST_ID = WOODY_META_USER_IDENTITY_PREFIX + "x-request-id";
    public static final String WOODY_META_REQUEST_DEADLINE = WOODY_META_USER_IDENTITY_PREFIX + "x-request-deadline";
    public static final String OTEL_TRACE_PARENT = THttpHeader.TRACE_PARENT.getKey();
    public static final String OTEL_TRACE_STATE = THttpHeader.TRACE_STATE.getKey();

    public static final class WoodySuffixes {
        private static final String META = "meta";
        private static final String USER_IDENTITY = "user-identity";
        private static final String HYPHEN = "-";
        private static final String DOT = ".";

        public static final String META_USER_IDENTITY_SUFFIX = META + HYPHEN + USER_IDENTITY + HYPHEN;
        public static final String META_USER_IDENTITY_DOT_SUFFIX = META + DOT + USER_IDENTITY + DOT;
        public static final String USER_IDENTITY_KEY_SUFFIX = USER_IDENTITY + DOT;

        private WoodySuffixes() {
        }

        public static String userIdentitySuffix(String extensionKey) {
            if (extensionKey == null || extensionKey.isEmpty()) {
                return "";
            }
            if (extensionKey.startsWith(USER_IDENTITY_KEY_SUFFIX)) {
                return extensionKey.substring(USER_IDENTITY_KEY_SUFFIX.length());
            }
            int lastDot = extensionKey.lastIndexOf('.');
            return lastDot >= 0 ? extensionKey.substring(lastDot + 1) : extensionKey;
        }
    }

}
