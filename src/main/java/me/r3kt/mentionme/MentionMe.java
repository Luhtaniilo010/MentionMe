package me.r3kt.mentionme;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MentionMe extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<String, UUID> online = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> everyoneCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> hereCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Prefs> prefs = new ConcurrentHashMap<>();
    private File prefsFile;
    private YamlConfiguration prefsYaml;

    @Override public void onEnable() {
        saveDefaultConfig();
        loadPrefs();
        Bukkit.getOnlinePlayers().forEach(p -> online.put(p.getName().toLowerCase(Locale.ROOT), p.getUniqueId()));
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("mentionme")).setExecutor(this);
        Objects.requireNonNull(getCommand("mentionme")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("mentions")).setExecutor(this);
        Objects.requireNonNull(getCommand("mentions")).setTabCompleter(this);
        if (getConfig().getBoolean("startup.enabled", true)) {
            String version = getDescription().getVersion();
            getLogger().info("----------------------------------------");
            getLogger().info(" MentionMe v" + version);
            getLogger().info(" Player mentions ready | Lightweight mode");
            getLogger().info("----------------------------------------");
        }
    }

    @Override public void onDisable() { savePrefs(); }

    @EventHandler public void onJoin(PlayerJoinEvent e) { online.put(e.getPlayer().getName().toLowerCase(Locale.ROOT), e.getPlayer().getUniqueId()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { online.remove(e.getPlayer().getName().toLowerCase(Locale.ROOT)); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!getConfig().getBoolean("mentions.enabled", true)) return;
        Player sender = event.getPlayer();
        if (!sender.hasPermission("mentionme.use")) return;

        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message());
        String symbol = getConfig().getString("mentions.symbol", "@");
        if (symbol == null || symbol.isEmpty()) symbol = "@";
        Pattern tokenPattern = Pattern.compile(Pattern.quote(symbol) + "([A-Za-z0-9_]{3,16}|everyone|here)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = tokenPattern.matcher(plain);
        LinkedHashMap<String, UUID> targets = new LinkedHashMap<>();
        LinkedHashSet<String> offlineNames = new LinkedHashSet<>();
        boolean everyone = false, here = false;

        while (matcher.find()) {
            String name = matcher.group(1);
            if (name.equalsIgnoreCase("everyone")) { everyone = true; continue; }
            if (name.equalsIgnoreCase("here")) { here = true; continue; }
            UUID id = online.get(name.toLowerCase(Locale.ROOT));
            if (id == null) offlineNames.add(name);
            else targets.put(name.toLowerCase(Locale.ROOT), id);
        }
        if (targets.isEmpty() && offlineNames.isEmpty() && !everyone && !here) return;

        // Special mentions are permission based. Give clear feedback instead of silently ignoring them.
        if (everyone && getConfig().getBoolean("everyone.enabled", true) && !sender.hasPermission("mentionme.everyone")) {
            schedule(() -> sendLines(sender, "messages.no-permission-everyone", Map.of()));
        }
        if (here && getConfig().getBoolean("here.enabled", true) && !sender.hasPermission("mentionme.here")) {
            schedule(() -> sendLines(sender, "messages.no-permission-here", Map.of()));
        }

        if (!sender.hasPermission("mentionme.bypass.limit")) {
            int max = getConfig().getInt("mentions.max-per-message", 5);
            if (targets.size() > max) { schedule(() -> sendLines(sender, "messages.too-many", Map.of("limit", String.valueOf(max)))); return; }
        }
        long now = System.currentTimeMillis();
        if (!sender.hasPermission("mentionme.bypass.cooldown") && (!targets.isEmpty() || !offlineNames.isEmpty())) {
            long wait = remaining(cooldowns, sender.getUniqueId(), getConfig().getLong("mentions.cooldown-seconds", 3), now);
            if (wait > 0) { schedule(() -> sendLines(sender, "messages.cooldown", Map.of("seconds", String.valueOf(wait)))); return; }
            cooldowns.put(sender.getUniqueId(), now);
        }

        Component changed = event.message();
        for (Map.Entry<String, UUID> entry : targets.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getValue());
            String display = p != null ? p.getName() : entry.getKey();
            if (entry.getValue().equals(sender.getUniqueId()) && !getConfig().getBoolean("mentions.allow-self-mention", true)) {
                schedule(() -> sendLines(sender, "messages.self-disabled", Map.of()));
                continue;
            }
            changed = replaceIgnoreCase(changed, symbol + display, format(getConfig().getString("mentions.highlight-format"), Map.of("player", display)));
        }
        if (everyone && sender.hasPermission("mentionme.everyone") && getConfig().getBoolean("everyone.enabled", true))
            changed = replaceIgnoreCase(changed, symbol + "everyone", format(getConfig().getString("everyone.highlight-format"), Map.of()));
        if (here && sender.hasPermission("mentionme.here") && getConfig().getBoolean("here.enabled", true))
            changed = replaceIgnoreCase(changed, symbol + "here", format(getConfig().getString("here.highlight-format"), Map.of()));
        event.message(changed);

        final boolean doEveryone = everyone, doHere = here;
        final Set<UUID> direct = new LinkedHashSet<>(targets.values());
        schedule(() -> {
            for (String off : offlineNames) sender.sendActionBar(format(getConfig().getString("offline-player.actionbar"), Map.of("player", off)));
            Set<UUID> notify = new LinkedHashSet<>(direct);
            if (doEveryone && sender.hasPermission("mentionme.everyone") && getConfig().getBoolean("everyone.enabled", true)) {
                if (cooldownAllowed(everyoneCooldowns, sender, "everyone.cooldown-seconds")) Bukkit.getOnlinePlayers().forEach(p -> notify.add(p.getUniqueId()));
            }
            if (doHere && sender.hasPermission("mentionme.here") && getConfig().getBoolean("here.enabled", true)) {
                if (cooldownAllowed(hereCooldowns, sender, "here.cooldown-seconds")) {
                    double radius = getConfig().getDouble("here.radius", 50.0), r2 = radius * radius;
                    for (Player p : sender.getWorld().getPlayers()) if (p.getLocation().distanceSquared(sender.getLocation()) <= r2) notify.add(p.getUniqueId());
                }
            }
            for (UUID id : notify) {
                Player target = Bukkit.getPlayer(id);
                if (target == null) continue;
                if (id.equals(sender.getUniqueId()) && !getConfig().getBoolean("mentions.allow-self-mention", true)) continue;
                Prefs pr = prefs.computeIfAbsent(id, x -> new Prefs(true, true));
                if (pr.enabled) notifyPlayer(target, sender, pr);
            }
        });
    }

    private boolean cooldownAllowed(Map<UUID,Long> map, Player sender, String path) {
        if (sender.hasPermission("mentionme.bypass.cooldown")) return true;
        long now = System.currentTimeMillis(), wait = remaining(map, sender.getUniqueId(), getConfig().getLong(path, 15), now);
        if (wait > 0) { sendLines(sender, "messages.cooldown", Map.of("seconds", String.valueOf(wait))); return false; }
        map.put(sender.getUniqueId(), now); return true;
    }
    private long remaining(Map<UUID,Long> map, UUID id, long seconds, long now) {
        long left = map.getOrDefault(id, 0L) + seconds * 1000L - now;
        return left <= 0 ? 0 : (left + 999) / 1000;
    }
    private void notifyPlayer(Player target, Player sender, Prefs pr) {
        Map<String,String> ph = Map.of("sender", sender.getName(), "player", target.getName());
        if (getConfig().getBoolean("notifications.actionbar.enabled", true)) target.sendActionBar(format(getConfig().getString("notifications.actionbar.message"), ph));
        if (pr.sound && getConfig().getBoolean("notifications.sound.enabled", true)) {
            try { target.playSound(target.getLocation(), Sound.valueOf(getConfig().getString("notifications.sound.name", "BLOCK_NOTE_BLOCK_PLING").toUpperCase(Locale.ROOT)), (float)getConfig().getDouble("notifications.sound.volume",1), (float)getConfig().getDouble("notifications.sound.pitch",1.2)); }
            catch (IllegalArgumentException ignored) { }
        }
        if (getConfig().getBoolean("notifications.title.enabled", false)) {
            Title.Times times = Title.Times.times(Duration.ofMillis(getConfig().getLong("notifications.title.fade-in",5)*50), Duration.ofMillis(getConfig().getLong("notifications.title.stay",40)*50), Duration.ofMillis(getConfig().getLong("notifications.title.fade-out",10)*50));
            target.showTitle(Title.title(format(getConfig().getString("notifications.title.title"), ph), format(getConfig().getString("notifications.title.subtitle"), ph), times));
        }
    }
    private Component replaceIgnoreCase(Component source, String literal, Component replacement) {
        return source.replaceText(TextReplacementConfig.builder().match(Pattern.compile(Pattern.quote(literal), Pattern.CASE_INSENSITIVE)).replacement(replacement).build());
    }
    private Component format(String raw, Map<String,String> placeholders) {
        if (raw == null) return Component.empty();
        String s = raw;
        for (var e : placeholders.entrySet()) s = s.replace("%"+e.getKey()+"%", escapeMini(e.getValue()));
        return mm.deserialize(s);
    }
    private String escapeMini(String s) { return s.replace("<", "\\<"); }
    private void schedule(Runnable r) { Bukkit.getScheduler().runTask(this, r); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("mentionme")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("mentionme.reload")) { sendLines(sender,"messages.no-permission",Map.of()); return true; }
                reloadConfig(); sendLines(sender,"messages.reload",Map.of()); return true;
            }
            sendLines(sender,"messages.help",Map.of());
            if (sender.hasPermission("mentionme.admin")) sendLines(sender,"messages.admin-help",Map.of());
            return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("This command is player-only."); return true; }
        Prefs pr = prefs.computeIfAbsent(p.getUniqueId(), x -> new Prefs(true,true));
        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            pr.enabled = !pr.enabled; savePrefs(); sendLines(p, pr.enabled ? "messages.mentions-enabled":"messages.mentions-disabled", Map.of()); return true;
        }
        if (args[0].equalsIgnoreCase("sound")) {
            pr.sound = !pr.sound; savePrefs(); sendLines(p, pr.sound ? "messages.sound-enabled":"messages.sound-disabled", Map.of()); return true;
        }
        sendLines(p,"messages.help",Map.of()); return true;
    }

    private void sendLines(CommandSender sender, String path, Map<String,String> ph) {
        List<String> lines = getConfig().getStringList(path);
        if (lines.isEmpty() && getConfig().isString(path)) lines = List.of(Objects.requireNonNull(getConfig().getString(path)));
        String prefix = getConfig().getString("messages.prefix", "MentionMe ▶");
        for (String line : lines) {
            String raw = line.replace("%prefix%", prefix);
            if (sender instanceof Player p) p.sendMessage(format(raw, ph)); else sender.sendMessage(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(format(raw, ph)));
        }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> values = command.getName().equalsIgnoreCase("mentionme") ? (sender.hasPermission("mentionme.reload") ? List.of("help","reload") : List.of("help")) : List.of("toggle","sound");
        String q=args[0].toLowerCase(Locale.ROOT); return values.stream().filter(v->v.startsWith(q)).toList();
    }

    private void loadPrefs() {
        prefsFile = new File(getDataFolder(), "players.yml"); prefsYaml = YamlConfiguration.loadConfiguration(prefsFile);
        for (String key : prefsYaml.getKeys(false)) try { UUID id=UUID.fromString(key); prefs.put(id,new Prefs(prefsYaml.getBoolean(key+".enabled",true),prefsYaml.getBoolean(key+".sound",true))); } catch (IllegalArgumentException ignored) {}
    }
    private synchronized void savePrefs() {
        if (prefsYaml == null) return;
        for (var e:prefs.entrySet()) { String k=e.getKey().toString(); prefsYaml.set(k+".enabled",e.getValue().enabled); prefsYaml.set(k+".sound",e.getValue().sound); }
        try { prefsYaml.save(prefsFile); } catch (IOException e) { getLogger().warning("Could not save players.yml: "+e.getMessage()); }
    }
    private static final class Prefs { boolean enabled, sound; Prefs(boolean enabled, boolean sound){this.enabled=enabled;this.sound=sound;} }
}
