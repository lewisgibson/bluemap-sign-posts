package com.lewisgibson.bluemapsignposts.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.lewisgibson.bluemapsignposts.core.access.LimitOverride;
import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostKey;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.sign.Side;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.block.SignChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * The admin command set and the limit model, end to end on a mocked server: tiers through real
 * permission attachments, overrides through the real command and the real store, and every listing.
 */
@DisplayName("/signposts (integration)")
class SignpostCommandIntegrationTest {

    private ServerMock server;
    private WorldMock world;
    private FakeBlueMapAPI blueMap;
    private BlueMapSignPostsPlugin plugin;
    private PlayerMock admin;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        blueMap = new FakeBlueMapAPI("world");
        blueMap.enable();
        plugin = MockBukkit.load(BlueMapSignPostsPlugin.class);
        admin = server.addPlayer("Admin");
        admin.setOp(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        blueMap.disable();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName(
            "a permission tier raises the limit, the highest tier wins, and the unlimited node lifts it")
    void tiers() {
        setDefaultLimit(1);
        PlayerMock alice = server.addPlayer("Alice");
        alice.addAttachment(plugin, "bluemapsignposts.limit.2", true);
        alice.addAttachment(plugin, "bluemapsignposts.limit.3", true);
        alice.addAttachment(plugin, "bluemapsignposts.limit.10", false);

        sign(alice, 1, "One");
        sign(alice, 2, "Two");
        sign(alice, 3, "Three");
        sign(alice, 4, "Four");
        assertThat(plugin.service().registry().countOwnedBy(alice.getUniqueId())).isEqualTo(3);

        admin.performCommand("signposts limit Alice");
        assertThat(admin.nextMessage()).contains("BlueMapSignPosts");
        assertThat(admin.nextMessage()).contains("3 (3 used)");
        assertThat(admin.nextMessage()).contains("bluemapsignposts.limit.3");

        alice.addAttachment(plugin, "bluemapsignposts.limit.unlimited", true);
        sign(alice, 5, "Five");
        assertThat(plugin.service().registry().countOwnedBy(alice.getUniqueId())).isEqualTo(4);
        admin.performCommand("signposts limit Alice");
        admin.nextMessage();
        assertThat(admin.nextMessage()).contains("unlimited");
        assertThat(admin.nextMessage()).contains("limit.unlimited");
    }

    @Test
    @DisplayName("operators are unlimited by default, and the switch turns that off")
    void operators() {
        setDefaultLimit(1);
        sign(admin, 1, "One");
        sign(admin, 2, "Two");
        admin.nextMessage();
        admin.nextMessage();
        assertThat(plugin.service().registry().countOwnedBy(admin.getUniqueId())).isEqualTo(2);
        admin.performCommand("signposts limit Admin");
        admin.nextMessage();
        assertThat(admin.nextMessage()).contains("unlimited");
        assertThat(admin.nextMessage()).contains("operator status");

        plugin.getConfig().set("limits.operators-unlimited", false);
        plugin.saveConfig();
        plugin.reloadSettings();
        admin.performCommand("signposts limit Admin");
        admin.nextMessage();
        assertThat(admin.nextMessage()).contains("1 (2 used)");
        assertThat(admin.nextMessage()).contains("limits.default = 1");
    }

    @Test
    @DisplayName("an admin override beats tiers, persists in the store, and can be cleared")
    void overrides() {
        setDefaultLimit(1);
        PlayerMock alice = server.addPlayer("Alice");
        alice.addAttachment(plugin, "bluemapsignposts.limit.5", true);

        admin.performCommand("signposts limit set Alice 2");
        assertThat(admin.nextMessage()).contains("Alice's limit is now 2");
        sign(alice, 1, "One");
        sign(alice, 2, "Two");
        sign(alice, 3, "Three");
        assertThat(alice.nextMessage()).contains("Added");
        assertThat(alice.nextMessage()).contains("Added");
        assertThat(alice.nextMessage()).contains("limit is").contains("2");

        admin.performCommand("signposts limit Alice");
        admin.nextMessage();
        assertThat(admin.nextMessage()).contains("2 (2 used)");
        assertThat(admin.nextMessage()).contains("admin override set by Admin");

        LimitOverride stored =
                plugin.service().registry().limitOverride(alice.getUniqueId()).orElseThrow();
        assertThat(stored.actor()).isEqualTo("Admin");
        assertThat(stored.limit()).isEqualTo(2);

        admin.performCommand("signposts limit set Alice unlimited");
        assertThat(admin.nextMessage()).contains("unlimited");
        sign(alice, 3, "Three");
        assertThat(alice.nextMessage()).contains("Added");

        admin.performCommand("signposts limit clear Alice");
        assertThat(admin.nextMessage()).contains("cleared");
        admin.performCommand("signposts limit clear Alice");
        assertThat(admin.nextMessage()).contains("no limit override");
        admin.performCommand("signposts limit Alice");
        admin.nextMessage();
        assertThat(admin.nextMessage()).contains("5 (3 used)");
    }

    @Test
    @DisplayName("limit set validates its arguments and names")
    void limitSetValidation() {
        admin.performCommand("signposts limit set Nobody 3");
        assertThat(admin.nextMessage()).contains("No player called Nobody");
        admin.performCommand("signposts limit set Admin zero");
        assertThat(admin.nextMessage()).contains("whole number");
        admin.performCommand("signposts limit set Admin 0");
        assertThat(admin.nextMessage()).contains("at least 1");
        admin.performCommand("signposts limit set Admin");
        assertThat(admin.nextMessage()).contains("Usage");
        admin.performCommand("signposts limit clear");
        assertThat(admin.nextMessage()).contains("Usage");
        admin.performCommand("signposts limit");
        assertThat(admin.nextMessage()).contains("Usage");
        admin.performCommand("signposts limit Nobody");
        assertThat(admin.nextMessage()).contains("No player called Nobody");
    }

    @Test
    @DisplayName(
            "an offline player is found by their signposts, and their limit is explained as offline")
    void offlinePlayer() {
        PlayerMock alice = server.addPlayer("Alice");
        sign(alice, 1, "Farm");
        alice.disconnect();

        admin.performCommand("signposts limit Alice");
        admin.nextMessage();
        assertThat(admin.nextMessage()).contains("10 (1 used)");
        assertThat(admin.nextMessage()).contains("offline");

        admin.performCommand("signposts limit set Alice 4");
        assertThat(admin.nextMessage()).contains("Alice's limit is now 4");
        admin.performCommand("signposts list Alice");
        admin.nextMessage();
        assertThat(admin.nextMessage()).contains("Alice has").contains("1 of 4");
    }

    @Test
    @DisplayName("list: own, all, per player, per world, and the console usage line")
    void listing() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        sign(alice, 1, "Farm");
        sign(bob, 2, "Shop");
        alice.nextMessage();
        bob.nextMessage();

        alice.performCommand("signposts list");
        assertThat(alice.nextMessage()).contains("BlueMapSignPosts");
        assertThat(alice.nextMessage()).contains("You have").contains("1 of 10");
        assertThat(alice.nextMessage()).contains("Farm").contains("by Alice");
        assertThat(alice.nextMessage()).isNull();

        alice.performCommand("signposts list all");
        assertThat(alice.nextMessage()).contains("permission");

        admin.performCommand("signposts list all");
        admin.nextMessage();
        assertThat(admin.nextMessage()).contains("Signposts on the map").contains("2");
        assertThat(admin.nextMessage()).isNotNull();
        assertThat(admin.nextMessage()).isNotNull();

        admin.performCommand("signposts list Bob");
        admin.nextMessage();
        assertThat(admin.nextMessage()).contains("Bob has").contains("1 of 10");
        assertThat(admin.nextMessage()).contains("Shop");

        admin.performCommand("signposts list world world");
        admin.nextMessage();
        assertThat(admin.nextMessage()).contains("Signposts in world").contains("2");
        admin.nextMessage();
        admin.nextMessage();
        admin.performCommand("signposts list world world_nether");
        admin.nextMessage();
        assertThat(admin.nextMessage()).contains("0");
        admin.performCommand("signposts list world");
        assertThat(admin.nextMessage()).contains("Usage");
        admin.performCommand("signposts list Nobody");
        assertThat(admin.nextMessage()).contains("No player called Nobody");

        ConsoleCommandSender console = server.getConsoleSender();
        server.dispatchCommand(console, "signposts list");
        server.dispatchCommand(console, "signposts");
    }

