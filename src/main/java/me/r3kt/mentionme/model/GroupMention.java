package me.r3kt.mentionme.model;

public record GroupMention(
        String id,
        boolean enabled,
        String name,
        String prefix,
        String senderPermission,
        String targetPermission,
        long cooldownSeconds,
        String highlightFormat
) {}
