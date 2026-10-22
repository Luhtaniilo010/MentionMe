package me.r3kt.mentionme.storage;

import me.r3kt.mentionme.model.Preferences;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LegacyPreferencesMigrator {
    private LegacyPreferencesMigrator() {}

    public static CompletableFuture<Void> migrate(File legacyFile, PreferenceStore store) {
        if (!legacyFile.exists()) {
            return CompletableFuture.completedFuture(null);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacyFile);
        List<CompletableFuture<Void>> saves = new ArrayList<>();

        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                Preferences preferences = new Preferences(
                        yaml.getBoolean(key + ".enabled", true),
                        yaml.getBoolean(key + ".sound", true),
                        true,
                        true
                );
                saves.add(store.save(uuid, preferences));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new))
                .thenRun(() -> {
                    File migrated = new File(legacyFile.getParentFile(), "players.yml.migrated");
                    if (!migrated.exists()) {
                        legacyFile.renameTo(migrated);
                    }
                });
    }
}
