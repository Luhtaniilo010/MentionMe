package me.r3kt.mentionme.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.r3kt.mentionme.MentionMePlugin;
import me.r3kt.mentionme.config.RuntimeConfig;
import me.r3kt.mentionme.model.GroupMention;
import me.r3kt.mentionme.model.Preferences;
import me.r3kt.mentionme.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MentionListener implements Listener {
    private final MentionMePlugin plugin;

    public MentionListener(MentionMePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        RuntimeConfig config = plugin.runtime();
        if (!config.mentionsEnabled()) return;

        Player sender = event.getPlayer();
        if (!sender.hasPermission("mentionme.use")) return;

        String plain = Text.plain(event.message());
        String lower = plain.toLowerCase(Locale.ROOT);

        Matcher matcher = plugin.playerPattern().matcher(plain);
        LinkedHashMap<String, UUID> directTargets = new LinkedHashMap<>();
        LinkedHashSet<String> offline = new LinkedHashSet<>();

        boolean everyone = false;
        boolean here = false;

        while (matcher.find()) {
            String token = matcher.group(1);

            if (token.equalsIgnoreCase("everyone")) {
                everyone = true;
                continue;
            }

            if (token.equalsIgnoreCase("here")) {
                here = true;
                continue;
            }

            UUID uuid = plugin.onlineUuid(token);
            if (uuid == null) {
                offline.add(token);
            } else {
                directTargets.put(token.toLowerCase(Locale.ROOT), uuid);
            }
        }

        List<GroupMention> triggeredGroups = new ArrayList<>();

        if (config.groupsEnabled()) {
            for (GroupMention group : plugin.groups().all()) {
                if (!group.enabled()) continue;
                if (containsToken(lower, group.prefix().toLowerCase(Locale.ROOT))) {
                    triggeredGroups.add(group);
                }
            }
        }

        if (directTargets.isEmpty() && offline.isEmpty() && !everyone && !here && triggeredGroups.isEmpty()) {
            return;
        }

        if (!sender.hasPermission("mentionme.bypass.limit")) {
            int max = config.maxPerMessage();
            if (directTargets.size() > max) {
                plugin.sync(() -> plugin.sendLines(sender, "messages.too-many", Map.of("limit", String.valueOf(max))));
                return;
            }
        }

        long now = System.currentTimeMillis();

        if ((!directTargets.isEmpty() || !offline.isEmpty()) && !sender.hasPermission("mentionme.bypass.cooldown")) {
            long remaining = plugin.cooldowns().remaining("direct", sender.getUniqueId(), config.mentionCooldownSeconds(), now);

            if (remaining > 0) {
                plugin.sync(() -> plugin.sendLines(sender, "messages.cooldown", Map.of("seconds", String.valueOf(remaining))));
                return;
            }

            plugin.cooldowns().mark("direct", sender.getUniqueId(), now);
        }

        Component changed = event.message();

        for (Map.Entry<String, UUID> entry : directTargets.entrySet()) {
            if (entry.getValue().equals(sender.getUniqueId()) && !config.allowSelfMention()) {
                plugin.sync(() -> plugin.sendLines(sender, "messages.self-disabled"));
                continue;
            }

            String actual = plugin.onlineName(entry.getValue());
            if (actual == null) actual = entry.getKey();

            changed = replaceIgnoreCase(
                    changed,
                    config.symbol() + actual,
                    Text.component(config.mentionHighlight(), Map.of("player", actual))
            );
        }

        if (everyone && config.everyoneEnabled() && sender.hasPermission("mentionme.everyone")) {
            changed = replaceIgnoreCase(changed, config.symbol() + "everyone", Text.component(config.everyoneHighlight()));
        }

        if (here && config.hereEnabled() && sender.hasPermission("mentionme.here")) {
            changed = replaceIgnoreCase(changed, config.symbol() + "here", Text.component(config.hereHighlight()));
        }

        for (GroupMention group : triggeredGroups) {
            if (sender.hasPermission(group.senderPermission())) {
                changed = replaceIgnoreCase(changed, group.prefix(), Text.component(group.highlightFormat()));
            }
        }

        event.message(changed);

        boolean doEveryone = everyone;
        boolean doHere = here;
        Set<UUID> direct = new LinkedHashSet<>(directTargets.values());
        List<String> offlineCopy = List.copyOf(offline);
        List<GroupMention> groupsCopy = List.copyOf(triggeredGroups);

        plugin.sync(() -> {
            for (String name : offlineCopy) {
                sender.sendActionBar(Text.component(
                        plugin.getConfig().getString("offline-player.actionbar", ""),
                        Map.of("player", name)
                ));
            }

            Set<UUID> notify = new LinkedHashSet<>(direct);

            if (doEveryone && config.everyoneEnabled()) {
                if (!sender.hasPermission("mentionme.everyone")) {
                    plugin.sendLines(sender, "messages.no-permission-everyone");
                } else if (allowedSpecial(sender, "everyone", config.everyoneCooldownSeconds(), now)) {
                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        notify.add(player.getUniqueId());
                    }
                }
            }

            if (doHere && config.hereEnabled()) {
                if (!sender.hasPermission("mentionme.here")) {
                    plugin.sendLines(sender, "messages.no-permission-here");
                } else if (allowedSpecial(sender, "here", config.hereCooldownSeconds(), now)) {
                    double radiusSquared = config.hereRadius() * config.hereRadius();
                    var senderLocation = sender.getLocation();

                    for (Player player : sender.getWorld().getPlayers()) {
                        if (player.getLocation().distanceSquared(senderLocation) <= radiusSquared) {
                            notify.add(player.getUniqueId());
                        }
                    }
                }
            }

            for (GroupMention group : groupsCopy) {
                if (!sender.hasPermission(group.senderPermission())) {
                    plugin.sendLines(sender, "messages.no-permission-group", Map.of("group", group.name()));
                    continue;
                }

                if (!allowedSpecial(sender, "group:" + group.id(), group.cooldownSeconds(), now)) {
                    continue;
                }

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (group.targetPermission().isBlank() || player.hasPermission(group.targetPermission())) {
                        notify.add(player.getUniqueId());
                    }
                }
            }

            for (UUID uuid : notify) {
                Player target = plugin.getServer().getPlayer(uuid);
                if (target == null) continue;
                if (uuid.equals(sender.getUniqueId()) && !config.allowSelfMention()) continue;

                Preferences preferences = plugin.preferences().get(uuid);
                plugin.notifications().notify(target, sender, preferences);
            }
        });
    }

    private boolean allowedSpecial(Player sender, String key, long seconds, long now) {
        if (sender.hasPermission("mentionme.bypass.cooldown")) return true;

        long remaining = plugin.cooldowns().remaining(key, sender.getUniqueId(), seconds, now);

        if (remaining > 0) {
            plugin.sendLines(sender, "messages.cooldown", Map.of("seconds", String.valueOf(remaining)));
            return false;
        }

        plugin.cooldowns().mark(key, sender.getUniqueId(), now);
        return true;
    }

    private boolean containsToken(String text, String token) {
        int index = text.indexOf(token);

        while (index >= 0) {
            int before = index - 1;
            int after = index + token.length();

            boolean leftOk = before < 0 || !Character.isLetterOrDigit(text.charAt(before)) && text.charAt(before) != '_';
            boolean rightOk = after >= text.length() || !Character.isLetterOrDigit(text.charAt(after)) && text.charAt(after) != '_';

            if (leftOk && rightOk) return true;
            index = text.indexOf(token, index + 1);
        }

        return false;
    }

    private Component replaceIgnoreCase(Component source, String literal, Component replacement) {
        return source.replaceText(
                TextReplacementConfig.builder()
                        .match(Pattern.compile(Pattern.quote(literal), Pattern.CASE_INSENSITIVE))
                        .replacement(replacement)
                        .build()
        );
    }
}
