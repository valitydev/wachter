package dev.vality.wachter.client;

import dev.vality.wachter.constants.RequestAttributeNames;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static dev.vality.wachter.constants.HeadersConstants.WOODY_TRACE_ID;
import static dev.vality.wachter.constants.HeadersConstants.X_WOODY_TRACE_ID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WachterClientOperationsTest {

    @Test
    void shouldSendRequestWithMergedHeaders() {
        final var builder = RestClient.builder();
        final var server = MockRestServiceServer.bindTo(builder).build();
        final var restClient = builder.build();

        final var servletRequest = new MockHttpServletRequest();
        servletRequest.setMethod("POST");
        servletRequest.addHeader("X-Custom", "custom-value");
        servletRequest.setAttribute(RequestAttributeNames.NORMALIZED_WOODY_HEADERS,
                Map.of(WOODY_TRACE_ID, "normalized-trace"));
        final var payload = "payload".getBytes();
        final var expectedResponse = "response".getBytes();

        server.expect(requestTo("http://upstream"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Custom", "custom-value"))
                .andExpect(header(WOODY_TRACE_ID, "normalized-trace"))
                .andExpect(header(X_WOODY_TRACE_ID, "normalized-trace"))
                .andExpect(content().bytes(payload))
                .andRespond(withSuccess(expectedResponse, MediaType.APPLICATION_OCTET_STREAM));

        final var factory = new WachterRequestFactory();
        final var client = new WachterClient(restClient, factory);

        final var actualResponse = client.send(servletRequest, payload, "http://upstream");

        assertEquals(HttpStatus.OK, actualResponse.statusCode());
        assertEquals(MediaType.APPLICATION_OCTET_STREAM_VALUE,
                actualResponse.headers().getFirst(HttpHeaders.CONTENT_TYPE));
        assertArrayEquals(expectedResponse, actualResponse.body());
        server.verify();
    }

    @Test
    void shouldHandleGetRequestWithoutBody() {
        final var builder = RestClient.builder();
        final var server = MockRestServiceServer.bindTo(builder).build();
        final var restClient = builder.build();

        final var servletRequest = new MockHttpServletRequest();
        servletRequest.setMethod("GET");
        servletRequest.addHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
        servletRequest.setAttribute(RequestAttributeNames.NORMALIZED_WOODY_HEADERS, Map.of());

        server.expect(requestTo("http://upstream/resource"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        final var factory = new WachterRequestFactory();
        final var client = new WachterClient(restClient, factory);

        final var response = client.send(servletRequest, null, "http://upstream/resource");

        assertEquals(HttpStatus.OK, response.statusCode());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.headers().getFirst(HttpHeaders.CONTENT_TYPE));
        assertArrayEquals("{}".getBytes(), response.body());
        server.verify();
    }

    @Test
    void shouldReturnErrorResponseWithoutThrowing() {
        final var builder = RestClient.builder();
        final var server = MockRestServiceServer.bindTo(builder).build();
        final var restClient = builder.build();

        final var servletRequest = new MockHttpServletRequest();
        servletRequest.setMethod("POST");
        servletRequest.setAttribute(RequestAttributeNames.NORMALIZED_WOODY_HEADERS, Map.of());
        final var payload = "payload".getBytes();

        server.expect(requestTo("http://upstream/fail"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .body("bad-gateway")
                        .contentType(MediaType.TEXT_PLAIN));

        final var factory = new WachterRequestFactory();
        final var client = new WachterClient(restClient, factory);

        final var response = client.send(servletRequest, payload, "http://upstream/fail");

        assertEquals(HttpStatus.BAD_GATEWAY, response.statusCode());
        assertEquals(MediaType.TEXT_PLAIN_VALUE, response.headers().getFirst(HttpHeaders.CONTENT_TYPE));
        assertArrayEquals("bad-gateway".getBytes(), response.body());
        server.verify();
    }
}
