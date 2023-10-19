package dev.vality.wachter.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

public class WoodyHeaderTest {

    @Test
    public void name() {
        var headers = Set.of("kekw", "", "woody-parent-id", "woody-trace-id", "woody-span-id", "woody-deadline");
        var collect = headers.stream().filter(s -> s.startsWith("woody-")).map(s -> s.replaceAll("woody-", "woody.")).collect(Collectors.toSet());
        Assertions.assertEquals(Set.of("woody.trace-id", "woody.parent-id", "woody.span-id", "woody.deadline"), collect);
    }
}
