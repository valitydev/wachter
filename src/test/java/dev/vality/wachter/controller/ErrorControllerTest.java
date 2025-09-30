package dev.vality.wachter.controller;

import dev.vality.wachter.client.WachterClient;
import dev.vality.wachter.config.AbstractKeycloakOpenIdAsWiremockConfig;
import dev.vality.wachter.exceptions.AuthorizationException;
import dev.vality.wachter.exceptions.WachterException;
import dev.vality.wachter.testutil.TMessageUtil;
import lombok.SneakyThrows;
import org.apache.thrift.protocol.TProtocolFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"auth.enabled=true"})
@SuppressWarnings("LineLength")
class ErrorControllerTest extends AbstractKeycloakOpenIdAsWiremockConfig {

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
    void requestAccessDenied() {
        final var expected = "User darkside-the-best@mail.com don't have roles";
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithoutRoles())
                        .header("Service", "messages")
                        .header("X-Request-ID", randomUUID())
                        .header("X-Request-Deadline", Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is4xxClientError())
                .andExpect(result -> assertInstanceOf(AuthorizationException.class, result.getResolvedException()))
                .andExpect(result -> assertTrue(
                        Objects.requireNonNull(result.getResolvedException()).getMessage().contains(expected)));
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
                .andExpect(result -> assertInstanceOf(WachterException.class, result.getResolvedException()))
                .andExpect(result -> assertEquals("Header \"Service\" must be set",
                        Objects.requireNonNull(result.getResolvedException()).getMessage()));
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
                .andExpect(result -> assertInstanceOf(WachterException.class, result.getResolvedException()))
                .andExpect(result -> assertEquals("Service \"wrong\" not found in configuration",
                        Objects.requireNonNull(result.getResolvedException()).getMessage()));
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
                .andExpect(result -> assertInstanceOf(AuthorizationException.class, result.getResolvedException()))
                .andExpect(result -> assertEquals("User darkside-the-best@mail.com don't have access" +
                                " to methodName in service Invoicing",
                        Objects.requireNonNull(result.getResolvedException()).getMessage()));
    }

    @Test
    @SneakyThrows
    void requestWithBadSignToken() {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        final var keyPair = keyGen.generateKeyPair();

        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " +
                                generateSimpleJwtWithRolesAndCustomKey(keyPair.getPrivate()))
                        .header("X-Request-ID", randomUUID())
                        .header("Service", "Domain")
                        .header("X-Request-Deadline", Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @SneakyThrows
    void requestWithForbiddenMethod() {
        final var expected =
                "User darkside-the-best@mail.com don't have access to methodName in service DominantCache";
        mvc.perform(post("/wachter")
                        .header("Authorization", "Bearer " + generateSimpleJwtWithRoles())
                        .header("X-Request-ID", randomUUID())
                        .header("Service", "DominantCache")
                        .header("X-Request-Deadline", Instant.now().plus(1, ChronoUnit.DAYS).toString())
                        .content(TMessageUtil.createTMessage(protocolFactory)))
                .andDo(print())
                .andExpect(status().is4xxClientError())
                .andExpect(result -> assertInstanceOf(AuthorizationException.class, result.getResolvedException()))
                .andExpect(result -> assertTrue(
                        Objects.requireNonNull(result.getResolvedException()).getMessage().contains(expected)));
    }
}
