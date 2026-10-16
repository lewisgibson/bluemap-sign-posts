package com.lewisgibson.bluemapsignposts.core.access;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Decides a player's effective limit from the inputs the adapter reads, in a fixed order that an
 * admin can predict: an override, the unlimited node, operator status, the highest tier node, then
 * the default.
 *
 * <p>An override beats everything because it is the one rule a person set for this one player on
 * purpose; the unlimited node and operator status come next because they are explicit grants; tiers
 * beat the default because a tier is a decision about a group and the default is a decision about
 * nobody in particular.
 */
public final class LimitResolver {

    private LimitResolver() {}

    /**
     * @param override the admin override, if one is set
     * @param hasUnlimitedNode whether the player holds the {@code limit.unlimited} node
     * @param operator whether the player is a server operator
     * @param operatorsUnlimited whether config lifts the limit for operators
     * @param highestTier the highest {@code limit.<n>} node held, if any
     * @param defaultLimit the config default ({@code 0} = unlimited)
     * @return the effective limit and the rule that produced it
     */
    public static EffectiveLimit resolve(
            Optional<LimitOverride> override,
            boolean hasUnlimitedNode,
            boolean operator,
            boolean operatorsUnlimited,
            OptionalInt highestTier,
            int defaultLimit) {
        Objects.requireNonNull(override, "override");
        Objects.requireNonNull(highestTier, "highestTier");
        if (defaultLimit < 0) {
            throw new IllegalArgumentException("defaultLimit must be >= 0, was " + defaultLimit);
        }
        if (override.isPresent()) {
            return new EffectiveLimit(override.get().limit(), LimitSource.OVERRIDE);
        }
        if (hasUnlimitedNode) {
            return new EffectiveLimit(0, LimitSource.UNLIMITED_PERMISSION);
        }
        if (operator && operatorsUnlimited) {
            return new EffectiveLimit(0, LimitSource.OPERATOR);
        }
        if (highestTier.isPresent()) {
            return new EffectiveLimit(highestTier.getAsInt(), LimitSource.TIER_PERMISSION);
        }
        return new EffectiveLimit(defaultLimit, LimitSource.DEFAULT);
    }

    /**
     * Finds the highest numeric tier among a player's permission nodes.
     *
     * @param nodes the nodes the player holds (only those set true)
     * @param prefix the tier prefix, e.g. {@code bluemapsignposts.limit.}
     * @return the highest {@code <prefix><n>} value, or empty if none is held; a node whose suffix
     *     is not a non-negative whole number is ignored, so {@code limit.unlimited} and a typo
     *     never count as a tier
     */
    public static OptionalInt highestTier(Collection<String> nodes, String prefix) {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(prefix, "prefix");
        OptionalInt highest = OptionalInt.empty();
        for (String node : nodes) {
            if (node == null || !node.startsWith(prefix)) {
                continue;
            }
            String suffix = node.substring(prefix.length());
            if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) {
                continue;
            }
            int tier;
            try {
                tier = Integer.parseInt(suffix);
            } catch (NumberFormatException overflow) {
                continue;
            }
            if (highest.isEmpty() || tier > highest.getAsInt()) {
                highest = OptionalInt.of(tier);
            }
        }
        return highest;
    }
}
