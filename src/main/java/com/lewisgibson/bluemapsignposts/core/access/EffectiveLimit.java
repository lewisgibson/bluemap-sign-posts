package com.lewisgibson.bluemapsignposts.core.access;

import java.util.Objects;

/**
 * A player's limit as it applies right now, with the rule that produced it, so the number and the
 * reason are always shown together.
 *
 * @param limit the most signposts the player may own; {@code 0} means unlimited
 * @param source the rule that decided it
 */
public record EffectiveLimit(int limit, LimitSource source) {

    public EffectiveLimit {
        Objects.requireNonNull(source, "source");
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be >= 0 (0 = unlimited), was " + limit);
        }
    }

    /**
     * @return whether there is no limit
     */
    public boolean isUnlimited() {
        return limit == 0;
    }

    /**
     * @param ownedCount how many signposts the player owns, not counting the one being written
     * @return whether one more is allowed
     */
    public boolean allows(int ownedCount) {
        return isUnlimited() || ownedCount < limit;
    }

    /**
     * @return the limit as a word or a number, for messages
     */
    public String describe() {
        return isUnlimited() ? "unlimited" : Integer.toString(limit);
    }
}
