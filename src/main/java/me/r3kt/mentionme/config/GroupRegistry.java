package me.r3kt.mentionme.config;

import me.r3kt.mentionme.model.GroupMention;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class GroupRegistry {
    private final File file;
    private final AtomicReference<List<GroupMention>> groups = new AtomicReference<>(List.of());

    public GroupRegistry(File file) {
        this.file = file;
    }

    public void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("groups");
        if (section == null) {
            groups.set(List.of());
            return;
        }

        List<GroupMention> loaded = new ArrayList<>();

        for (String id : section.getKeys(false)) {
            String base = "groups." + id + ".";
            String prefix = yaml.getString(base + "prefix", "@" + id);
            if (prefix == null || prefix.isBlank()) continue;

            loaded.add(new GroupMention(
                    id.toLowerCase(Locale.ROOT),
                    yaml.getBoolean(base + "enabled", true),
                    yaml.getString(base + "name", id),
                    prefix,
                    yaml.getString(base + "sender-permission", "mentionme.group." + id),
                    yaml.getString(base + "target-permission", "mentionme.group." + id + ".receive"),
                    Math.max(0, yaml.getLong(base + "cooldown-seconds", 20)),
                    yaml.getString(base + "highlight-format", "<#F0C75E>" + prefix + "</#F0C75E>")
            ));
        }

        groups.set(List.copyOf(loaded));
    }

    public List<GroupMention> all() {
        return groups.get();
    }

    public Map<String, GroupMention> byPrefixLowercase() {
        return groups.get().stream().collect(Collectors.toUnmodifiableMap(
                group -> group.prefix().toLowerCase(Locale.ROOT),
                group -> group,
                (first, second) -> first
        ));
    }
}
