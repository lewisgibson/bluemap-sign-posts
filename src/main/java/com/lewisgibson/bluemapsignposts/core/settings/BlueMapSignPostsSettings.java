package com.lewisgibson.bluemapsignposts.core.settings;

import com.lewisgibson.bluemapsignposts.core.message.Messages;
import java.util.Objects;

/**
 * The fully-resolved, immutable plugin configuration.
 *
 * <p>Produced by the platform adapter from the on-disk config and consumed by the pure logic. Being
 * platform-free, it can be constructed directly in tests.
 *
 * @param trigger the first-line text that marks a sign as a signpost request
 * @param bannersEnabled whether a banner with a custom name also becomes a signpost
 * @param requiredPlaytimeTicks accumulated playtime, in ticks, required to place a signpost ({@code
 *     0} disables the gate)
 * @param defaultLimit the most signposts one player may own unless a tier or an override says
 *     otherwise ({@code 0} means unlimited)
 * @param operatorsUnlimited whether server operators have no limit
 * @param createPermission the permission node that lets a player place signposts
 * @param bypassPermission the permission node that skips every check
 * @param markerSetLabel the layer name shown in the map's marker menu
 * @param markerSetToggleable whether map visitors can hide the layer
 * @param markerSetDefaultHidden whether the layer starts hidden
 * @param markerMaxDistance how far (in blocks) from the camera a marker is still drawn
 * @param markerDetail the popup template, see {@link
 *     com.lewisgibson.bluemapsignposts.core.map.MarkerContent}
 * @param storage where the store lives
 * @param messages the player-facing message templates
 */
public record BlueMapSignPostsSettings(
        String trigger,
        boolean bannersEnabled,
        long requiredPlaytimeTicks,
        int defaultLimit,
        boolean operatorsUnlimited,
        String createPermission,
        String bypassPermission,
        String markerSetLabel,
        boolean markerSetToggleable,
        boolean markerSetDefaultHidden,
        double markerMaxDistance,
        String markerDetail,
        StorageSettings storage,
        Messages messages) {

    /** The permission node that lifts the limit entirely. */
    public static final String UNLIMITED_PERMISSION = "bluemapsignposts.limit.unlimited";

    /** The prefix of the numeric tier nodes, {@code bluemapsignposts.limit.<n>}. */
    public static final String LIMIT_TIER_PREFIX = "bluemapsignposts.limit.";

    public BlueMapSignPostsSettings {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(createPermission, "createPermission");
        Objects.requireNonNull(bypassPermission, "bypassPermission");
        Objects.requireNonNull(markerSetLabel, "markerSetLabel");
        Objects.requireNonNull(markerDetail, "markerDetail");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(messages, "messages");
        if (trigger.isBlank()) {
            throw new IllegalArgumentException("trigger must not be blank");
        }
        if (requiredPlaytimeTicks < 0) {
            throw new IllegalArgumentException(
                    "requiredPlaytimeTicks must be >= 0, was " + requiredPlaytimeTicks);
        }
        if (defaultLimit < 0) {
            throw new IllegalArgumentException("defaultLimit must be >= 0, was " + defaultLimit);
        }
        if (createPermission.isBlank()) {
            throw new IllegalArgumentException("createPermission must not be blank");
        }
        if (bypassPermission.isBlank()) {
            throw new IllegalArgumentException("bypassPermission must not be blank");
        }
        if (markerSetLabel.isBlank()) {
            throw new IllegalArgumentException("markerSetLabel must not be blank");
        }
        if (markerDetail.isBlank()) {
            // BlueMap shows a POI's label as HTML when its detail is empty, and the label is
            // player-written text that is only escaped inside the detail template.
            throw new IllegalArgumentException(
                    "markerDetail must not be blank (BlueMap would show the raw label as HTML)");
        }
        if (!(markerMaxDistance > 0)) {
            throw new IllegalArgumentException(
                    "markerMaxDistance must be > 0, was " + markerMaxDistance);
        }
    }
}
