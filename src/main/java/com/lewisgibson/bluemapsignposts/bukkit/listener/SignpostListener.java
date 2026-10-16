package com.lewisgibson.bluemapsignposts.bukkit.listener;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import com.lewisgibson.bluemapsignposts.bukkit.BlueMapSignPostsPlugin;
import com.lewisgibson.bluemapsignposts.bukkit.PluginRuntime;
import com.lewisgibson.bluemapsignposts.bukkit.block.SignpostReader;
import com.lewisgibson.bluemapsignposts.bukkit.limit.EffectiveLimits;
import com.lewisgibson.bluemapsignposts.bukkit.playtime.Playtime;
import com.lewisgibson.bluemapsignposts.core.access.AccessResult;
import com.lewisgibson.bluemapsignposts.core.access.DecisionReason;
import com.lewisgibson.bluemapsignposts.core.access.EffectiveLimit;
import com.lewisgibson.bluemapsignposts.core.message.Messages;
import com.lewisgibson.bluemapsignposts.core.settings.BlueMapSignPostsSettings;
import com.lewisgibson.bluemapsignposts.core.sign.ParseOutcome;
import com.lewisgibson.bluemapsignposts.core.sign.ParseResult;
import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostKey;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostSource;
import com.lewisgibson.bluemapsignposts.core.time.PlaytimeDuration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Turns sign edits and named-banner placements into signposts, and takes signposts down when their
 * block goes.
 *
 * <p>Everything here runs at {@code MONITOR} priority and never cancels: the sign is written and
 * the banner is placed exactly as vanilla would, whether or not a marker results. The plugin
 * observes the game; it does not change it. The marker always reflects what the block says: an edit
 * that drops the trigger, or that a player is not allowed to make into a marker, removes any marker
 * already at that block rather than leaving a label the sign no longer carries.
 */
public final class SignpostListener implements Listener {

    private static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";

    private final BlueMapSignPostsPlugin plugin;
    private final boolean folia;

    /**
     * @param plugin the plugin whose runtime, service and messenger the handlers use
     */
    public SignpostListener(BlueMapSignPostsPlugin plugin) {
        this(plugin, classExists(FOLIA_MARKER));
    }

    /** For tests: forces the Folia or the plain Paper path regardless of the running server. */
    SignpostListener(BlueMapSignPostsPlugin plugin, boolean folia) {
        this.plugin = plugin;
        this.folia = folia;
    }

    /** Whether a class is on the classpath; Folia is detected by its region server class. */
    static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    /**
     * A written sign either becomes, updates or stops being a signpost, depending on its trigger
     * line.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        BlockState state = event.getBlock().getState();
        if (!(state instanceof Sign sign)) {
            return;
        }
        PluginRuntime runtime = plugin.runtime();
        ParseResult result =
                SignpostReader.readAfterEdit(
                        sign, event.getSide(), event.lines(), runtime.parser());
        Block block = event.getBlock();
        SignpostKey key = keyOf(block);
        Player player = event.getPlayer();
        if (result.outcome() == ParseOutcome.EMPTY_LABEL) {
            plugin.service().remove(key);
            send(
                    player,
                    runtime.settings().messages().emptyLabel(),
                    Map.of("trigger", runtime.settings().trigger()));
            return;
        }
        if (!result.isSignpost()) {
            plugin.service().remove(key);
            return;
        }
        request(player, key, Signpost.clip(result.label()), SignpostSource.SIGN, runtime);
    }

    /** A banner placed with a custom name becomes a signpost when banners are enabled. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        PluginRuntime runtime = plugin.runtime();
        // canBuild() false (vanilla spawn protection, for one) undoes the placement without
        // cancelling the event, so the banner never lands and must not become a marker.
        if (!event.canBuild() || !runtime.settings().bannersEnabled()) {
            return;
        }
        Block block = event.getBlockPlaced();
        if (!Tag.BANNERS.isTagged(block.getType())) {
            return;
        }
        ItemMeta meta = event.getItemInHand().getItemMeta();
        Component name = meta == null ? null : meta.customName();
        Optional<SignpostReader.Reading> reading = SignpostReader.bannerReading(name);
        if (reading.isEmpty()) {
            return;
        }
        request(
                event.getPlayer(),
                keyOf(block),
                reading.get().result().label(),
                SignpostSource.BANNER,
                runtime);
    }

    /** Breaking a signpost block takes it off the map and tells the player who broke it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (plugin.service().registry().isEmptyIn(block.getWorld().getName())) {
            return;
        }
        Optional<Signpost> removed = plugin.service().remove(keyOf(block));
        if (removed.isPresent()) {
            send(
                    event.getPlayer(),
                    plugin.runtime().settings().messages().removed(),
                    Map.of("label", removed.get().label()));
        }
    }

    /** A sign or banner knocked off the block it hung on is destroyed by the server, not broken. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDestroy(BlockDestroyEvent event) {
        removeQuietly(event.getBlock());
    }

    /** Blocks an entity explosion destroys lose their signposts, silently. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeQuietly(event.blockList());
    }

    /** Blocks a block explosion destroys lose their signposts, silently. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeQuietly(event.blockList());
    }

    /** A piston moves its blocks away from their stored position, so their signposts go. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        removeQuietly(event.getBlocks());
    }

    /** A piston moves its blocks away from their stored position, so their signposts go. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        removeQuietly(event.getBlocks());
    }

    /**
     * Verifies the stored signposts in a chunk against the world as it loads, so a block changed
     * while the chunk was out (a world edit, a restore, an event this listener never saw) cannot
     * leave a marker with nothing under it. A chunk with no signposts costs two map lookups.
     *
     * <p>On Folia the check is handed to the region scheduler for the chunk, because nothing
     * guarantees the thread that fires the event owns the chunk it is about to read block states
     * from. On Paper it runs inline, as it always has.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        Chunk chunk = event.getChunk();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        if (plugin.service().registry().inChunk(world.getName(), chunkX, chunkZ).isEmpty()) {
            return;
        }
        if (folia) {
            plugin.getServer()
                    .getRegionScheduler()
                    .execute(
                            plugin,
                            world,
                            chunkX,
                            chunkZ,
                            () -> verifyChunk(world, chunkX, chunkZ));
        } else {
            verifyChunk(world, chunkX, chunkZ);
        }
    }

    /**
     * A world that loads after BlueMap came up (one a multiworld plugin loads later, or loads again
     * after unloading it) gets its signposts back on the map: they were skipped while there was no
     * world for BlueMap to put them on.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.service().publishWorld(event.getWorld().getName());
    }

    /** Must run on the thread that owns the chunk. Skips a chunk that has unloaded meanwhile. */
    private void verifyChunk(World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        List<Signpost> stored =
                plugin.service().registry().inChunk(world.getName(), chunkX, chunkZ);
        PluginRuntime runtime = plugin.runtime();
        for (Signpost signpost : stored) {
            SignpostKey key = signpost.key();
            Block block = chunk.getBlock(key.x() & 15, key.y(), key.z() & 15);
            if (!stillSays(block, signpost, runtime)) {
                plugin.service().remove(key);
            }
        }
    }

