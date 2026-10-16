package com.lewisgibson.bluemapsignposts.bukkit.command;

import com.lewisgibson.bluemapsignposts.bukkit.BlueMapSignPostsPlugin;
import com.lewisgibson.bluemapsignposts.bukkit.PluginRuntime;
import com.lewisgibson.bluemapsignposts.bukkit.limit.EffectiveLimits;
import com.lewisgibson.bluemapsignposts.core.access.EffectiveLimit;
import com.lewisgibson.bluemapsignposts.core.access.LimitOverride;
import com.lewisgibson.bluemapsignposts.core.settings.BlueMapSignPostsSettings;
import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostKey;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostRegistry;
import com.lewisgibson.bluemapsignposts.core.time.PlaytimeDuration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * Handles {@code /bluemapsignposts} (alias {@code /signposts}).
 *
 * <p>Everyone: {@code list} (their own signposts and their effective limit). Admins ({@code
 * bluemapsignposts.admin}): {@code list all}, {@code list <player>}, {@code list world <world>},
 * {@code remove <world> <x> <y> <z>}, {@code limit <player>} (the effective limit and which rule
 * decided it), {@code limit set <player> <n|unlimited>}, {@code limit clear <player>}, {@code
 * reload} and {@code republish}.
 *
 * <p>A player is named, and resolved in this order without a profile fetch: an online player, the
 * server's cached offline player (anyone who has joined), then the name a signpost was placed
 * under. So every subcommand works from the console for a player who is offline.
 */
public final class BlueMapSignPostsCommand implements TabExecutor {

    private static final String ADMIN_PERMISSION = "bluemapsignposts.admin";
    private static final List<String> LIMIT_SUGGESTIONS =
            List.of("unlimited", "5", "10", "25", "50");
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final BlueMapSignPostsPlugin plugin;
    private final Clock clock;

    /**
     * @param plugin the plugin whose runtime and service the command reads on every invocation
     */
    public BlueMapSignPostsCommand(BlueMapSignPostsPlugin plugin) {
        this(plugin, Clock.systemUTC());
    }

    BlueMapSignPostsCommand(BlueMapSignPostsPlugin plugin, Clock clock) {
        this.plugin = plugin;
        this.clock = clock;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            handleList(sender, args);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> handleList(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "limit" -> handleLimit(sender, args);
            case "reload" -> handleReload(sender);
            case "republish" -> handleRepublish(sender);
            default ->
                    sender.sendMessage(
                            error(
                                    "Unknown subcommand. Use /signposts"
                                            + " <list|remove|limit|reload|republish>."));
        }
        return true;
    }

