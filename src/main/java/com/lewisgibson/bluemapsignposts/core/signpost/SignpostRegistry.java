package com.lewisgibson.bluemapsignposts.core.signpost;

import com.lewisgibson.bluemapsignposts.core.access.LimitOverride;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The in-memory set of every signpost, with the views the adapter asks for at gameplay frequency:
 * lookup by block, count and list per owner, and list per chunk.
 *
 * <p>Every method is synchronised: the adapter calls in from event threads (region threads on
 * Folia) and from BlueMap's asynchronous enable callback, and a signpost operation is rare enough
 * that a monitor is cheaper to reason about than a lock-free structure. The per-chunk index is what
 * keeps the chunk-load verification cheap: a chunk with no signposts costs one map lookup per world
 * and one per chunk, and returns a shared empty list rather than allocating.
 */
public final class SignpostRegistry {

    private final Map<SignpostKey, Signpost> byKey = new HashMap<>();
    private final Map<String, Map<Long, List<Signpost>>> byWorldChunk = new HashMap<>();
    private final Map<UUID, LimitOverride> limitOverrides = new HashMap<>();

    /**
     * Adds or replaces the signpost at its block.
     *
     * @param signpost the signpost to store
     * @return the signpost previously at that block, if any
     */
    public synchronized Optional<Signpost> put(Signpost signpost) {
        Signpost previous = byKey.put(signpost.key(), signpost);
        if (previous != null) {
            unindex(previous);
        }
        index(signpost);
        return Optional.ofNullable(previous);
    }

    /**
     * Removes the signpost at a block.
     *
     * @param key the block position
     * @return the removed signpost, if there was one
     */
    public synchronized Optional<Signpost> remove(SignpostKey key) {
        Signpost removed = byKey.remove(key);
        if (removed != null) {
            unindex(removed);
        }
        return Optional.ofNullable(removed);
    }

    /**
     * @param key the block position
     * @return the signpost at that block, if any
     */
    public synchronized Optional<Signpost> get(SignpostKey key) {
        return Optional.ofNullable(byKey.get(key));
    }

    /**
     * @param world a world name
     * @return whether no signpost at all is in that world - the cheapest possible pre-check for a
     *     per-block event
     */
    public synchronized boolean isEmptyIn(String world) {
        return !byWorldChunk.containsKey(world);
    }

    /**
     * @param owner a player's unique id
     * @return how many signposts that player owns
     */
    public synchronized int countOwnedBy(UUID owner) {
        int count = 0;
        for (Signpost signpost : byKey.values()) {
            if (signpost.owner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    /**
     * @param owner a player's unique id
     * @return a snapshot of that player's signposts, in no particular order
     */
    public synchronized List<Signpost> ownedBy(UUID owner) {
        List<Signpost> owned = new ArrayList<>();
        for (Signpost signpost : byKey.values()) {
            if (signpost.owner().equals(owner)) {
                owned.add(signpost);
            }
        }
        return owned;
    }

    /**
     * @param world a world name
     * @param chunkX the chunk X
     * @param chunkZ the chunk Z
     * @return a snapshot of the signposts in that chunk, or an empty list
     */
    public synchronized List<Signpost> inChunk(String world, int chunkX, int chunkZ) {
        Map<Long, List<Signpost>> chunks = byWorldChunk.get(world);
        if (chunks == null) {
            return List.of();
        }
        List<Signpost> inChunk = chunks.get(SignpostKey.chunkKey(chunkX, chunkZ));
        return inChunk == null ? List.of() : List.copyOf(inChunk);
    }

    /**
     * @param world a world name
     * @return a snapshot of that world's signposts, in no particular order
     */
    public synchronized List<Signpost> inWorld(String world) {
        List<Signpost> inWorld = new ArrayList<>();
        for (Signpost signpost : byKey.values()) {
            if (signpost.key().world().equals(world)) {
                inWorld.add(signpost);
            }
        }
        return inWorld;
    }

    /**
     * @return a snapshot of every signpost, in no particular order
     */
    public synchronized List<Signpost> all() {
        return new ArrayList<>(byKey.values());
    }

    /**
     * @param player a player's unique id
     * @return the admin's limit override for that player, if one is set
     */
    public synchronized Optional<LimitOverride> limitOverride(UUID player) {
        return Optional.ofNullable(limitOverrides.get(player));
    }

    /**
     * Sets or replaces a player's limit override.
     *
     * @param override the override
     * @return the override it replaced, if any
     */
    public synchronized Optional<LimitOverride> putLimitOverride(LimitOverride override) {
        return Optional.ofNullable(limitOverrides.put(override.player(), override));
    }

    /**
     * Clears a player's limit override.
     *
     * @param player the player's unique id
     * @return the override removed, if there was one
     */
    public synchronized Optional<LimitOverride> removeLimitOverride(UUID player) {
        return Optional.ofNullable(limitOverrides.remove(player));
    }

    /**
     * @return a snapshot of every limit override
     */
    public synchronized List<LimitOverride> limitOverrides() {
        return new ArrayList<>(limitOverrides.values());
    }

    /**
     * @return how many signposts are stored
     */
    public synchronized int size() {
        return byKey.size();
    }

    /**
     * Replaces the whole contents, used when loading the store at startup.
     *
     * @param signposts the new signposts; a later duplicate of the same block wins
     * @param overrides the new limit overrides
     */
    public synchronized void replaceAll(
            Collection<Signpost> signposts, Collection<LimitOverride> overrides) {
        byKey.clear();
        byWorldChunk.clear();
        limitOverrides.clear();
        for (Signpost signpost : signposts) {
            put(signpost);
        }
        for (LimitOverride override : overrides) {
            limitOverrides.put(override.player(), override);
        }
    }

    /**
     * Loads a registry from a store, reading it in full.
     *
     * @param store an open store
     * @return the loaded registry
     */
    public static SignpostRegistry load(SignpostStore store) {
        SignpostRegistry registry = new SignpostRegistry();
        registry.replaceAll(store.loadSignposts(), store.loadLimitOverrides().values());
        return registry;
    }

    private void index(Signpost signpost) {
        SignpostKey key = signpost.key();
        byWorldChunk
                .computeIfAbsent(key.world(), world -> new HashMap<>())
                .computeIfAbsent(
                        SignpostKey.chunkKey(key.chunkX(), key.chunkZ()),
                        chunk -> new ArrayList<>(1))
                .add(signpost);
    }

    private void unindex(Signpost signpost) {
        SignpostKey key = signpost.key();
        Map<Long, List<Signpost>> chunks = byWorldChunk.get(key.world());
        long chunkKey = SignpostKey.chunkKey(key.chunkX(), key.chunkZ());
        List<Signpost> inChunk = chunks.get(chunkKey);
        inChunk.remove(signpost);
        if (inChunk.isEmpty()) {
            chunks.remove(chunkKey);
        }
        if (chunks.isEmpty()) {
            byWorldChunk.remove(key.world());
        }
    }
}