    @Test
    @DisplayName("remove takes one signpost off the map by coordinates and leaves the block")
    void remove() {
        PlayerMock alice = server.addPlayer("Alice");
        Block block = sign(alice, 7, "Farm");
        alice.nextMessage();

        admin.performCommand("signposts remove world 7 64 7");
        assertThat(admin.nextMessage()).contains("Removed \"Farm\"").contains("untouched");
        assertThat(blueMap.signposts("world").get("7,64,7")).isNull();
        assertThat(block.getType()).isEqualTo(Material.OAK_SIGN);

        admin.performCommand("signposts remove world 7 64 7");
        assertThat(admin.nextMessage()).contains("no signpost at");
        admin.performCommand("signposts remove world x 64 7");
        assertThat(admin.nextMessage()).contains("whole numbers");
        admin.performCommand("signposts remove world 7");
        assertThat(admin.nextMessage()).contains("Usage");
        alice.performCommand("signposts remove world 7 64 7");
        assertThat(alice.nextMessage()).contains("permission");
    }

    @Test
    @DisplayName("reload and republish, and the admin gate on both")
    void reloadAndRepublish() {
        PlayerMock alice = server.addPlayer("Alice");
        sign(alice, 1, "Farm");
        alice.nextMessage();
        blueMap.map("world").getMarkerSets().clear();

        admin.performCommand("signposts republish");
        assertThat(admin.nextMessage()).contains("Re-published 1");
        assertThat(blueMap.signposts("world").get("1,64,1")).isNotNull();

        blueMap.map("world").getMarkerSets().clear();
        admin.performCommand("signposts reload");
        assertThat(admin.nextMessage()).contains("reloaded").contains("1 signpost");
        assertThat(blueMap.signposts("world").get("1,64,1")).isNotNull();

        plugin.getConfig().set("required-playtime", "soon");
        plugin.saveConfig();
        admin.performCommand("signposts reload");
        assertThat(admin.nextMessage()).contains("Reload failed");

        alice.performCommand("signposts reload");
        assertThat(alice.nextMessage()).contains("permission");
        alice.performCommand("signposts republish");
        assertThat(alice.nextMessage()).contains("permission");
        alice.performCommand("signposts limit Alice");
        assertThat(alice.nextMessage()).contains("permission");
        admin.performCommand("signposts bogus");
        assertThat(admin.nextMessage()).contains("Unknown subcommand");
    }

