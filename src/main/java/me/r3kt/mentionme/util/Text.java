package me.r3kt.mentionme.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Map;

public final class Text {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private Text() {}

    public static Component component(String raw, Map<String, String> placeholders) {
        if (raw == null) return Component.empty();
        String value = raw;
        for (var entry : placeholders.entrySet()) {
            value = value.replace("%" + entry.getKey() + "%", escape(entry.getValue()));
        }
        return MINI.deserialize(value);
    }

    public static Component component(String raw) {
        return component(raw, Map.of());
    }

    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("<", "\\<");
    }
}
