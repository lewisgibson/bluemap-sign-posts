package com.lewisgibson.bluemapsignposts.core.signpost;

import com.lewisgibson.bluemapsignposts.core.access.LimitOverride;
import com.lewisgibson.bluemapsignposts.core.map.MarkerSink;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Adds and removes signposts and limit overrides, keeping the in-memory registry, the store and the
 * map in step.
 *
 * <p>The registry is the truth and is updated first, so the next event and the next command see the
 * change the moment a call returns; the map follows at once; the store write is handed to {@code
 * writer} (the plugin's storage thread) so no event ever waits on a database. A write that fails is
 * reported by the writer's own failure handler, and the registry and the map stay correct.
 *
 * <p>The registry update, the queued store write and the map update are not one atomic step: a
 * reader can see the registry change before the map does, and a crash between the registry update
 * and the write running loses that change.
 */
public final class SignpostService {

    private final SignpostRegistry registry;
    private final SignpostStore store;
    private final Consumer<Runnable> writer;
    private final MarkerSink sink;

    /**
     * @param registry the in-memory copy, already loaded from {@code store}
     * @param store the durable store behind it
     * @param writer runs a store write off the calling thread
     * @param sink where markers are shown
     */
    public SignpostService(
            SignpostRegistry registry,
            SignpostStore store,
            Consumer<Runnable> writer,
            MarkerSink sink) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.store = Objects.requireNonNull(store, "store");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /**
     * @return the registry, for read-only queries
     */
    public SignpostRegistry registry() {
        return registry;
    }

    /**
     * Adds or replaces a signpost and shows it on the map.
     *
     * @param signpost the signpost
     * @return the signpost previously at that block, if any
     */
    public Optional<Signpost> place(Signpost signpost) {
        Optional<Signpost> previous = registry.put(signpost);
        writer.accept(() -> store.putSignpost(signpost));
        sink.publish(signpost);
        return previous;
    }

    /**
     * Removes the signpost at a block, if there is one, and takes it off the map.
     *
     * @param key the block position
     * @return the removed signpost, if any
     */
    public Optional<Signpost> remove(SignpostKey key) {
        Optional<Signpost> removed = registry.remove(key);
        if (removed.isPresent()) {
            writer.accept(() -> store.removeSignpost(key));
            sink.retract(key);
        }
        return removed;
    }

    /**
     * Sets a player's limit override.
     *
     * @param override the override
     * @return the override it replaced, if any
     */
    public Optional<LimitOverride> setLimitOverride(LimitOverride override) {
        Optional<LimitOverride> previous = registry.putLimitOverride(override);
        writer.accept(() -> store.putLimitOverride(override));
        return previous;
    }

    /**
     * Clears a player's limit override so tiers and the default decide again.
     *
     * @param player the player's unique id
     * @return the override removed, if there was one
     */
    public Optional<LimitOverride> clearLimitOverride(UUID player) {
        Optional<LimitOverride> removed = registry.removeLimitOverride(player);
        if (removed.isPresent()) {
            writer.accept(() -> store.removeLimitOverride(player));
        }
        return removed;
    }

    /**
     * Takes every signpost this service holds off the map, leaving the registry and the store as
     * they are. Used when a reload swaps this service for one over a different store, so markers
     * only the old store held do not stay on the map with nothing behind them.
     */
    public void retractAll() {
        for (Signpost signpost : registry.all()) {
            sink.retract(signpost.key());
        }
    }

    /**
     * Shows every stored signpost in one world. Called when a world loads after the server started
     * (a world a multiworld plugin loads on demand, say): until then the map had no world to put
     * them on.
     *
     * @param world the world's name
     * @return how many were published
     */
    public int publishWorld(String world) {
        List<Signpost> inWorld = registry.inWorld(world);
        for (Signpost signpost : inWorld) {
            sink.publish(signpost);
        }
        return inWorld.size();
    }

    /**
     * Shows every stored signpost. Called whenever BlueMap enables, since it starts with no markers
     * each time, and by the re-publish command.
     *
     * @return how many were published
     */
    public int publishAll() {
        List<Signpost> all = registry.all();
        for (Signpost signpost : all) {
            sink.publish(signpost);
        }
        return all.size();
    }
}
