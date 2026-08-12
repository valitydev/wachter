package dev.vality.wachter.tracing;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.vality.wachter.auth.utils.JwtTokenBuilder;
import dev.vality.wachter.auth.utils.KeycloakOpenIdStub;
import dev.vality.wachter.testutil.TMessageUtil;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static dev.vality.wachter.tracing.TraceHeaders.OTEL_TRACE_PARENT;
import static org.junit.jupiter.api.Assertions.*;

class OpenTelemetryAgentTest {

    private static final String TRACEPARENT_PATTERN = "00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}";
    private static final String TRACE_ID = "11111111111111111111111111111111";
    private static final String SPAN_ID = "2222222222222222";

    @Test
    void shouldContinueIncomingTraceAndCreateTraceWhenMissing() throws Exception {
        var wireMock = new WireMockServer(0);
        wireMock.start();
        var application = startApplication(wireMock);
        try {
            var jwt = configureAuthentication(wireMock);
            wireMock.stubFor(post(urlEqualTo("/upstream"))
                    .willReturn(aResponse().withStatus(200).withBody("ok")));
            awaitApplication(application.port(), application.process());

            var incomingTraceparent = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";
            sendRequest(application.port(), jwt, incomingTraceparent);
            var continued = singleUpstreamTraceparent(wireMock);

            assertAll(
                    () -> assertTrue(continued.matches(TRACEPARENT_PATTERN), continued),
                    () -> assertEquals(TRACE_ID, traceId(continued)),
                    () -> assertNotEquals(SPAN_ID, spanId(continued)));

            wireMock.resetRequests();
            sendRequest(application.port(), jwt, null);
            var created = singleUpstreamTraceparent(wireMock);

            assertAll(
                    () -> assertTrue(created.matches(TRACEPARENT_PATTERN), created),
                    () -> assertNotEquals("00000000000000000000000000000000", traceId(created)),
                    () -> assertNotEquals("0000000000000000", spanId(created)));
        } finally {
            stop(application.process());
            wireMock.stop();
        }
    }

    private AgentApplication startApplication(WireMockServer wireMock) throws Exception {
        var port = availablePort();
        var managementPort = availablePort();
        var java = Path.of(System.getProperty("java.home"), "bin", "java");
        var agent = Path.of("target/maven-shared-archive-resources/opentelemetry-javaagent.jar")
                .toAbsolutePath();
        assertTrue(Files.isRegularFile(agent), "Packaged OpenTelemetry agent is missing");
        var process = new ProcessBuilder(
                java.toString(),
                "-javaagent:" + agent,
                "-cp", System.getProperty("java.class.path"),
                "dev.vality.wachter.WachterApplication",
                "--server.port=" + port,
                "--management.server.port=" + managementPort,
                "--spring.security.oauth2.resourceserver.url=" + wireMock.baseUrl(),
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri="
                        + wireMock.baseUrl() + "/auth/realms/internal",
                "--wachter.services.Domain.url=" + wireMock.baseUrl() + "/upstream")
                .redirectErrorStream(true)
                .redirectOutput(Path.of("target/opentelemetry-agent-test.log").toFile());
        var environment = process.environment();
        environment.put("OTEL_TRACES_EXPORTER", "none");
        environment.put("OTEL_METRICS_EXPORTER", "none");
        environment.put("OTEL_LOGS_EXPORTER", "none");
        environment.put("OTEL_SERVICE_NAME", "wachter-agent-test");
        return new AgentApplication(process.start(), port);
    }

    private String configureAuthentication(WireMockServer wireMock) throws Exception {
        configureFor("localhost", wireMock.port());
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var tokenBuilder = new JwtTokenBuilder(generator.generateKeyPair());
        var keycloak = new KeycloakOpenIdStub(wireMock.baseUrl() + "/auth", "internal", tokenBuilder);
        keycloak.givenStub();
        return keycloak.generateJwt("Domain", "messages:methodName");
    }

    private void sendRequest(int port, String jwt, String traceparent) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/wachter"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + jwt)
                .header("Service", "Domain")
                .header("X-Request-ID", "agent-test")
                .header("X-Request-Deadline", Instant.now().plusSeconds(30).toString())
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                        TMessageUtil.createTMessage(new TBinaryProtocol.Factory())))
                .build();
        if (traceparent != null) {
            request = HttpRequest.newBuilder(request, (name, value) -> true)
                    .header(OTEL_TRACE_PARENT, traceparent)
                    .build();
        }
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
    }

    private String singleUpstreamTraceparent(WireMockServer wireMock) {
        var requests = wireMock.findAll(postRequestedFor(urlEqualTo("/upstream")));
        assertEquals(1, requests.size());
        return requests.getFirst().getHeader(OTEL_TRACE_PARENT);
    }

    private void awaitApplication(int port, Process process) throws Exception {
        var deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                fail("Wachter agent process exited with code " + process.exitValue());
            }
            try (var socket = new java.net.Socket("localhost", port)) {
                return;
            } catch (java.io.IOException ignored) {
                Thread.sleep(100);
            }
        }
        fail("Timed out waiting for Wachter agent process");
    }

    private void stop(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private int availablePort() throws Exception {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private String traceId(String traceparent) {
        return traceparent.split("-")[1];
    }

    private String spanId(String traceparent) {
        return traceparent.split("-")[2];
    }

    private record AgentApplication(Process process, int port) {
    }
}
