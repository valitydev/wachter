package dev.vality.wachter.tracing;

import dev.vality.woody.api.trace.context.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.concurrent.atomic.AtomicBoolean;

import static dev.vality.wachter.tracing.TraceHeaders.*;
import static org.junit.jupiter.api.Assertions.*;

class WoodyTracingFilterTest {

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        TraceContext.setCurrentTraceData(null);
        MDC.clear();
    }

    @Test
    void shouldExposeJwtIdentityAsWoodyServerMetadataDuringRequest() throws Exception {
        var request = new MockHttpServletRequest("POST", "/wachter");
        request.addHeader(ExternalHeaders.X_WOODY_TRACE_ID, "trace-id");
        request.addHeader(ExternalHeaders.X_WOODY_SPAN_ID, "span-id");
        request.addHeader(ExternalHeaders.X_REQUEST_ID, "request-id");
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-id")
                .claim("preferred_username", "user@example.com")
                .claim("email", "user@example.com")
                .issuer("https://auth.example.com/realms/internal")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        var invoked = new AtomicBoolean();
        var previousTraceData = TraceContext.getCurrentTraceData();

        new WoodyTracingFilter().doFilter(request, new MockHttpServletResponse(), (servletRequest, response) -> {
            invoked.set(true);
            assertAll(
                    () -> assertEquals("user-id",
                            MDC.get("rpc.server.metadata.user-identity.id")),
                    () -> assertEquals("user@example.com",
                            MDC.get("rpc.server.metadata.user-identity.username")),
                    () -> assertEquals("user@example.com",
                            MDC.get("rpc.server.metadata.user-identity.email")),
                    () -> assertEquals("internal",
                            MDC.get("rpc.server.metadata.user-identity.realm")),
                    () -> assertEquals("request-id",
                            MDC.get("rpc.server.metadata.user-identity.x-request-id")),
                    () -> assertEquals("trace-id", WoodyTraceContext.extractHeaders().getFirst(WOODY_TRACE_ID)),
                    () -> assertEquals("user-id", WoodyTraceContext.extractHeaders().getFirst(WOODY_META_ID)));
        });

        assertTrue(invoked.get());
        assertSame(previousTraceData, TraceContext.getCurrentTraceData());
        assertNull(MDC.get("rpc.server.metadata.user-identity.email"));
    }

    @Test
    void shouldGenerateWoodySpanWhenIncomingSpanDuplicatesTrace() throws Exception {
        var request = new MockHttpServletRequest("POST", "/wachter");
        request.addHeader(ExternalHeaders.X_WOODY_TRACE_ID, "duplicated-id");
        request.addHeader(ExternalHeaders.X_WOODY_SPAN_ID, "duplicated-id");
        request.addHeader(ExternalHeaders.X_WOODY_PARENT_ID, "undefined");

        new WoodyTracingFilter().doFilter(request, new MockHttpServletResponse(), (servletRequest, response) -> {
            var headers = WoodyTraceContext.extractHeaders();
            assertAll(
                    () -> assertEquals("duplicated-id", headers.getFirst(WOODY_TRACE_ID)),
                    () -> assertNotEquals("duplicated-id", headers.getFirst(WOODY_SPAN_ID)),
                    () -> assertEquals("undefined", headers.getFirst(WOODY_PARENT_ID)));
        });
    }
}
