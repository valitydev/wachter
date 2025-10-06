package dev.vality.wachter.config.tracing;

import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Map;

final class MapHeaderSetter implements TextMapSetter<Map<String, String>> {

    static final MapHeaderSetter INSTANCE = new MapHeaderSetter();

    private MapHeaderSetter() {
    }

    @Override
    public void set(Map<String, String> carrier, String key, String value) {
        if (carrier == null || key == null || value == null) {
            return;
        }
        carrier.put(key, value);
    }
}
