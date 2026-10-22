package me.r3kt.mentionme.config;

import org.bukkit.configuration.file.FileConfiguration;

public record RuntimeConfig(
        boolean mentionsEnabled,
        String symbol,
        boolean allowSelfMention,
        int maxPerMessage,
        long mentionCooldownSeconds,
        String mentionHighlight,
        boolean everyoneEnabled,
        long everyoneCooldownSeconds,
        String everyoneHighlight,
        boolean hereEnabled,
        double hereRadius,
        long hereCooldownSeconds,
        String hereHighlight,
        boolean groupsEnabled
) {
    public static RuntimeConfig from(FileConfiguration config) {
        String symbol = config.getString("mentions.symbol", "@");
        if (symbol == null || symbol.isBlank()) symbol = "@";

        return new RuntimeConfig(
                config.getBoolean("mentions.enabled", true),
                symbol,
                config.getBoolean("mentions.allow-self-mention", true),
                Math.max(1, Math.min(100, config.getInt("mentions.max-per-message", 5))),
                Math.max(0, config.getLong("mentions.cooldown-seconds", 3)),
                config.getString("mentions.highlight-format", "<#FFD65A>@%player%</#FFD65A>"),
                config.getBoolean("everyone.enabled", true),
                Math.max(0, config.getLong("everyone.cooldown-seconds", 30)),
                config.getString("everyone.highlight-format", "<#55FF7F>@everyone</#55FF7F>"),
                config.getBoolean("here.enabled", true),
                Math.max(1.0, config.getDouble("here.radius", 50.0)),
                Math.max(0, config.getLong("here.cooldown-seconds", 15)),
                config.getString("here.highlight-format", "<#55FF7F>@here</#55FF7F>"),
                config.getBoolean("groups.enabled", true)
        );
    }
}
