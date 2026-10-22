package me.r3kt.mentionme.listener;

import me.r3kt.mentionme.MentionMePlugin;
import me.r3kt.mentionme.gui.SettingsHolder;
import me.r3kt.mentionme.model.PreferenceKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class SettingsListener implements Listener {
    private final MentionMePlugin plugin;

    public SettingsListener(MentionMePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.track(event.getPlayer());
        plugin.loadPreferences(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.untrack(event.getPlayer());
        plugin.preferences().unload(event.getPlayer().getUniqueId());
        plugin.cooldowns().clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SettingsHolder)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;

        PreferenceKey key = plugin.menu().at(event.getRawSlot());
        if (key == null) return;

        if (!plugin.preferences().loaded(player.getUniqueId())) {
            plugin.sendLines(player, "messages.loading");
            return;
        }

        plugin.preferences().toggle(player.getUniqueId(), key).thenAccept(updated ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) plugin.menu().open(player);
                })
        ).exceptionally(error -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) plugin.sendLines(player, "messages.save-failed");
            });
            return null;
        });
    }
}
