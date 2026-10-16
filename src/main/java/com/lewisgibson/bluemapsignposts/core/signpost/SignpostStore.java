package com.lewisgibson.bluemapsignposts.core.signpost;

import com.lewisgibson.bluemapsignposts.core.access.LimitOverride;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Durable storage for the two things the plugin has to remember across a restart: the signposts
 * (BlueMap keeps no API markers of its own) and the per-player limit overrides an admin set by hand
 * (which belong beside the markers, not in config.yml, so a reload cannot lose them).
 *
 * <p>Implementations are opened once, read in full at startup into a {@link SignpostRegistry},
 * written through as things change, and closed on shutdown. Every method throws {@link
 * StorageException} on failure. Nothing here is on the hot path: block events read the in-memory
 * registry only, and writes are handed to the plugin's storage thread.
 */
public interface SignpostStore {

    /** Opens the store and creates or upgrades its tables. */
    void open();

    /**
     * @return where the store is, for the log (never a password)
     */
    String description();

    /**
     * @return every stored signpost, in no particular order
     */
    List<Signpost> loadSignposts();

    /**
     * @return every stored limit override, keyed by player
     */
    Map<UUID, LimitOverride> loadLimitOverrides();

    /** Inserts or replaces the signpost at {@code signpost.key()}. */
    void putSignpost(Signpost signpost);

    /** Removes the signpost at {@code key}, if any. */
    void removeSignpost(SignpostKey key);

    /** Inserts or replaces the override for {@code override.player()}. */
    void putLimitOverride(LimitOverride override);

    /** Removes the override for {@code player}, if any. */
    void removeLimitOverride(UUID player);

    /** Closes the store for good. Idempotent. */
    void close();
}
