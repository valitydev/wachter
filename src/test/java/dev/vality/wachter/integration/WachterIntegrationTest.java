package dev.vality.wachter.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.vality.wachter.auth.utils.JwtTokenBuilder;
import dev.vality.wachter.client.WachterRequestFactory;
import dev.vality.wachter.config.AbstractKeycloakOpenIdAsWiremockConfig;
import dev.vality.wachter.constants.HeadersConstants;
import dev.vality.wachter.testutil.TMessageUtil;
import dev.vality.woody.api.trace.context.TraceContext;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityEmailExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityIdExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityRealmExtensionKit;
import dev.vality.woody.api.trace.context.metadata.user.UserIdentityUsernameExtensionKit;
import org.apache.thrift.protocol.TProtocolFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static dev.vality.wachter.constants.HeadersConstants.*;
import static dev.vality.woody.api.trace.ContextUtils.getCustomMetadataValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "wachter.services.Domain.url=http://localhost:${wiremock.server.port}/domain"
})
class WachterIntegrationTest extends AbstractKeycloakOpenIdAsWiremockConfig {

    private static final String USER_ID_HEADER = WOODY_META_USER_IDENTITY_PREFIX +
            HeadersConstants.WoodySuffixes.userIdentitySuffix(UserIdentityIdExtensionKit.KEY);
    private static final String USER_NAME_HEADER = WOODY_META_USER_IDENTITY_PREFIX +
            HeadersConstants.WoodySuffixes.userIdentitySuffix(UserIdentityUsernameExtensionKit.KEY);
    private static final String USER_EMAIL_HEADER = WOODY_META_USER_IDENTITY_PREFIX +
            HeadersConstants.WoodySuffixes.userIdentitySuffix(UserIdentityEmailExtensionKit.KEY);
    private static final String USER_REALM_HEADER = WOODY_META_USER_IDENTITY_PREFIX +
            HeadersConstants.WoodySuffixes.userIdentitySuffix(UserIdentityRealmExtensionKit.KEY);
    private static final String TRACEPARENT_PATTERN = "00-[0-9a-f]{32}-[0-9a-f]{16}-0[0-1]";
    private static final String EXPECTED_REALM = "/internal";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TProtocolFactory protocolFactory;

    @MockitoSpyBean
    private WachterRequestFactory requestFactory;

    @AfterEach
    void tearDown() {
        TraceContext.setCurrentTraceData(null);
        Mockito.reset(requestFactory);
        resetAllRequests();
    }