    @Test
    @DisplayName("tab completion follows the permission and the subcommand")
    void tabCompletion() {
        PlayerMock alice = server.addPlayer("Alice");
        sign(alice, 1, "Farm");
        admin.performCommand("signposts limit set Alice 3");

        assertThat(complete(alice, "")).containsExactly("list");
        assertThat(complete(alice, "list", "")).isEmpty();
        assertThat(complete(admin, "re")).containsExactly("remove", "reload", "republish");
        assertThat(complete(admin, "list", "")).contains("all", "world", "Alice", "Admin");
        assertThat(complete(admin, "list", "world", "w")).containsExactly("world");
        assertThat(complete(admin, "remove", "")).containsExactly("world");
        assertThat(complete(admin, "limit", "")).contains("set", "clear", "Alice");
        assertThat(complete(admin, "limit", "set", "Al")).containsExactly("Alice");
        assertThat(complete(admin, "limit", "clear", "Al")).containsExactly("Alice");
        assertThat(complete(admin, "limit", "set", "Alice", "u")).containsExactly("unlimited");
        assertThat(complete(admin, "limit", "Alice", "x")).isEmpty();
        assertThat(complete(admin, "bogus", "")).isEmpty();
        assertThat(complete(admin, "limit", "set", "Alice", "5", "x")).isEmpty();
    }

