package com.lewisgibson.bluemapsignposts.core.access;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An admin's by-hand limit for one player, which beats every permission tier and the default. Kept
 * in the plugin's own store beside the signposts, never in config.yml, so a config reload cannot
 * lose it and the config file stays the defaults.
 *
 * @param player the player's unique id
 * @param playerName the player's name when the override was set, for display without a lookup
 * @param limit the most signposts the player may own; {@code 0} means unlimited
 * @param actor who set it (a player's name, or {@code console})
 * @param at when it was set
 */
public record LimitOverride(UUID player, String playerName, int limit, String actor, Instant at) {

    public LimitOverride {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(at, "at");
        if (playerName.isBlank()) {
            throw new IllegalArgumentException("playerName must not be blank");
        }
        if (actor.isBlank()) {
            throw new IllegalArgumentException("actor must not be blank");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be >= 0 (0 = unlimited), was " + limit);
        }
    }

    /**
     * @return whether this override lifts the limit entirely
     */
    public boolean isUnlimited() {
        return limit == 0;
    }
}
