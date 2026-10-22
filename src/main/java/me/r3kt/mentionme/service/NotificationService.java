package me.r3kt.mentionme.service;

import me.r3kt.mentionme.MentionMePlugin;
import me.r3kt.mentionme.model.Preferences;
import me.r3kt.mentionme.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

public final class NotificationService {
    private final MentionMePlugin plugin;

    public NotificationService(MentionMePlugin plugin) {
        this.plugin = plugin;
    }

    public void notify(Player target, Player sender, Preferences preferences) {
        if (!preferences.mentions()) return;

        Map<String, String> placeholders = Map.of(
                "sender", sender.getName(),
                "player", target.getName()
        );

        if (preferences.actionbar() && plugin.getConfig().getBoolean("notifications.actionbar.enabled", true)) {
            target.sendActionBar(Text.component(
                    plugin.getConfig().getString("notifications.actionbar.message", ""),
                    placeholders
            ));
        }

        if (preferences.sound() && plugin.getConfig().getBoolean("notifications.sound.enabled", true)) {
            String name = plugin.getConfig().getString("notifications.sound.name", "BLOCK_NOTE_BLOCK_PLING");

            try {
                Sound sound = Sound.valueOf(name.toUpperCase(Locale.ROOT));
                target.playSound(
                        target.getLocation(),
                        sound,
                        (float) plugin.getConfig().getDouble("notifications.sound.volume", 1.0),
                        (float) plugin.getConfig().getDouble("notifications.sound.pitch", 1.2)
                );
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (preferences.title() && plugin.getConfig().getBoolean("notifications.title.enabled", false)) {
            Title.Times times = Title.Times.times(
                    Duration.ofMillis(plugin.getConfig().getLong("notifications.title.fade-in", 5) * 50L),
                    Duration.ofMillis(plugin.getConfig().getLong("notifications.title.stay", 40) * 50L),
                    Duration.ofMillis(plugin.getConfig().getLong("notifications.title.fade-out", 10) * 50L)
            );

            target.showTitle(Title.title(
                    Text.component(plugin.getConfig().getString("notifications.title.title", ""), placeholders),
                    Text.component(plugin.getConfig().getString("notifications.title.subtitle", ""), placeholders),
                    times
            ));
        }
    }
}
