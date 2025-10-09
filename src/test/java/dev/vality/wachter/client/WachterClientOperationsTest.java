package dev.vality.wachter.client;

import dev.vality.woody.api.trace.TraceData;
import dev.vality.woody.api.trace.context.TraceContext;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static dev.vality.wachter.constants.TraceHeadersConstants.X_WOODY_TRACE_ID;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WachterClientOperationsTest {

    private SdkTracerProvider tracerProvider;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        GlobalOpenTelemetry.resetForTest();
        tracerProvider = SdkTracerProvider.builder().build();
        final var openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        GlobalOpenTelemetry.set(openTelemetry);
        tracer = openTelemetry.getTracer("test");
    }

    @AfterEach
    void tearDown() {
        TraceContext.setCurrentTraceData(null);
        GlobalOpenTelemetry.resetForTest();
        if (tracerProvider != null) {
            tracerProvider.close();
        }
    }

    @Test
    void shouldSendRequestWithTracingHeaders() {
        final var builder = RestClient.builder();
        final var server = MockRestServiceServer.bindTo(builder).build();
        final var restClient = builder.build();

        final var traceData = new TraceData();
        final var otelSpan = tracer.spanBuilder("test-span").startSpan();
        traceData.setOtelSpan(otelSpan);
        TraceContext.setCurrentTraceData(traceData);

        final var serviceSpan = traceData.getServiceSpan().getSpan();
        serviceSpan.setTraceId("test-trace-id");
        serviceSpan.setId("test-span-id");

        final var servletRequest = new MockHttpServletRequest();
        servletRequest.setMethod("POST");
        servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);
        servletRequest.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        servletRequest.addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        servletRequest.addHeader(HttpHeaders.ACCEPT_ENCODING, "gzip");
        final var payload = "payload".getBytes();
        final var expectedResponse = "response".getBytes();

        server.expect(requestTo("http://upstream"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().bytes(payload))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.ACCEPT_ENCODING, "gzip"))
                .andRespond(withSuccess(expectedResponse, MediaType.APPLICATION_OCTET_STREAM));

        final var client = new WachterClient(restClient);

        final var actualResponse = client.send(servletRequest, payload, "http://upstream");

        assertEquals(HttpStatus.OK, actualResponse.statusCode());
        assertArrayEquals(expectedResponse, actualResponse.body());
        server.verify();
        otelSpan.end();
    }

    @Test
    void shouldFilterDisallowedHeaders() {
        final var builder = RestClient.builder();
        final var server = MockRestServiceServer.bindTo(builder).build();
        final var restClient = builder.build();

        final var traceData = new TraceData();
        final var otelSpan = tracer.spanBuilder("test-span").startSpan();
        traceData.setOtelSpan(otelSpan);
        TraceContext.setCurrentTraceData(traceData);
        traceData.getServiceSpan().getSpan().setTraceId("filter-trace-id");
        traceData.getServiceSpan().getSpan().setId("filter-span-id");

        final var servletRequest = new MockHttpServletRequest();
        servletRequest.setMethod("POST");
        servletRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer secret");
        servletRequest.addHeader("Service", "Domain");
        servletRequest.addHeader("cf-ray", "test");
        servletRequest.addHeader(X_WOODY_TRACE_ID, "should-not-pass");

        server.expect(requestTo("http://upstream/disallowed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(headerDoesNotExist("Service"))
                .andExpect(headerDoesNotExist("cf-ray"))
                .andExpect(headerDoesNotExist(X_WOODY_TRACE_ID))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        final var client = new WachterClient(restClient);

        client.send(servletRequest, null, "http://upstream/disallowed");

        server.verify();
        otelSpan.end();
    }

    @Test
    void shouldHandleGetRequestWithoutBody() {
        final var builder = RestClient.builder();
        final var server = MockRestServiceServer.bindTo(builder).build();
        final var restClient = builder.build();

        final var traceData = new TraceData();
        final var otelSpan = tracer.spanBuilder("test-span").startSpan();
        traceData.setOtelSpan(otelSpan);
        TraceContext.setCurrentTraceData(traceData);

        final var serviceSpan = traceData.getServiceSpan().getSpan();
        serviceSpan.setTraceId("get-trace-id");

        final var servletRequest = new MockHttpServletRequest();
        servletRequest.setMethod("GET");

        server.expect(requestTo("http://upstream/resource"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        final var client = new WachterClient(restClient);

        final var response = client.send(servletRequest, null, "http://upstream/resource");

        assertEquals(HttpStatus.OK, response.statusCode());
        assertArrayEquals("{}".getBytes(), response.body());
        server.verify();
        otelSpan.end();
    }

    @Test
    void shouldReturnErrorResponseWithoutThrowing() {
        final var builder = RestClient.builder();
        final var server = MockRestServiceServer.bindTo(builder).build();
        final var restClient = builder.build();

        final var traceData = new TraceData();
        final var otelSpan = tracer.spanBuilder("test-span").startSpan();
        traceData.setOtelSpan(otelSpan);
        TraceContext.setCurrentTraceData(traceData);

        final var serviceSpan = traceData.getServiceSpan().getSpan();
        serviceSpan.setTraceId("error-trace-id");

        final var servletRequest = new MockHttpServletRequest();
        servletRequest.setMethod("POST");
        final var payload = "payload".getBytes();

        server.expect(requestTo("http://upstream/fail"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .body("bad-gateway")
                        .contentType(MediaType.TEXT_PLAIN));

        final var client = new WachterClient(restClient);

        final var response = client.send(servletRequest, payload, "http://upstream/fail");

        assertEquals(HttpStatus.BAD_GATEWAY, response.statusCode());
        assertArrayEquals("bad-gateway".getBytes(), response.body());
        server.verify();
        otelSpan.end();
    }
}
