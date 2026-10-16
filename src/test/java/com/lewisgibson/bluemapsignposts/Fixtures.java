package com.lewisgibson.bluemapsignposts;

import com.lewisgibson.bluemapsignposts.core.access.LimitOverride;
import com.lewisgibson.bluemapsignposts.core.message.Messages;
import com.lewisgibson.bluemapsignposts.core.settings.BlueMapSignPostsSettings;
import com.lewisgibson.bluemapsignposts.core.settings.StorageSettings;
import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostKey;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostSource;
import java.time.Instant;
import java.util.UUID;

/** Shared valid instances for the unit tests. */
public final class Fixtures {

    public static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final Instant T1 = Instant.parse("2026-09-10T12:00:00Z");
    public static final Messages MESSAGES =
            new Messages("created", "updated", "removed", "perm", "play", "limit", "empty");

    private Fixtures() {}

    public static Signpost signpost(String world, int x, int y, int z, String label, UUID owner) {
        return new Signpost(
                new SignpostKey(world, x, y, z),
                label,
                owner,
                owner.equals(ALICE) ? "Alice" : "Bob",
                SignpostSource.SIGN,
                1_700_000_000_000L);
    }

    public static LimitOverride override(UUID player, int limit) {
        return new LimitOverride(
                player, player.equals(ALICE) ? "Alice" : "Bob", limit, "console", T1);
    }

    public static BlueMapSignPostsSettings settings() {
        return new BlueMapSignPostsSettings(
                "[map]",
                true,
                0L,
                10,
                true,
                "bluemapsignposts.create",
                "bluemapsignposts.bypass",
                "Signposts",
                true,
                false,
                100000.0d,
                "%label%",
                StorageSettings.sqliteDefault(),
                MESSAGES);
    }
}
