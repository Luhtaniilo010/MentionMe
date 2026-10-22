package me.r3kt.mentionme.model;

public record Preferences(
        boolean mentions,
        boolean sound,
        boolean actionbar,
        boolean title
) {
    public Preferences toggle(PreferenceKey key) {
        return switch (key) {
            case MENTIONS -> new Preferences(!mentions, sound, actionbar, title);
            case SOUND -> new Preferences(mentions, !sound, actionbar, title);
            case ACTIONBAR -> new Preferences(mentions, sound, !actionbar, title);
            case TITLE -> new Preferences(mentions, sound, actionbar, !title);
        };
    }

    public boolean value(PreferenceKey key) {
        return switch (key) {
            case MENTIONS -> mentions;
            case SOUND -> sound;
            case ACTIONBAR -> actionbar;
            case TITLE -> title;
        };
    }

    public static Preferences defaults() {
        return new Preferences(true, true, true, true);
    }
}
