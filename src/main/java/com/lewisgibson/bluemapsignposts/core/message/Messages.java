package com.lewisgibson.bluemapsignposts.core.message;

import java.util.Objects;

/**
 * The player-facing message templates, as configured. Each is a MiniMessage string with {@code
 * %placeholder%} tokens; a blank template means "say nothing" for that event.
 *
 * @param created shown when a signpost is put on the map ({@code %label%})
 * @param updated shown when a player rewrites their own signpost ({@code %label%})
 * @param removed shown to the player who breaks a signpost block ({@code %label%})
 * @param deniedPermission shown when the player lacks the create permission
 * @param deniedPlaytime shown when the player is below the playtime requirement ({@code
 *     %required%}, {@code %played%}, {@code %remaining%})
 * @param deniedLimit shown when the player is at the per-player limit ({@code %count%}, {@code
 *     %limit%})
 * @param emptyLabel shown when the trigger line has nothing under it ({@code %trigger%})
 */
public record Messages(
        String created,
        String updated,
        String removed,
        String deniedPermission,
        String deniedPlaytime,
        String deniedLimit,
        String emptyLabel) {

    public Messages {
        Objects.requireNonNull(created, "created");
        Objects.requireNonNull(updated, "updated");
        Objects.requireNonNull(removed, "removed");
        Objects.requireNonNull(deniedPermission, "deniedPermission");
        Objects.requireNonNull(deniedPlaytime, "deniedPlaytime");
        Objects.requireNonNull(deniedLimit, "deniedLimit");
        Objects.requireNonNull(emptyLabel, "emptyLabel");
    }
}
