package dev.vality.wachter.controller;

import dev.vality.wachter.config.AbstractKeycloakOpenIdAsWiremockConfig;
import dev.vality.wachter.exeptions.AuthorizationException;
import dev.vality.wachter.exeptions.WachterException;
import dev.vality.wachter.testutil.TMessageUtil;
import lombok.SneakyThrows;
import org.apache.http.client.HttpClient;
import org.apache.thrift.protocol.TProtocolFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"auth.enabled=true"})
class ErrorControllerTest extends AbstractKeycloakOpenIdAsWiremockConfig {

    @MockBean
    private HttpClient httpClient;

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
    void requestAccessDenied() {
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithoutRoles())
                        .header("Service", "messages")
                        .header("X-Request-ID", randomUUID())
                        .header("X-Request-Deadline", Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is4xxClientError())
                .andExpect(result -> assertTrue(result.getResolvedException() instanceof AuthorizationException))
                .andExpect(result -> assertEquals("User darkside-the-best@mail.com don't " +
                                "have roles with trace_id null",
                        result.getResolvedException().getMessage()));
    }

    @Test
    @SneakyThrows
    void requestWithoutServiceHeader() {
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("X-Request-ID", randomUUID())
                        .header("X-Request-Deadline", Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is4xxClientError())
                .andExpect(result -> assertTrue(result.getResolvedException() instanceof WachterException))
                .andExpect(result -> assertEquals("Header \"Service\" must be set",
                        result.getResolvedException().getMessage()));
    }

    @Test
    @SneakyThrows
    void requestWithWrongServiceHeader() {
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("X-Request-ID", randomUUID())
                        .header("Service", "wrong")
                        .header("X-Request-Deadline", Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is4xxClientError())
                .andExpect(result -> assertTrue(result.getResolvedException() instanceof WachterException))
                .andExpect(result -> assertEquals("Service \"wrong\" not found in configuration",
                        result.getResolvedException().getMessage()));
    }

    @Test
    @SneakyThrows
    void requestWithUnknownRoles() {
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("X-Request-ID", randomUUID())
                        .header("Service", "invoicing")
                        .header("X-Request-Deadline", Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is4xxClientError())
                .andExpect(result -> assertTrue(result.getResolvedException() instanceof AuthorizationException))
                .andExpect(result -> assertEquals("User darkside-the-best@mail.com don't have access" +
                                " to methodName in service Invoicing",
                        result.getResolvedException().getMessage()));
    }

    @Test
    @SneakyThrows
    void requestWithForbiddenMethod() {
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("X-Request-ID", randomUUID())
                        .header("Service", "DominantCache")
                        .header("X-Request-Deadline", Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is4xxClientError())
                .andExpect(result -> assertTrue(result.getResolvedException() instanceof AuthorizationException))
                .andExpect(result -> assertEquals("User darkside-the-best@mail.com don't have access" +
                                " to methodName in service DominantCache with trace_id null",
                        result.getResolvedException().getMessage()));
    }
}
