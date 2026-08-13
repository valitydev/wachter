package dev.vality.wachter.client;

import dev.vality.woody.api.flow.WFlow;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static dev.vality.wachter.tracing.TraceHeaders.ExternalHeaders.X_WOODY_TRACE_ID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WachterClientOperationsTest {

    @Test
    void shouldSendRequestWithTracingHeaders() throws Exception {
        final var builder = RestClient.builder();
        final var server = MockRestServiceServer.bindTo(builder).build();
        final var restClient = builder.build();

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

        final var actualResponse = new WFlow().createServiceFork(
                () -> client.send(servletRequest, payload, "http://upstream")).call();

        assertEquals(HttpStatus.OK, actualResponse.statusCode());
        assertArrayEquals(expectedResponse, actualResponse.body());
        server.verify();
    }

    @Test
    void shouldFilterDisallowedHeaders() throws Exception {
        final var builder = RestClient.builder();
        final var server = MockRestServiceServer.bindTo(builder).build();
        final var restClient = builder.build();

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

        new WFlow().createServiceFork(
                () -> client.send(servletRequest, null, "http://upstream/disallowed")).call();

        server.verify();
    }

    @Test
    void shouldHandleGetRequestWithoutBody() throws Exception {
        final var builder = RestClient.builder();
        final var server = MockRestServiceServer.bindTo(builder).build();
        final var restClient = builder.build();

        final var servletRequest = new MockHttpServletRequest();
        servletRequest.setMethod("GET");

        server.expect(requestTo("http://upstream/resource"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        final var client = new WachterClient(restClient);

        final var response = new WFlow().createServiceFork(
                () -> client.send(servletRequest, null, "http://upstream/resource")).call();

        assertEquals(HttpStatus.OK, response.statusCode());
        assertArrayEquals("{}".getBytes(), response.body());
        server.verify();
    }

    @Test
    void shouldReturnErrorResponseWithoutThrowing() throws Exception {
        final var builder = RestClient.builder();
        final var server = MockRestServiceServer.bindTo(builder).build();
        final var restClient = builder.build();

        final var servletRequest = new MockHttpServletRequest();
        servletRequest.setMethod("POST");
        final var payload = "payload".getBytes();

        server.expect(requestTo("http://upstream/fail"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .body("bad-gateway")
                        .contentType(MediaType.TEXT_PLAIN));

        final var client = new WachterClient(restClient);

        final var response = new WFlow().createServiceFork(
                () -> client.send(servletRequest, payload, "http://upstream/fail")).call();

        assertEquals(HttpStatus.BAD_GATEWAY, response.statusCode());
        assertArrayEquals("bad-gateway".getBytes(), response.body());
        server.verify();
    }

}
