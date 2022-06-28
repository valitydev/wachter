package dev.vality.wachter.testutil;

import dev.vality.bouncer.ctx.ContextFragment;
import dev.vality.bouncer.decisions.*;
import dev.vality.geck.serializer.kit.mock.FieldHandler;
import dev.vality.geck.serializer.kit.mock.MockMode;
import dev.vality.geck.serializer.kit.mock.MockTBaseProcessor;
import dev.vality.geck.serializer.kit.tbase.TBaseHandler;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.thrift.TBase;
import org.apache.thrift.TSerializer;

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

    @SneakyThrows
    public static ContextFragment createContextFragment() {
        ContextFragment fragment = ContextUtil.fillRequiredTBaseObject(new ContextFragment(), ContextFragment.class);
        fragment.setContent(new TSerializer().serialize(new dev.vality.bouncer.context.v1.ContextFragment()));
        return fragment;
    }

    public static Judgement createJudgementAllowed() {
        Resolution resolution = new Resolution();
        resolution.setAllowed(new ResolutionAllowed());
        return new Judgement().setResolution(resolution);
    }

    public static Judgement createJudgementRestricted() {
        Resolution resolution = new Resolution();
        resolution.setRestricted(new ResolutionRestricted());
        return new Judgement().setResolution(resolution);
    }

    public static Judgement createJudgementForbidden() {
        Resolution resolution = new Resolution();
        resolution.setForbidden(new ResolutionForbidden());
        return new Judgement().setResolution(resolution);
    }
}
