package dev.vality.wachter.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static dev.vality.wachter.constants.HeadersConstants.WOODY_DEPRECATED_PREFIX;
import static dev.vality.wachter.constants.HeadersConstants.X_WOODY_PREFIX;

public class WoodyHeaderTest {

    @Test
    public void nawoodyDeprecatedHeadersTest() {
        var headers = Set.of(
                "kekw", "", "x-woody-parent-id", "x-woody-trace-id", "x-woody-span-id", "x-woody-deadline");
        var woodyDeprecatedHeaders = headers.stream()
                .filter(s -> s.startsWith(X_WOODY_PREFIX))
                .map(s -> s.replaceAll(X_WOODY_PREFIX, WOODY_DEPRECATED_PREFIX))
                .collect(Collectors.toSet());
        Assertions.assertEquals(
                Set.of("woody.trace-id", "woody.parent-id", "woody.span-id", "woody.deadline"),
                woodyDeprecatedHeaders);
    }
}
