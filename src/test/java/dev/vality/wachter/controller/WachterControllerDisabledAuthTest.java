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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"wachter.auth.enabled=false"})
class WachterControllerDisabledAuthTest extends AbstractKeycloakOpenIdAsWiremockConfig {

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
    void requestSuccess() {
        when(wachterClient.send(any(), any(), any()))
                .thenReturn(new WachterClientResponse(HttpStatus.OK, new HttpHeaders(), new byte[0]));
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithoutRoles())
                        .header("Service", "messages")
                        .header("X-Request-ID", randomUUID())
                        .header("X-Request-Deadline", Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is2xxSuccessful());
        verify(wachterClient, times(1)).send(any(), any(), any());
    }

}
