package dev.vality.wachter.constants;

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
    public static final String X_WOODY_META_USER_IDENTITY_PREFIX = X_WOODY_PREFIX + WoodySuffixes.META_USER_IDENTITY;
    public static final String WOODY_PREFIX = "woody.";
    public static final String WOODY_TRACE_ID = WOODY_PREFIX + "trace-id";
    public static final String WOODY_SPAN_ID = WOODY_PREFIX + "span-id";
    public static final String WOODY_PARENT_ID = WOODY_PREFIX + "parent-id";
    public static final String WOODY_DEADLINE = WOODY_PREFIX + "deadline";
    public static final String WOODY_ERROR_CLASS = WOODY_PREFIX + "error-class";
    public static final String WOODY_ERROR_REASON = WOODY_PREFIX + "error-reason";
    public static final String WOODY_META_USER_IDENTITY_PREFIX = WOODY_PREFIX + WoodySuffixes.META_USER_IDENTITY_DOT;
    public static final String OTEL_TRACE_PARENT = "traceparent";

    public static final class WoodySuffixes {
        private static final String META = "meta";
        private static final String USER_IDENTITY = "user-identity";
        private static final String HYPHEN = "-";
        private static final String DOT = ".";

        public static final String META_USER_IDENTITY = META + HYPHEN + USER_IDENTITY + HYPHEN;
        public static final String META_USER_IDENTITY_DOT = META + DOT + USER_IDENTITY + DOT;
        public static final String USER_IDENTITY_KEY_PREFIX = USER_IDENTITY + DOT;

        private WoodySuffixes() {
        }

        public static String userIdentitySuffix(String extensionKey) {
            if (extensionKey == null || extensionKey.isEmpty()) {
                return "";
            }
            if (extensionKey.startsWith(USER_IDENTITY_KEY_PREFIX)) {
                return extensionKey.substring(USER_IDENTITY_KEY_PREFIX.length());
            }
            int lastDot = extensionKey.lastIndexOf('.');
            return lastDot >= 0 ? extensionKey.substring(lastDot + 1) : extensionKey;
        }
    }

}