    @Test
    @DisplayName("own list shows the playtime requirement when there is one")
    void ownListShowsPlaytime() {
        plugin.getConfig().set("required-playtime", "2h");
        plugin.saveConfig();
        plugin.reloadSettings();
        PlayerMock alice = server.addPlayer("Alice");
        alice.performCommand("signposts list");
        alice.nextMessage();
        alice.nextMessage();
        assertThat(alice.nextMessage()).contains("Required playtime").contains("2h");
    }

    @Test
    @DisplayName("names are found from signposts and overrides of players the server never cached")
    void namesFromTheStoreAlone() {
        UUID ghost = UUID.randomUUID();
        plugin.service()
                .place(
                        new Signpost(
                                new SignpostKey("world", 9, 64, 9),
                                "Ruin",
                                ghost,
                                "Ghost",
                                SignpostSource.SIGN,
                                0L));
        UUID phantom = UUID.randomUUID();
        plugin.service()
                .setLimitOverride(
                        new LimitOverride(phantom, "Phantom", 3, "console", Instant.EPOCH));

        admin.performCommand("signposts list ghost");
        admin.nextMessage();
        assertThat(admin.nextMessage()).contains("Ghost has").contains("1 of 10");
        assertThat(admin.nextMessage()).contains("Ruin");
        admin.performCommand("signposts limit phantom");
        admin.nextMessage();
        assertThat(admin.nextMessage()).contains("Phantom's limit").contains("3 (0 used)");
        assertThat(admin.nextMessage()).contains("admin override set by console");
        admin.performCommand("signposts limit clear Nobody");
        assertThat(admin.nextMessage()).contains("No player called Nobody");

        assertThat(complete(admin, "limit", "")).contains("Ghost", "Phantom");
    }

    @Test
    @DisplayName("tab completion lists a name once however many places it is known from")
    void tabCompletionDeduplicates() {
        PlayerMock alice = server.addPlayer("Alice");
        sign(alice, 1, "Farm");
        sign(alice, 2, "Shop");
        admin.performCommand("signposts limit set Alice 3");
        PlayerMock bob = server.addPlayer("Bob");
        sign(bob, 3, "Mine");
        bob.disconnect();
        admin.performCommand("signposts limit set Bob 4");

        assertThat(complete(admin, "limit", "")).containsOnlyOnce("Alice", "Bob");
        assertThat(complete(admin, "list", "Alice", "x")).isEmpty();
        assertThat(complete(admin, "limit", "clear", "Alice", "x")).isEmpty();
        assertThat(complete(admin, "list", "x", "y", "z")).isEmpty();
    }

    @Test
    @DisplayName("an override set from the console is credited to the console")
    void consoleOverride() {
        server.addPlayer("Alice");
        server.dispatchCommand(server.getConsoleSender(), "signposts limit set Alice 2");
        admin.performCommand("signposts limit Alice");
        admin.nextMessage();
        admin.nextMessage();
        assertThat(admin.nextMessage()).contains("set by console");
    }

    private List<String> complete(PlayerMock sender, String... args) {
        return plugin.getCommand("bluemapsignposts").tabComplete(sender, "signposts", args);
    }

    private void setDefaultLimit(int limit) {
        plugin.getConfig().set("limits.default", limit);
        plugin.saveConfig();
        plugin.reloadSettings();
    }

    private Block sign(PlayerMock player, int x, String label) {
        Block block = world.getBlockAt(x, 64, x);
        block.setType(Material.OAK_SIGN);
        SignChangeEvent event =
                new SignChangeEvent(
                        block,
                        player,
                        List.of(
                                Component.text("[map]"),
                                Component.text(label),
                                Component.empty(),
                                Component.empty()),
                        Side.FRONT);
        server.getPluginManager().callEvent(event);
        return block;
    }
}
