package me.r3kt.mentionme.model;

import java.util.Locale;

public enum PreferenceKey {
    MENTIONS("mentions"),
    SOUND("sound"),
    ACTIONBAR("actionbar"),
    TITLE("title");

    private final String id;

    PreferenceKey(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static PreferenceKey from(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (PreferenceKey key : values()) {
            if (key.id.equals(normalized)) return key;
        }
        return null;
    }
}
