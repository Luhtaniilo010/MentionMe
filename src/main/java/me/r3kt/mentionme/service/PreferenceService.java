package me.r3kt.mentionme.service;

import me.r3kt.mentionme.model.PreferenceKey;
import me.r3kt.mentionme.model.Preferences;
import me.r3kt.mentionme.storage.PreferenceStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PreferenceService {
    private final PreferenceStore store;
    private final Map<UUID, Preferences> cache = new ConcurrentHashMap<>();

    public PreferenceService(PreferenceStore store) {
        this.store = store;
    }

    public CompletableFuture<Void> load(UUID uuid) {
        return store.load(uuid).thenAccept(preferences -> cache.put(uuid, preferences));
    }

    public boolean loaded(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public Preferences get(UUID uuid) {
        return cache.getOrDefault(uuid, Preferences.defaults());
    }

    public CompletableFuture<Preferences> toggle(UUID uuid, PreferenceKey key) {
        Preferences previous = get(uuid);
        Preferences updated = previous.toggle(key);
        cache.put(uuid, updated);

        return store.save(uuid, updated)
                .thenApply(ignored -> updated)
                .exceptionallyCompose(error -> {
                    cache.put(uuid, previous);
                    return CompletableFuture.failedFuture(error);
                });
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    public void clearCache() {
        cache.clear();
    }
}
