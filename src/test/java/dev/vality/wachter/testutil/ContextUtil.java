package dev.vality.wachter.testutil;

import dev.vality.geck.serializer.kit.mock.FieldHandler;
import dev.vality.geck.serializer.kit.mock.MockMode;
import dev.vality.geck.serializer.kit.mock.MockTBaseProcessor;
import dev.vality.geck.serializer.kit.tbase.TBaseHandler;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.thrift.TBase;

import java.time.Instant;
import java.util.Map;

@UtilityClass
public class ContextUtil {

    private static final MockTBaseProcessor mockRequiredTBaseProcessor;

    static {
        mockRequiredTBaseProcessor = new MockTBaseProcessor(MockMode.REQUIRED_ONLY, 15, 1);
        Map.Entry<FieldHandler, String[]> timeFields = Map.entry(
                structHandler -> structHandler.value(Instant.now().toString()),
                new String[]{"conversation_id", "messages", "status", "user_id", "email", "fullname",
                        "held_until", "from_time", "to_time"}
        );
        mockRequiredTBaseProcessor.addFieldHandler(timeFields.getKey(), timeFields.getValue());
    }

    @SneakyThrows
    public static <T extends TBase> T fillRequiredTBaseObject(T tbase, Class<T> type) {
        return ContextUtil.mockRequiredTBaseProcessor.process(tbase, new TBaseHandler<>(type));
    }
}