    private static boolean stillSays(Block block, Signpost signpost, PluginRuntime runtime) {
        Optional<SignpostReader.Reading> reading = SignpostReader.read(block, runtime.parser());
        if (reading.isEmpty()) {
            return false;
        }
        SignpostReader.Reading current = reading.get();
        return current.source() == signpost.source()
                && current.result().isSignpost()
                && Signpost.clip(current.result().label()).equals(signpost.label());
    }

    private void request(
            Player player,
            SignpostKey key,
            String label,
            SignpostSource source,
            PluginRuntime runtime) {
        BlueMapSignPostsSettings settings = runtime.settings();
        Messages messages = settings.messages();
        Optional<Signpost> existing = plugin.service().registry().get(key);
        boolean ownEdit =
                existing.isPresent() && existing.get().owner().equals(player.getUniqueId());
        if (!ownEdit) {
            long played = Playtime.ticks(player);
            EffectiveLimit limit = EffectiveLimits.of(player, runtime);
            AccessResult access =
                    runtime.policy()
                            .evaluate(
                                    player.hasPermission(settings.createPermission()),
                                    player.hasPermission(settings.bypassPermission()),
                                    played,
                                    plugin.service().registry().countOwnedBy(player.getUniqueId()),
                                    limit);
            if (!access.allowed()) {
                plugin.service().remove(key);
                deny(player, access.reason(), played, limit, runtime);
                return;
            }
        }
        long createdAt =
                ownEdit ? existing.get().createdAtEpochMillis() : System.currentTimeMillis();
        Signpost signpost =
                new Signpost(key, label, player.getUniqueId(), player.getName(), source, createdAt);
        plugin.service().place(signpost);
        send(player, ownEdit ? messages.updated() : messages.created(), Map.of("label", label));
    }

    private void deny(
            Player player,
            DecisionReason reason,
            long played,
            EffectiveLimit limit,
            PluginRuntime runtime) {
        Messages messages = runtime.settings().messages();
        switch (reason) {
            case BELOW_THRESHOLD -> {
                long required = runtime.policy().requiredPlaytimeTicks();
                send(
                        player,
                        messages.deniedPlaytime(),
                        Map.of(
                                "required", PlaytimeDuration.format(required),
                                "played", PlaytimeDuration.format(played),
                                "remaining", PlaytimeDuration.format(required - played)));
            }
            case LIMIT_REACHED ->
                    send(
                            player,
                            messages.deniedLimit(),
                            Map.of(
                                    "count",
                                    Integer.toString(
                                            plugin.service()
                                                    .registry()
                                                    .countOwnedBy(player.getUniqueId())),
                                    "limit",
                                    limit.describe()));
            default -> send(player, messages.deniedPermission(), Map.of());
        }
    }

    private void removeQuietly(List<Block> blocks) {
        if (blocks.isEmpty()
                || plugin.service().registry().isEmptyIn(blocks.get(0).getWorld().getName())) {
            return;
        }
        for (Block block : blocks) {
            plugin.service().remove(keyOf(block));
        }
    }

    private void removeQuietly(Block block) {
        if (plugin.service().registry().isEmptyIn(block.getWorld().getName())) {
            return;
        }
        plugin.service().remove(keyOf(block));
    }

    private void send(Player player, String template, Map<String, String> placeholders) {
        plugin.messenger().send(player, template, placeholders);
    }

    private static SignpostKey keyOf(Block block) {
        return new SignpostKey(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }
}
