package com.lewisgibson.bluemapsignposts.bukkit.limit;

import com.lewisgibson.bluemapsignposts.bukkit.PluginRuntime;
import com.lewisgibson.bluemapsignposts.core.access.EffectiveLimit;
import com.lewisgibson.bluemapsignposts.core.access.LimitOverride;
import com.lewisgibson.bluemapsignposts.core.access.LimitResolver;
import com.lewisgibson.bluemapsignposts.core.settings.BlueMapSignPostsSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

/** Resolves a player's effective limit from the server's view of them. */
public final class EffectiveLimits {

    private EffectiveLimits() {}

    /**
     * The limit for an online player: override, nodes, operator status and the default, all
     * readable.
     *
     * @param player the online player
     * @param runtime the current runtime
     * @return the effective limit and why
     */
    public static EffectiveLimit of(Player player, PluginRuntime runtime) {
        BlueMapSignPostsSettings settings = runtime.settings();
        List<String> held = new ArrayList<>();
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (info.getValue()) {
                held.add(info.getPermission());
            }
        }
        return LimitResolver.resolve(
                override(player.getUniqueId(), runtime),
                player.hasPermission(BlueMapSignPostsSettings.UNLIMITED_PERMISSION),
                player.isOp(),
                settings.operatorsUnlimited(),
                LimitResolver.highestTier(held, BlueMapSignPostsSettings.LIMIT_TIER_PREFIX),
                settings.defaultLimit());
    }

    /**
     * The limit for a player who is offline: their permission nodes cannot be read, so only the
     * override, operator status and the default apply. Tiers they hold count again the moment they
     * are online, which is also the only time a limit is enforced.
     *
     * @param player the offline player
     * @param runtime the current runtime
     * @return the effective limit and why, as far as it can be known offline
     */
    public static EffectiveLimit ofOffline(OfflinePlayer player, PluginRuntime runtime) {
        BlueMapSignPostsSettings settings = runtime.settings();
        return LimitResolver.resolve(
                override(player.getUniqueId(), runtime),
                false,
                player.isOp(),
                settings.operatorsUnlimited(),
                OptionalInt.empty(),
                settings.defaultLimit());
    }

    private static Optional<LimitOverride> override(UUID player, PluginRuntime runtime) {
        return runtime.service().registry().limitOverride(player);
    }
}
