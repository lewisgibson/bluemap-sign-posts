package com.lewisgibson.bluemapsignposts.core.access;

import java.util.Objects;

/**
 * Decides whether a player may place a new signpost, from the permission and bypass flags the
 * adapter reads, the player's accumulated playtime, how many signposts they already own and their
 * effective limit.
 *
 * <p>Checks run cheapest and most decisive first: bypass, then permission, then playtime, then the
 * limit. Updating a signpost the player already owns never comes here - the adapter treats that as
 * an edit, not a creation - so the count passed in is the number of OTHER signposts the player
 * holds.
 */
public final class SignpostPolicy {

    private final long requiredPlaytimeTicks;

    /**
     * @param requiredPlaytimeTicks the accumulated playtime, in game ticks (20 per second), a
     *     player needs before placing a signpost; {@code 0} disables the gate
     * @throws IllegalArgumentException if the value is negative
     */
    public SignpostPolicy(long requiredPlaytimeTicks) {
        if (requiredPlaytimeTicks < 0) {
            throw new IllegalArgumentException(
                    "requiredPlaytimeTicks must be >= 0, was " + requiredPlaytimeTicks);
        }
        this.requiredPlaytimeTicks = requiredPlaytimeTicks;
    }

    /**
     * @return the playtime requirement in ticks ({@code 0} when disabled)
     */
    public long requiredPlaytimeTicks() {
        return requiredPlaytimeTicks;
    }

    /**
     * Evaluates whether a new signpost may be placed.
     *
     * @param mayCreate whether the player holds the create permission
     * @param hasBypass whether the player holds the bypass permission
     * @param playtimeTicks the player's accumulated playtime in ticks
     * @param ownedCount how many signposts the player already owns, not counting the block being
     *     written
     * @param limit the player's effective limit
     * @return the decision
     */
    public AccessResult evaluate(
            boolean mayCreate,
            boolean hasBypass,
            long playtimeTicks,
            int ownedCount,
            EffectiveLimit limit) {
        Objects.requireNonNull(limit, "limit");
        if (hasBypass) {
            return AccessResult.allowed(DecisionReason.BYPASS);
        }
        if (!mayCreate) {
            return AccessResult.denied(DecisionReason.NO_PERMISSION);
        }
        if (playtimeTicks < requiredPlaytimeTicks) {
            return AccessResult.denied(DecisionReason.BELOW_THRESHOLD);
        }
        if (!limit.allows(ownedCount)) {
            return AccessResult.denied(DecisionReason.LIMIT_REACHED);
        }
        return AccessResult.allowed(DecisionReason.ALLOWED);
    }
}
