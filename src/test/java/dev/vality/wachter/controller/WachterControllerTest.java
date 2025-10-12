package dev.vality.wachter.controller;

import dev.vality.wachter.client.WachterClient;
import dev.vality.wachter.client.WachterClientResponse;
import dev.vality.wachter.config.AbstractKeycloakOpenIdAsWiremockConfig;
import dev.vality.wachter.testutil.TMessageUtil;
import lombok.SneakyThrows;
import org.apache.thrift.protocol.TProtocolFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static dev.vality.wachter.constants.TraceHeadersConstants.*;
import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WachterControllerTest extends AbstractKeycloakOpenIdAsWiremockConfig {

    @MockitoBean
    private WachterClient wachterClient;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TProtocolFactory protocolFactory;

    private AutoCloseable mocks;

    private Object[] preparedMocks;


    @BeforeEach
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
        preparedMocks = new Object[] {wachterClient};
    }

    @AfterEach
    public void clean() throws Exception {
        verifyNoMoreInteractions(preparedMocks);
        mocks.close();
    }

    @Test
    @SneakyThrows
    void requestSuccessWithServiceRole() {
        when(wachterClient.send(any(), any(), any()))
                .thenReturn(new WachterClientResponse(HttpStatus.OK, new HttpHeaders(), new byte[0]));
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("Service", "Domain")
                        .header(ExternalHeaders.X_REQUEST_ID, randomUUID())
                        .header(ExternalHeaders.X_REQUEST_DEADLINE, Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is2xxSuccessful());
        verify(wachterClient, times(1)).send(any(), any(), any());
    }

    @Test
    @SneakyThrows
    void requestSuccessWithMethodRole() {
        when(wachterClient.send(any(), any(), any()))
                .thenReturn(new WachterClientResponse(HttpStatus.OK, new HttpHeaders(), new byte[0]));
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("Service", "Domain")
                        .header(ExternalHeaders.X_REQUEST_ID, randomUUID())
                        .header(ExternalHeaders.X_REQUEST_DEADLINE, Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is2xxSuccessful());
        verify(wachterClient, times(1)).send(any(), any(), any());
    }

    @Test
    @SneakyThrows
    void requestSuccessWithWoodyHeaders() {
        when(wachterClient.send(any(), any(), any()))
                .thenReturn(new WachterClientResponse(HttpStatus.OK, new HttpHeaders(), new byte[0]));
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("Service", "Domain")
                        .header(ExternalHeaders.X_REQUEST_ID, randomUUID())
                        .header(ExternalHeaders.X_REQUEST_DEADLINE, Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .header(WOODY_PARENT_ID, "parent")
                        .header(WOODY_TRACE_ID, "trace")
                        .header(WOODY_SPAN_ID, "span")
                        .header(WOODY_DEADLINE, "deadline")
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is2xxSuccessful());
        verify(wachterClient, times(1)).send(any(), any(), any());
    }

    @Test
    @SneakyThrows
    void requestSuccessWithWoodyWithDashHeaders() {
        when(wachterClient.send(any(), any(), any()))
                .thenReturn(new WachterClientResponse(HttpStatus.OK, new HttpHeaders(), new byte[0]));
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("Service", "Domain")
                        .header(ExternalHeaders.X_REQUEST_ID, randomUUID())
                        .header(ExternalHeaders.X_REQUEST_DEADLINE, Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .header(ExternalHeaders.X_WOODY_PARENT_ID, "parent")
                        .header(ExternalHeaders.X_WOODY_TRACE_ID, "trace")
                        .header(ExternalHeaders.X_WOODY_SPAN_ID, "span")
                        .header(ExternalHeaders.X_WOODY_DEADLINE, "deadline")
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is2xxSuccessful());
        verify(wachterClient, times(1)).send(any(), any(), any());
    }

    @Test
    @SneakyThrows
    void shouldPropagateUpstreamError() {
        final var headers = new HttpHeaders();
        headers.set("X-Upstream", "value");
        final var body = "failure".getBytes();
        when(wachterClient.send(any(), any(), any()))
                .thenReturn(new WachterClientResponse(HttpStatus.BAD_GATEWAY, headers, body));

        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("Service", "Domain")
                        .header(ExternalHeaders.X_REQUEST_ID, randomUUID())
                        .header(ExternalHeaders.X_REQUEST_DEADLINE, Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().isBadGateway())
                .andExpect(header().string("X-Upstream", "value"))
                .andExpect(content().bytes(body));
        verify(wachterClient, times(1)).send(any(), any(), any());
    }
}
