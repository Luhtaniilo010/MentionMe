package me.r3kt.mentionme.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownService {
    private final Map<String, Map<UUID, Long>> cooldowns = new ConcurrentHashMap<>();

    public long remaining(String key, UUID uuid, long seconds, long now) {
        if (seconds <= 0) return 0;

        Map<UUID, Long> bucket = cooldowns.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
        long left = bucket.getOrDefault(uuid, 0L) + seconds * 1000L - now;
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    public void mark(String key, UUID uuid, long now) {
        cooldowns.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(uuid, now);
    }

    public void clear(UUID uuid) {
        for (Map<UUID, Long> bucket : cooldowns.values()) {
            bucket.remove(uuid);
        }
    }

    public void clearAll() {
        cooldowns.clear();
    }
}
