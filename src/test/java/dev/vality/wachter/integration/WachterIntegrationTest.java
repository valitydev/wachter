package dev.vality.wachter.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.vality.wachter.auth.utils.JwtTokenBuilder;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static dev.vality.wachter.constants.HeadersConstants.*;
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

    @AfterEach
    void tearDown() {
        TraceContext.setCurrentTraceData(null);
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
                .andExpect(MockMvcResultMatchers.content().bytes(responseBody));

        verify(postRequestedFor(urlEqualTo("/domain"))
                .withRequestBody(binaryEqualTo(payload)));
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
                .withRequestBody(binaryEqualTo(payload)));
    }

    @Test
    void shouldReturnCorsHeadersOnSuccessfulResponse() throws Exception {
        final var deadline = Instant.now().plusSeconds(120);
        final var payload = TMessageUtil.createTMessage(protocolFactory);
        final var origin = "https://iddqd.valitydev.com";

        stubFor(WireMock.post(urlEqualTo("/domain"))
                .withRequestBody(binaryEqualTo(payload))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())));

        mockMvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("Service", "Domain")
                        .header(X_REQUEST_ID, UUID.randomUUID().toString())
                        .header(X_REQUEST_DEADLINE, deadline.toString())
                        .header(HttpHeaders.ORIGIN, origin)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.header()
                        .exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void shouldReturnCorsHeadersOnErrorResponse() throws Exception {
        final var deadline = Instant.now().plusSeconds(120);
        final var payload = TMessageUtil.createTMessage(protocolFactory);
        final var origin = "https://iddqd.valitydev.com";

        stubFor(WireMock.post(urlEqualTo("/domain"))
                .withRequestBody(binaryEqualTo(payload))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.BAD_GATEWAY.value())));

        mockMvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("Service", "Domain")
                        .header(X_REQUEST_ID, UUID.randomUUID().toString())
                        .header(X_REQUEST_DEADLINE, deadline.toString())
                        .header(HttpHeaders.ORIGIN, origin)
                        .content(payload))
                .andExpect(status().isBadGateway())
                .andExpect(MockMvcResultMatchers.header()
                        .exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
