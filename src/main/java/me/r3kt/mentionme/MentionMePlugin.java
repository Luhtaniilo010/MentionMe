package me.r3kt.mentionme;

import me.r3kt.mentionme.command.MentionCommand;
import me.r3kt.mentionme.config.GroupRegistry;
import me.r3kt.mentionme.config.RuntimeConfig;
import me.r3kt.mentionme.gui.SettingsMenu;
import me.r3kt.mentionme.listener.MentionListener;
import me.r3kt.mentionme.listener.SettingsListener;
import me.r3kt.mentionme.service.CooldownService;
import me.r3kt.mentionme.service.NotificationService;
import me.r3kt.mentionme.service.PreferenceService;
import me.r3kt.mentionme.storage.LegacyPreferencesMigrator;
import me.r3kt.mentionme.storage.PreferenceStore;
import me.r3kt.mentionme.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class MentionMePlugin extends JavaPlugin {
    private final Map<String, UUID> onlineByName = new ConcurrentHashMap<>();
    private final Map<UUID, String> onlineNames = new ConcurrentHashMap<>();

    private volatile RuntimeConfig runtime;
    private volatile Pattern playerPattern;

    private GroupRegistry groups;
    private PreferenceStore store;
    private PreferenceService preferences;
    private CooldownService cooldowns;
    private NotificationService notifications;
    private SettingsMenu menu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("groups.yml");

        loadRuntimeFiles();

        store = new PreferenceStore(new File(getDataFolder(), "data.db"));
        preferences = new PreferenceService(store);
        cooldowns = new CooldownService();
        notifications = new NotificationService(this);
        menu = new SettingsMenu(this);

        MentionCommand command = new MentionCommand(this);
        Objects.requireNonNull(getCommand("mentionme")).setExecutor(command);
        Objects.requireNonNull(getCommand("mentionme")).setTabCompleter(command);
        Objects.requireNonNull(getCommand("mentions")).setExecutor(command);
        Objects.requireNonNull(getCommand("mentions")).setTabCompleter(command);
        Objects.requireNonNull(getCommand("msettings")).setExecutor(command);

        getServer().getPluginManager().registerEvents(new MentionListener(this), this);
        getServer().getPluginManager().registerEvents(new SettingsListener(this), this);

        for (Player player : getServer().getOnlinePlayers()) {
            track(player);
        }

        store.start()
                .thenCompose(ignored -> LegacyPreferencesMigrator.migrate(
                        new File(getDataFolder(), "players.yml"),
                        store
                ))
                .thenRun(() -> sync(() -> {
                    for (Player player : getServer().getOnlinePlayers()) {
                        loadPreferences(player);
                    }
                }))
                .exceptionally(error -> {
                    getLogger().severe("Storage startup failed: " + rootMessage(error));
                    sync(() -> getServer().getPluginManager().disablePlugin(this));
                    return null;
                });

        if (getConfig().getBoolean("startup.enabled", true)) {
            getLogger().info("----------------------------------------");
            getLogger().info(" MentionMe v" + getDescription().getVersion());
            getLogger().info(" Mentions and groups ready");
            getLogger().info("----------------------------------------");
        }
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.shutdown();
        }

        if (preferences != null) {
            preferences.clearCache();
        }

        if (cooldowns != null) {
            cooldowns.clearAll();
        }

        onlineByName.clear();
        onlineNames.clear();
    }

    public void reloadRuntime() {
        reloadConfig();
        loadRuntimeFiles();
    }

    private void loadRuntimeFiles() {
        runtime = RuntimeConfig.from(getConfig());

        if (groups == null) {
            groups = new GroupRegistry(new File(getDataFolder(), "groups.yml"));
        }

        groups.load();

        playerPattern = Pattern.compile(
                Pattern.quote(runtime.symbol()) + "([A-Za-z0-9_]{3,16}|everyone|here)",
                Pattern.CASE_INSENSITIVE
        );
    }

    private void saveResourceIfMissing(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
    }

    public void track(Player player) {
        String lower = player.getName().toLowerCase(Locale.ROOT);
        onlineByName.put(lower, player.getUniqueId());
        onlineNames.put(player.getUniqueId(), player.getName());
    }

    public void untrack(Player player) {
        onlineByName.remove(player.getName().toLowerCase(Locale.ROOT));
        onlineNames.remove(player.getUniqueId());
    }

    public UUID onlineUuid(String name) {
        return onlineByName.get(name.toLowerCase(Locale.ROOT));
    }

    public String onlineName(UUID uuid) {
        return onlineNames.get(uuid);
    }

    public void loadPreferences(Player player) {
        preferences.load(player.getUniqueId()).exceptionally(error -> {
            getLogger().warning("Could not load preferences for " + player.getUniqueId() + ": " + rootMessage(error));
            return null;
        });
    }

    public void sendLines(CommandSender sender, String path) {
        sendLines(sender, path, Map.of());
    }

    public void sendLines(CommandSender sender, String path, Map<String, String> placeholders) {
        List<String> lines = getConfig().getStringList(path);

        if (lines.isEmpty() && getConfig().isString(path)) {
            lines = List.of(Objects.requireNonNull(getConfig().getString(path)));
        }

        String prefix = getConfig().getString("messages.prefix", "MentionMe ▶");

        for (String line : lines) {
            Map<String, String> merged = new HashMap<>(placeholders);
            String raw = line.replace("%prefix%", prefix);
            sender.sendMessage(Text.component(raw, merged));
        }
    }

    public void sync(Runnable runnable) {
        if (!isEnabled()) return;

        if (org.bukkit.Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            getServer().getScheduler().runTask(this, runnable);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public RuntimeConfig runtime() {
        return runtime;
    }

    public Pattern playerPattern() {
        return playerPattern;
    }

    public GroupRegistry groups() {
        return groups;
    }

    public PreferenceService preferences() {
        return preferences;
    }

    public CooldownService cooldowns() {
        return cooldowns;
    }

    public NotificationService notifications() {
        return notifications;
    }

    public SettingsMenu menu() {
        return menu;
    }
}