    private void handleList(CommandSender sender, String[] args) {
        SignpostRegistry registry = plugin.service().registry();
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        String scope = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";

        if (scope.isEmpty()) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(error("Usage: /signposts list <all|world <world>|player>"));
                return;
            }
            EffectiveLimit limit = EffectiveLimits.of(player, plugin.runtime());
            List<Signpost> own = registry.ownedBy(player.getUniqueId());
            sender.sendMessage(header());
            sender.sendMessage(detail("You have", own.size() + " of " + limit.describe()));
            long required = plugin.runtime().policy().requiredPlaytimeTicks();
            if (required > 0) {
                sender.sendMessage(detail("Required playtime", PlaytimeDuration.format(required)));
            }
            listSignposts(sender, own);
            return;
        }
        if (!admin) {
            sender.sendMessage(noPermission());
            return;
        }
        if (scope.equals("all")) {
            sender.sendMessage(header());
            sender.sendMessage(detail("Signposts on the map", Integer.toString(registry.size())));
            listSignposts(sender, registry.all());
            return;
        }
        if (scope.equals("world")) {
            if (args.length < 3) {
                sender.sendMessage(error("Usage: /signposts list world <world>"));
                return;
            }
            List<Signpost> inWorld = registry.inWorld(args[2]);
            sender.sendMessage(header());
            sender.sendMessage(detail("Signposts in " + args[2], Integer.toString(inWorld.size())));
            listSignposts(sender, inWorld);
            return;
        }
        Optional<Target> target = resolve(args[1]);
        if (target.isEmpty()) {
            sender.sendMessage(unknownPlayer(args[1]));
            return;
        }
        List<Signpost> owned = registry.ownedBy(target.get().id());
        sender.sendMessage(header());
        sender.sendMessage(
                detail(
                        target.get().name() + " has",
                        owned.size() + " of " + limitOf(target.get()).describe()));
        listSignposts(sender, owned);
    }

    private void listSignposts(CommandSender sender, List<Signpost> signposts) {
        for (Signpost signpost : signposts) {
            sender.sendMessage(
                    detail(
                            signpost.label(),
                            describe(signpost.key()) + " by " + signpost.ownerName()));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(noPermission());
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(error("Usage: /signposts remove <world> <x> <y> <z>"));
            return;
        }
        SignpostKey key;
        try {
            key =
                    new SignpostKey(
                            args[1],
                            Integer.parseInt(args[2]),
                            Integer.parseInt(args[3]),
                            Integer.parseInt(args[4]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(error("Coordinates must be whole numbers."));
            return;
        }
        Optional<Signpost> removed = plugin.service().remove(key);
        if (removed.isEmpty()) {
            sender.sendMessage(error("There is no signpost at " + describe(key) + "."));
            return;
        }
        sender.sendMessage(
                success(
                        "Removed \""
                                + removed.get().label()
                                + "\" at "
                                + describe(key)
                                + " from the map. The block is untouched."));
    }

    private void handleLimit(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(noPermission());
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(
                    error(
                            "Usage: /signposts limit <player> | set <player> <n|unlimited> | clear <player>"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "set" -> setLimit(sender, args);
            case "clear" -> clearLimit(sender, args);
            default -> showLimit(sender, args[1]);
        }
    }

    private void showLimit(CommandSender sender, String name) {
        Optional<Target> target = resolve(name);
        if (target.isEmpty()) {
            sender.sendMessage(unknownPlayer(name));
            return;
        }
        PluginRuntime runtime = plugin.runtime();
        EffectiveLimit limit = limitOf(target.get());
        int owned = runtime.service().registry().countOwnedBy(target.get().id());
        sender.sendMessage(header());
        sender.sendMessage(
                detail(
                        target.get().name() + "'s limit",
                        limit.describe() + " (" + owned + " used)"));
        sender.sendMessage(detail("Decided by", explain(limit, target.get(), runtime)));
    }

    private String explain(EffectiveLimit limit, Target target, PluginRuntime runtime) {
        BlueMapSignPostsSettings settings = runtime.settings();
        return switch (limit.source()) {
            case OVERRIDE -> {
                LimitOverride override =
                        runtime.service().registry().limitOverride(target.id()).orElseThrow();
                yield "an admin override set by "
                        + override.actor()
                        + " on "
                        + WHEN.format(override.at())
                        + " UTC (/signposts limit clear "
                        + target.name()
                        + " removes it)";
            }
            case UNLIMITED_PERMISSION ->
                    "the permission node " + BlueMapSignPostsSettings.UNLIMITED_PERMISSION;
            case OPERATOR -> "operator status (limits.operators-unlimited is on)";
            case TIER_PERMISSION ->
                    "the permission tier "
                            + BlueMapSignPostsSettings.LIMIT_TIER_PREFIX
                            + limit.limit()
                            + " (the highest held)";
            case DEFAULT ->
                    target.online()
                            ? "the config default limits.default = " + settings.defaultLimit()
                            : "the config default limits.default = "
                                    + settings.defaultLimit()
                                    + " (offline: permission tiers are only readable while they are"
                                    + " online, which is also the only time a limit is enforced)";
        };
    }

    private void setLimit(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(error("Usage: /signposts limit set <player> <n|unlimited>"));
            return;
        }
        Optional<Target> target = resolve(args[2]);
        if (target.isEmpty()) {
            sender.sendMessage(unknownPlayer(args[2]));
            return;
        }
        int limit;
        if (args[3].equalsIgnoreCase("unlimited")) {
            limit = 0;
        } else {
            try {
                limit = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(error("The limit must be a whole number, or 'unlimited'."));
                return;
            }
            if (limit < 1) {
                sender.sendMessage(error("The limit must be at least 1, or 'unlimited'."));
                return;
            }
        }
        LimitOverride override =
                new LimitOverride(
                        target.get().id(),
                        target.get().name(),
                        limit,
                        sender instanceof Player player ? player.getName() : "console",
                        Instant.now(clock));
        plugin.service().setLimitOverride(override);
        sender.sendMessage(
                success(
                        target.get().name()
                                + "'s limit is now "
                                + limitText(limit)
                                + " (an override, kept in the store; clear it to go back to"
                                + " tiers and the default)."));
    }

    private void clearLimit(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(error("Usage: /signposts limit clear <player>"));
            return;
        }
        Optional<Target> target = resolve(args[2]);
        if (target.isEmpty()) {
            sender.sendMessage(unknownPlayer(args[2]));
            return;
        }
        Optional<LimitOverride> removed = plugin.service().clearLimitOverride(target.get().id());
        if (removed.isEmpty()) {
            sender.sendMessage(error(target.get().name() + " has no limit override."));
            return;
        }
        sender.sendMessage(
                success(
                        target.get().name()
                                + "'s override ("
                                + limitText(removed.get().limit())
                                + ") is cleared; tiers and the default decide again."));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(noPermission());
            return;
        }
        try {
            plugin.reloadSettings();
            int published = plugin.service().publishAll();
            sender.sendMessage(
                    success(
                            "Configuration reloaded and "
                                    + published
                                    + " signpost(s) re-published to the map."));
        } catch (RuntimeException ex) {
            sender.sendMessage(error("Reload failed: " + ex.getMessage()));
        }
    }

    private void handleRepublish(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(noPermission());
            return;
        }
        int published = plugin.service().publishAll();
        sender.sendMessage(success("Re-published " + published + " signpost(s) to the map."));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        if (args.length == 1) {
            return filter(
                    args[0],
                    admin
                            ? List.of("list", "remove", "limit", "reload", "republish")
                            : List.of("list"));
        }
        if (!admin) {
            return List.of();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "list" -> filter(args[1], concat(List.of("all", "world"), knownNames()));
                case "remove" -> filter(args[1], worldNames());
                case "limit" -> filter(args[1], concat(List.of("set", "clear"), knownNames()));
                default -> List.of();
            };
        }
        if (args.length == 3) {
            if (sub.equals("list") && args[1].equalsIgnoreCase("world")) {
                return filter(args[2], worldNames());
            }
            if (sub.equals("limit")
                    && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("clear"))) {
                return filter(args[2], knownNames());
            }
            return List.of();
        }
        if (args.length == 4 && sub.equals("limit") && args[1].equalsIgnoreCase("set")) {
            return filter(args[3], LIMIT_SUGGESTIONS);
        }
        return List.of();
    }

    /** A player named on the command line, resolved without a profile fetch. */
    private record Target(UUID id, String name, boolean online, Player player) {}

    private Optional<Target> resolve(String name) {
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) {
            return Optional.of(new Target(online.getUniqueId(), online.getName(), true, online));
        }
        OfflinePlayer cached = plugin.getServer().getOfflinePlayerIfCached(name);
        if (cached != null) {
            // Cached means the server knows the name; fall back to what was typed regardless.
            String known = Objects.requireNonNullElse(cached.getName(), name);
            return Optional.of(new Target(cached.getUniqueId(), known, false, null));
        }
        for (Signpost signpost : plugin.service().registry().all()) {
            if (signpost.ownerName().equalsIgnoreCase(name)) {
                return Optional.of(new Target(signpost.owner(), signpost.ownerName(), false, null));
            }
        }
        for (LimitOverride override : plugin.service().registry().limitOverrides()) {
            if (override.playerName().equalsIgnoreCase(name)) {
                return Optional.of(
                        new Target(override.player(), override.playerName(), false, null));
            }
        }
        return Optional.empty();
    }

    private EffectiveLimit limitOf(Target target) {
        if (target.online()) {
            return EffectiveLimits.of(target.player(), plugin.runtime());
        }
        return EffectiveLimits.ofOffline(
                plugin.getServer().getOfflinePlayer(target.id()), plugin.runtime());
    }

    private List<String> knownNames() {
        List<String> names = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            names.add(player.getName());
        }
        for (Signpost signpost : plugin.service().registry().all()) {
            if (!names.contains(signpost.ownerName())) {
                names.add(signpost.ownerName());
            }
        }
        for (LimitOverride override : plugin.service().registry().limitOverrides()) {
            if (!names.contains(override.playerName())) {
                names.add(override.playerName());
            }
        }
        return names;
    }

    private List<String> worldNames() {
        List<String> names = new ArrayList<>();
        for (World world : plugin.getServer().getWorlds()) {
            names.add(world.getName());
        }
        return names;
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static List<String> filter(String prefix, List<String> options) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }

    private static String limitText(int limit) {
        return limit == 0 ? "unlimited" : Integer.toString(limit);
    }

    private static String describe(SignpostKey key) {
        return key.world() + " " + key.x() + " " + key.y() + " " + key.z();
    }

    private static Component header() {
        return Component.text("BlueMapSignPosts", NamedTextColor.GREEN);
    }

    private static Component detail(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private static Component success(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    private static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    private static Component noPermission() {
        return error("You do not have permission to do that.");
    }

    private static Component unknownPlayer(String name) {
        return error(
                "No player called "
                        + name
                        + " is known here (they must be online, have joined before, or have a"
                        + " signpost on the map).");
    }
}
