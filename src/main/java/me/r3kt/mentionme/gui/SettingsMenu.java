package me.r3kt.mentionme.gui;

import me.r3kt.mentionme.MentionMePlugin;
import me.r3kt.mentionme.model.PreferenceKey;
import me.r3kt.mentionme.model.Preferences;
import me.r3kt.mentionme.util.Text;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class SettingsMenu {
    private final MentionMePlugin plugin;

    public SettingsMenu(MentionMePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (!plugin.preferences().loaded(player.getUniqueId())) {
            plugin.sendLines(player, "messages.loading");
            return;
        }

        int size = normalize(plugin.getConfig().getInt("settings-menu.size", 27));
        Inventory inventory = plugin.getServer().createInventory(
                new SettingsHolder(),
                size,
                Text.component(plugin.getConfig().getString("settings-menu.title", "<dark_gray>Mention Settings"))
        );

        Material fillerMaterial = Material.matchMaterial(plugin.getConfig().getString("settings-menu.filler.material", "GRAY_STAINED_GLASS_PANE"));
        ItemStack filler = item(
                fillerMaterial == null ? Material.GRAY_STAINED_GLASS_PANE : fillerMaterial,
                plugin.getConfig().getString("settings-menu.filler.name", " "),
                List.of(),
                false
        );

        for (int i = 0; i < size; i++) {
            inventory.setItem(i, filler);
        }

        Preferences preferences = plugin.preferences().get(player.getUniqueId());

        for (PreferenceKey key : PreferenceKey.values()) {
            String base = "settings-menu.items." + key.id() + ".";
            int slot = plugin.getConfig().getInt(base + "slot", -1);
            if (slot < 0 || slot >= size) continue;

            Material material = Material.matchMaterial(plugin.getConfig().getString(base + "material", "PAPER"));
            List<String> lore = new ArrayList<>(plugin.getConfig().getStringList(base + "lore"));
            boolean enabled = preferences.value(key);
            lore.add("");
            lore.add(plugin.getConfig().getString("settings-menu." + (enabled ? "enabled" : "disabled") + ".lore", ""));
            lore.add("");
            lore.add(plugin.getConfig().getString("settings-menu.action", ""));

            inventory.setItem(slot, item(
                    material == null ? Material.PAPER : material,
                    plugin.getConfig().getString(base + "name", key.id()),
                    lore,
                    enabled && plugin.getConfig().getBoolean("settings-menu.enabled.glow", true)
            ));
        }

        player.openInventory(inventory);
    }

    public PreferenceKey at(int slot) {
        for (PreferenceKey key : PreferenceKey.values()) {
            if (plugin.getConfig().getInt("settings-menu.items." + key.id() + ".slot", -1) == slot) {
                return key;
            }
        }
        return null;
    }

    private ItemStack item(Material material, String name, List<String> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.component(name));

        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Text.component(line));
        }
        meta.lore(lines);

        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private int normalize(int value) {
        int clamped = Math.max(9, Math.min(54, value));
        return ((clamped + 8) / 9) * 9;
    }
}
