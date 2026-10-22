package me.r3kt.mentionme.command;

import me.r3kt.mentionme.MentionMePlugin;
import me.r3kt.mentionme.model.PreferenceKey;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class MentionCommand implements CommandExecutor, TabCompleter {
    private final MentionMePlugin plugin;

    public MentionCommand(MentionMePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("mentionme")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("mentionme.reload")) {
                    plugin.sendLines(sender, "messages.no-permission");
                    return true;
                }

                plugin.reloadRuntime();
                plugin.sendLines(sender, "messages.reload");
                return true;
            }

            plugin.sendLines(sender, "messages.help");

            if (sender.hasPermission("mentionme.admin")) {
                plugin.sendLines(sender, "messages.admin-help");
            }

            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is player-only.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("msettings")) {
            plugin.menu().open(player);
            return true;
        }

        if (args.length == 0) {
            toggle(player, PreferenceKey.MENTIONS);
            return true;
        }

        if (args[0].equalsIgnoreCase("settings")) {
            plugin.menu().open(player);
            return true;
        }

        PreferenceKey key = args[0].equalsIgnoreCase("toggle")
                ? PreferenceKey.MENTIONS
                : PreferenceKey.from(args[0]);

        if (key == null) {
            plugin.sendLines(player, "messages.help");
            return true;
        }

        toggle(player, key);
        return true;
    }

    private void toggle(Player player, PreferenceKey key) {
        if (!plugin.preferences().loaded(player.getUniqueId())) {
            plugin.sendLines(player, "messages.loading");
            return;
        }

        plugin.preferences().toggle(player.getUniqueId(), key).thenAccept(updated ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    boolean enabled = updated.value(key);
                    plugin.sendLines(player, "messages." + key.id() + "-" + (enabled ? "enabled" : "disabled"));
                })
        ).exceptionally(error -> {
            plugin.getLogger().warning("Could not save settings for " + player.getUniqueId() + ": " + error.getMessage());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) plugin.sendLines(player, "messages.save-failed");
            });
            return null;
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();

        List<String> values;

        if (command.getName().equalsIgnoreCase("mentionme")) {
            values = sender.hasPermission("mentionme.reload") ? List.of("help", "reload") : List.of("help");
        } else if (command.getName().equalsIgnoreCase("mentions")) {
            values = List.of("toggle", "sound", "actionbar", "title", "settings");
        } else {
            return List.of();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(input)).toList();
    }
}
