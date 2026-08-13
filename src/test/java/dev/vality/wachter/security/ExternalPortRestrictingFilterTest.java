package dev.vality.wachter.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExternalPortRestrictingFilterTest {

    private static final int API_PORT = 8022;
    private static final int MANAGEMENT_PORT = 8023;

    private final ExternalPortRestrictingFilter filter = new ExternalPortRestrictingFilter(API_PORT);

    @Test
    void shouldAllowWachterEndpointOnApiPort() throws Exception {
        var request = request(API_PORT, "/wachter");
        var response = new MockHttpServletResponse();
        var filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertAll(
                () -> assertNotNull(filterChain.getRequest()),
                () -> assertEquals(200, response.getStatus()));
    }

    @Test
    void shouldRejectOtherEndpointOnApiPort() throws Exception {
        var request = request(API_PORT, "/actuator/health");
        var response = new MockHttpServletResponse();
        var filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertAll(
                () -> assertNull(filterChain.getRequest()),
                () -> assertEquals(404, response.getStatus()),
                () -> assertEquals("Unknown address", response.getErrorMessage()));
    }

    @Test
    void shouldNotRestrictManagementPort() throws Exception {
        var request = request(MANAGEMENT_PORT, "/actuator/health/readiness");
        var response = new MockHttpServletResponse();
        var filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertAll(
                () -> assertNotNull(filterChain.getRequest()),
                () -> assertEquals(200, response.getStatus()));
    }

    private MockHttpServletRequest request(int port, String servletPath) {
        var request = new MockHttpServletRequest("GET", servletPath);
        request.setLocalPort(port);
        request.setServletPath(servletPath);
        return request;
    }
}