    @Test
    void shouldProxyRequestEndToEnd() throws Exception {
        final var traceId = "integration-trace";
        final var spanId = "integration-span";
        final var parentId = "integration-parent";
        final var deadline = Instant.now().plusSeconds(300);
        final var requestId = UUID.randomUUID().toString();
        final var payload = TMessageUtil.createTMessage(protocolFactory);
        final var responseBody = "integration-response".getBytes();
        final var upstreamTraceparent = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01";

        final var capturedHeaders = new AtomicReference<HttpHeaders>();
        final var capturedTraceId = new AtomicReference<String>();
        final var capturedSpanId = new AtomicReference<String>();
        final var capturedParentId = new AtomicReference<String>();
        final var capturedDeadline = new AtomicReference<Instant>();
        final var capturedUserId = new AtomicReference<String>();
        final var capturedRealm = new AtomicReference<String>();

        Mockito.doAnswer(invocation -> {
            final var headers = (HttpHeaders) invocation.callRealMethod();
            capturedHeaders.set(headers);
            final var traceData = TraceContext.getCurrentTraceData();
            assertNotNull(traceData);
            final var serviceSpan = traceData.getServiceSpan().getSpan();
            capturedTraceId.set(serviceSpan.getTraceId());
            capturedSpanId.set(serviceSpan.getId());
            capturedParentId.set(serviceSpan.getParentId());
            capturedDeadline.set(serviceSpan.getDeadline());
            capturedUserId.set(getCustomMetadataValue(String.class, UserIdentityIdExtensionKit.KEY));
            capturedRealm.set(getCustomMetadataValue(String.class, UserIdentityRealmExtensionKit.KEY));
            return headers;
        }).when(requestFactory).buildHeaders(Mockito.any());

        stubFor(WireMock.post(urlEqualTo("/domain"))
                .withRequestBody(binaryEqualTo(payload))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.ACCEPTED.value())
                        .withHeader("X-Upstream", "accepted")
                        .withHeader("traceparent", upstreamTraceparent)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        .withBody(responseBody)));

        mockMvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("Service", "Domain")
                        .header(X_REQUEST_ID, requestId)
                        .header(X_REQUEST_DEADLINE, deadline.toString())
                        .header(X_WOODY_TRACE_ID, traceId)
                        .header(X_WOODY_SPAN_ID, spanId)
                        .header(X_WOODY_PARENT_ID, parentId)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(MockMvcResultMatchers.header().string("X-Upstream", "accepted"))
                .andExpect(MockMvcResultMatchers.header().string("traceparent", upstreamTraceparent))
                .andExpect(MockMvcResultMatchers.content().bytes(responseBody));

        verify(postRequestedFor(urlEqualTo("/domain"))
                .withHeader(WOODY_TRACE_ID, equalTo(traceId))
                .withHeader(WOODY_SPAN_ID, equalTo(spanId))
                .withHeader(WOODY_PARENT_ID, equalTo(parentId))
                .withHeader(WOODY_DEADLINE, equalTo(deadline.toString()))
                .withHeader(USER_ID_HEADER, matching(".+"))
                .withHeader(USER_EMAIL_HEADER, equalTo(JwtTokenBuilder.DEFAULT_EMAIL))
                .withHeader(USER_NAME_HEADER, equalTo(JwtTokenBuilder.DEFAULT_USERNAME))
                .withHeader(USER_REALM_HEADER, equalTo(EXPECTED_REALM))
                .withHeader(OTEL_TRACE_PARENT, matching(TRACEPARENT_PATTERN))
                .withRequestBody(binaryEqualTo(payload)));

        final var headers = capturedHeaders.get();
        assertNotNull(headers);
        assertEquals(traceId, headers.getFirst(WOODY_TRACE_ID));
        assertEquals(spanId, headers.getFirst(WOODY_SPAN_ID));
        assertEquals(parentId, headers.getFirst(WOODY_PARENT_ID));
        assertEquals(deadline.toString(), headers.getFirst(WOODY_DEADLINE));
        assertEquals(JwtTokenBuilder.DEFAULT_EMAIL, headers.getFirst(USER_EMAIL_HEADER));
        assertEquals(JwtTokenBuilder.DEFAULT_USERNAME, headers.getFirst(USER_NAME_HEADER));
        assertEquals(EXPECTED_REALM, headers.getFirst(USER_REALM_HEADER));
        final var userId = capturedUserId.get();
        assertNotNull(userId);
        assertFalse(userId.isBlank());
        assertEquals(userId, headers.getFirst(USER_ID_HEADER));
        assertEquals(EXPECTED_REALM, capturedRealm.get());
        assertEquals(traceId, capturedTraceId.get());
        assertEquals(spanId, capturedSpanId.get());
        assertEquals(parentId, capturedParentId.get());
        assertEquals(deadline, capturedDeadline.get());
    }

    @Test
    void shouldStripHopByHopHeadersBeforeProxying() throws Exception {
        final var deadline = Instant.now().plusSeconds(60);
        final var payload = TMessageUtil.createTMessage(protocolFactory);

        stubFor(WireMock.post(urlEqualTo("/domain"))
                .withRequestBody(binaryEqualTo(payload))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())));

        mockMvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("Service", "Domain")
                        .header(X_REQUEST_ID, UUID.randomUUID().toString())
                        .header(X_REQUEST_DEADLINE, deadline.toString())
                        .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
                        .header(HttpHeaders.CONNECTION, "keep-alive")
                        .header(HttpHeaders.TE, "trailers")
                        .header(HttpHeaders.HOST, "example.org")
                        .content(payload))
                .andExpect(status().isOk());

        verify(postRequestedFor(urlEqualTo("/domain"))
                .withHeader(HttpHeaders.HOST, matching("localhost:\\d+"))
                .withoutHeader(HttpHeaders.TRANSFER_ENCODING)
                .withHeader(HttpHeaders.CONNECTION, notMatching("(?i).*keep-alive.*"))
                .withoutHeader(HttpHeaders.TE)
                .withRequestBody(binaryEqualTo(payload)));
    }
}
