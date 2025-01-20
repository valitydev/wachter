package dev.vality.wachter.controller;

import dev.vality.wachter.client.WachterResponseHandler;
import dev.vality.wachter.config.AbstractKeycloakOpenIdAsWiremockConfig;
import dev.vality.wachter.testutil.TMessageUtil;
import lombok.SneakyThrows;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicStatusLine;
import org.apache.thrift.protocol.TProtocolFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WachterControllerTest extends AbstractKeycloakOpenIdAsWiremockConfig {

    @MockBean
    private HttpClient httpClient;
    @MockBean
    private HttpResponse httpResponse;

    @Autowired
    private WachterResponseHandler responseHandler;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TProtocolFactory protocolFactory;

    private AutoCloseable mocks;

    private Object[] preparedMocks;


    @BeforeEach
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
        preparedMocks = new Object[]{httpClient};
    }

    @AfterEach
    public void clean() throws Exception {
        verifyNoMoreInteractions(preparedMocks);
        mocks.close();
    }

    @Test
    @SneakyThrows
    void requestSuccessWithServiceRole() {
        when(httpResponse.getEntity()).thenReturn(new StringEntity(""));
        when(httpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("", 0, 0), 200, ""));
        when(httpClient.execute(any(), eq(responseHandler))).thenReturn(new byte[0]);
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("Service", "Domain")
                        .header("X-Request-ID", randomUUID())
                        .header("X-Request-Deadline", Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is2xxSuccessful());
        verify(httpClient, times(1)).execute(any(), eq(responseHandler));
    }

    @Test
    @SneakyThrows
    void requestSuccessWithMethodRole() {
        when(httpResponse.getEntity()).thenReturn(new StringEntity(""));
        when(httpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("", 0, 0), 200, ""));
        when(httpClient.execute(any(), eq(responseHandler))).thenReturn(new byte[0]);
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("Service", "messages")
                        .header("X-Request-ID", randomUUID())
                        .header("X-Request-Deadline", Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is2xxSuccessful());
        verify(httpClient, times(1)).execute(any(), eq(responseHandler));
    }

    @Test
    @SneakyThrows
    void requestSuccessWithWoodyHeaders() {
        when(httpResponse.getEntity()).thenReturn(new StringEntity(""));
        when(httpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("", 0, 0), 200, ""));
        when(httpClient.execute(any(), eq(responseHandler))).thenReturn(new byte[0]);
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("Service", "Domain")
                        .header("X-Request-ID", randomUUID())
                        .header("X-Request-Deadline", Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .header("woody.parent-id", "parent")
                        .header("woody.trace-id", "trace")
                        .header("woody.span-id", "span")
                        .header("woody.deadline", "deadline")
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is2xxSuccessful());
        verify(httpClient, times(1)).execute(any(), eq(responseHandler));
    }

    @Test
    @SneakyThrows
    void requestSuccessWithWoodyWithDashHeaders() {
        when(httpResponse.getEntity()).thenReturn(new StringEntity(""));
        when(httpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("", 0, 0), 200, ""));
        when(httpClient.execute(any(), eq(responseHandler))).thenReturn(new byte[0]);
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("Service", "Domain")
                        .header("X-Request-ID", randomUUID())
                        .header("X-Request-Deadline", Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .header("x-woody-parent-id", "parent")
                        .header("x-woody-trace-id", "trace")
                        .header("x-woody-span-id", "span")
                        .header("x-woody-deadline", "deadline")
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is2xxSuccessful());
        verify(httpClient, times(1)).execute(any(), eq(responseHandler));
    }
}
