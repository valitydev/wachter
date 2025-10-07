package dev.vality.wachter.client;

import dev.vality.wachter.http.HttpHeadersPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class WachterClientExtractTest {

    @Test
    public void shouldFormatAndTruncateParameters() {
        final var request = new MockHttpServletRequest();
        request.addParameter("short", "value");
        request.addParameter("long", "12345678901234567890123456789012345");
        request.addParameter("multi", "first");
        request.addParameter("multi", "second");

        final var factory = new WachterRequestFactory(new HttpHeadersPolicy());
        String result = factory.extract(request);

        Assertions.assertEquals(
                "short=value, long=12345678901234567890123456789012345, multi=first, second",
                result);
    }
}
