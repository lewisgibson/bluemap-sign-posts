package com.lewisgibson.bluemapsignposts.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostKey;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * End-to-end tests booting the plugin on a mocked Paper server with a fake BlueMap registered,
 * firing real Bukkit events, and checking what lands in the map's marker set and on disk.
 */
@DisplayName("BlueMapSignPosts (integration)")
class BlueMapSignPostsIntegrationTest {

    private ServerMock server;
    private WorldMock world;
    private FakeBlueMapAPI blueMap;
    private BlueMapSignPostsPlugin plugin;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        blueMap = new FakeBlueMapAPI("world");
        blueMap.enable();
        plugin = MockBukkit.load(BlueMapSignPostsPlugin.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        blueMap.disable();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("enables, registers its command and starts with no signposts")
    void enablesCleanly() {
        assertThat(plugin.isEnabled()).isTrue();
        assertThat(plugin.getCommand("bluemapsignposts")).isNotNull();
        assertThat(plugin.service().registry().size()).isZero();
    }

    @Test
    @DisplayName("a [map] sign becomes a POI marker on the world's map and is written to disk")
    void signBecomesMarker() throws Exception {
        PlayerMock player = server.addPlayer("Alice");
        Block block = signAt(10, 64, 10);

        writeSign(player, block, "[map]", "Iron", "farm", "");

        MarkerSet set = blueMap.signposts("world");
        assertThat(set).isNotNull();
        assertThat(set.getLabel()).isEqualTo("Signposts");
        Marker marker = set.get("10,64,10");
        assertThat(marker).isInstanceOf(POIMarker.class);
        assertThat(marker.getLabel()).isEqualTo("Iron farm");
        assertThat(marker.getPosition().getX()).isEqualTo(10.5d);
        assertThat(((POIMarker) marker).getDetail()).contains("Iron farm").contains("Alice");

        assertThat(plugin.getDataPath().resolve("data.db")).exists();
        assertThat(plugin.service().registry().get(new SignpostKey("world", 10, 64, 10)))
                .map(Signpost::label)
                .contains("Iron farm");
        assertThat(player.nextMessage()).contains("Iron farm");
    }

    @Test
    @DisplayName(
            "a label written as MiniMessage markup is shown as text, never as a click or colour")
    void hostileLabelIsInert() throws Exception {
        PlayerMock player = server.addPlayer("Alice");
        Block block = signAt(10, 64, 10);

        writeSign(player, block, "[map]", "<click:run_command:/op x><red>Hi", "", "");

        Component message = player.nextComponentMessage();
        assertThat(PlainTextComponentSerializer.plainText().serialize(message))
                .isEqualTo("Added to the map: <click:run_command:/op x><red>Hi");
        assertThat(injectsMarkup(message)).isFalse();
    }

    private static boolean injectsMarkup(Component component) {
        return component.clickEvent() != null
                || component.color() == NamedTextColor.RED
                || component.children().stream().anyMatch(child -> injectsMarkup(child));
    }

    @Test
    @DisplayName("an ordinary sign is ignored")
    void ordinarySignIgnored() {
        PlayerMock player = server.addPlayer();
        writeSign(player, signAt(1, 64, 1), "Welcome", "home", "", "");
        assertThat(blueMap.signposts("world")).isNull();
        assertThat(player.nextMessage()).isNull();
    }

    @Test
    @DisplayName("a trigger with nothing under it tells the player and makes no marker")
    void emptyLabelRefused() {
        PlayerMock player = server.addPlayer();
        writeSign(player, signAt(1, 64, 1), "[map]", "", "", "");
        assertThat(blueMap.signposts("world")).isNull();
        assertThat(player.nextMessage()).contains("[map]");
    }

    @Test
    @DisplayName("breaking the sign removes the marker")
    void breakRemoves() {
        PlayerMock player = server.addPlayer();
        Block block = signAt(10, 64, 10);
        writeSign(player, block, "[map]", "Farm", "", "");
        player.nextMessage();

        server.getPluginManager().callEvent(new BlockBreakEvent(block, player));

        assertThat(blueMap.signposts("world").get("10,64,10")).isNull();
        assertThat(plugin.service().registry().size()).isZero();
        assertThat(player.nextMessage()).contains("Farm");
    }

    @Test
    @DisplayName("rewriting the sign without the trigger removes the marker")
    void editWithoutTriggerRemoves() {
        PlayerMock player = server.addPlayer();
        Block block = signAt(10, 64, 10);
        writeSign(player, block, "[map]", "Farm", "", "");
        writeSign(player, block, "Just", "a sign", "", "");
        assertThat(blueMap.signposts("world").get("10,64,10")).isNull();
    }

    @Test
    @DisplayName("the owner rewriting their sign updates the label without a new slot")
    void ownEditUpdates() {
        plugin.getConfig().set("limits.default", 1);
        plugin.saveConfig();
        plugin.reloadSettings();
        PlayerMock player = server.addPlayer();
        Block block = signAt(10, 64, 10);
        writeSign(player, block, "[map]", "Farm", "", "");
        player.nextMessage();

        writeSign(player, block, "[map]", "Iron farm", "", "");

        assertThat(blueMap.signposts("world").get("10,64,10").getLabel()).isEqualTo("Iron farm");
        assertThat(plugin.service().registry().size()).isEqualTo(1);
        assertThat(player.nextMessage()).contains("Updated");
    }

    @Test
    @DisplayName("a player without the create permission gets no marker, and the sign stays")
    void permissionDenied() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, "bluemapsignposts.create", false);
        SignChangeEvent event = writeSign(player, signAt(1, 64, 1), "[map]", "Farm", "", "");
        assertThat(event.isCancelled()).isFalse();
        assertThat(blueMap.signposts("world")).isNull();
        assertThat(player.nextMessage()).contains("can't");
    }

    @Test
    @DisplayName("the playtime gate refuses a new player and lets a bypass through")
    void playtimeGate() {
        plugin.getConfig().set("required-playtime", "1h");
        plugin.saveConfig();
        plugin.reloadSettings();
        PlayerMock newcomer = server.addPlayer();
        newcomer.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
        writeSign(newcomer, signAt(1, 64, 1), "[map]", "Farm", "", "");
        assertThat(blueMap.signposts("world")).isNull();
        assertThat(newcomer.nextMessage()).contains("1h");

        PlayerMock staff = server.addPlayer();
        staff.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
        staff.addAttachment(plugin, "bluemapsignposts.bypass", true);
        writeSign(staff, signAt(2, 64, 2), "[map]", "Farm", "", "");
        assertThat(blueMap.signposts("world").get("2,64,2")).isNotNull();
    }

    @Test
    @DisplayName("the per-player limit refuses the one past it")
    void limit() {
        plugin.getConfig().set("limits.default", 1);
        plugin.saveConfig();
        plugin.reloadSettings();
        PlayerMock player = server.addPlayer();
        writeSign(player, signAt(1, 64, 1), "[map]", "One", "", "");
        player.nextMessage();
        writeSign(player, signAt(2, 64, 2), "[map]", "Two", "", "");
        assertThat(blueMap.signposts("world").get("2,64,2")).isNull();
        assertThat(player.nextMessage()).contains("limit is").contains("free a slot");
    }

    @Test
    @DisplayName("a named banner becomes a marker; an unnamed one does not")
    void bannerBecomesMarker() {
        PlayerMock player = server.addPlayer("Bob");
        Block against = world.getBlockAt(5, 63, 5);
        against.setType(Material.STONE);
        Block placed = world.getBlockAt(5, 64, 5);
        placed.setType(Material.WHITE_BANNER);

        ItemStack unnamed = new ItemStack(Material.WHITE_BANNER);
        server.getPluginManager()
                .callEvent(
                        new BlockPlaceEvent(
                                placed,
                                placed.getState(),
                                against,
                                unnamed,
                                player,
                                true,
                                EquipmentSlot.HAND));
        assertThat(blueMap.signposts("world")).isNull();

        ItemStack named = new ItemStack(Material.WHITE_BANNER);
        ItemMeta meta = named.getItemMeta();
        meta.customName(Component.text("Bob's base"));
        named.setItemMeta(meta);
        server.getPluginManager()
                .callEvent(
                        new BlockPlaceEvent(
                                placed,
                                placed.getState(),
                                against,
                                named,
                                player,
                                true,
                                EquipmentSlot.HAND));

        Marker marker = blueMap.signposts("world").get("5,64,5");
        assertThat(marker.getLabel()).isEqualTo("Bob's base");
        assertThat(((POIMarker) marker).getDetail()).contains("Bob&#39;s base");
    }

    @Test
    @DisplayName("a banner placement the server undoes (canBuild false) makes no marker")
    void bannerThatCannotBuildIgnored() {
        PlayerMock player = server.addPlayer("Bob");
        Block against = world.getBlockAt(5, 63, 5);
        against.setType(Material.STONE);
        Block placed = world.getBlockAt(5, 64, 5);
        placed.setType(Material.WHITE_BANNER);

        server.getPluginManager()
                .callEvent(
                        new BlockPlaceEvent(
                                placed,
                                placed.getState(),
                                against,
                                namedBanner("Spawn shop"),
                                player,
                                false,
                                EquipmentSlot.HAND));

        assertThat(blueMap.signposts("world")).isNull();
        assertThat(plugin.service().registry().size()).isZero();
        assertThat(player.nextMessage()).isNull();
    }

    private static ItemStack namedBanner(String name) {
        ItemStack item = new ItemStack(Material.WHITE_BANNER);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    @Test
    @DisplayName("a blank marker popup in config disables the plugin")
    void blankDetailDisables() {
        server.getPluginManager().disablePlugin(plugin);
        plugin = MockBukkit.load(BlueMapSignPostsPlugin.class);
        plugin.getConfig().set("marker.detail", "");
        plugin.saveConfig();
        server.getPluginManager().disablePlugin(plugin);

        plugin = MockBukkit.load(BlueMapSignPostsPlugin.class);

        assertThat(plugin.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("banners can be switched off")
    void bannersOff() {
        plugin.getConfig().set("banners.enabled", false);
        plugin.saveConfig();
        plugin.reloadSettings();
        PlayerMock player = server.addPlayer();
        Block placed = world.getBlockAt(5, 64, 5);
        placed.setType(Material.WHITE_BANNER);
        ItemStack named = new ItemStack(Material.WHITE_BANNER);
        ItemMeta meta = named.getItemMeta();
        meta.customName(Component.text("Base"));
        named.setItemMeta(meta);
        server.getPluginManager()
                .callEvent(
                        new BlockPlaceEvent(
                                placed,
                                placed.getState(),
                                placed,
                                named,
                                player,
                                true,
                                EquipmentSlot.HAND));
        assertThat(blueMap.signposts("world")).isNull();
    }

    @Test
    @DisplayName("a chunk load drops a stored signpost whose block no longer says so")
    void chunkLoadVerifies() {
        PlayerMock player = server.addPlayer();
        Block kept = signAt(1, 64, 1);
        writeSign(player, kept, "[map]", "Kept", "", "");
        applyLines(kept, "[map]", "Kept", "", "");
        Block gone = signAt(3, 64, 3);
        writeSign(player, gone, "[map]", "Gone", "", "");
        gone.setType(Material.AIR);

        server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(0, 0), false));

        assertThat(blueMap.signposts("world").get("1,64,1")).isNotNull();
        assertThat(blueMap.signposts("world").get("3,64,3")).isNull();
        assertThat(plugin.service().registry().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("stored signposts are re-published each time BlueMap enables")
    void republishesOnBlueMapEnable() throws Exception {
        PlayerMock player = server.addPlayer();
        writeSign(player, signAt(1, 64, 1), "[map]", "Farm", "", "");

        blueMap.disable();
        blueMap = new FakeBlueMapAPI("world");
        assertThat(blueMap.signposts("world")).isNull();
        blueMap.enable();

        assertThat(blueMap.signposts("world").get("1,64,1").getLabel()).isEqualTo("Farm");
    }

    @Test
    @DisplayName("a reload onto a different store takes the old store's markers off the map")
    void storeSwitchRetractsOldMarkers() {
        PlayerMock player = server.addPlayer();
        writeSign(player, signAt(1, 64, 1), "[map]", "Old", "", "");
        assertThat(blueMap.signposts("world").get("1,64,1")).isNotNull();

        plugin.getConfig().set("storage.sqlite.file", "other.db");
        plugin.saveConfig();
        server.dispatchCommand(server.getConsoleSender(), "signposts reload");

        assertThat(plugin.service().registry().size()).isZero();
        assertThat(blueMap.signposts("world").get("1,64,1")).isNull();
        assertThat(plugin.getDataPath().resolve("other.db")).exists();
    }

    @Test
    @DisplayName("signposts survive a plugin restart through the store")
    void survivesRestart() {
        PlayerMock player = server.addPlayer();
        writeSign(player, signAt(1, 64, 1), "[map]", "Farm", "", "");
        Path dataFolder = plugin.getDataPath();

        server.getPluginManager().disablePlugin(plugin);
        blueMap.map("world").getMarkerSets().clear();
        plugin = MockBukkit.load(BlueMapSignPostsPlugin.class);

        assertThat(plugin.getDataPath()).isEqualTo(dataFolder);
        assertThat(dataFolder.resolve("data.db")).exists();
        assertThat(plugin.service().registry().size()).isEqualTo(1);
        assertThat(blueMap.signposts("world").get("1,64,1").getLabel()).isEqualTo("Farm");
    }

    @Test
    @DisplayName("a world that loads later gets its signposts back on the map")
    void worldLoadRepublishes() {
        PlayerMock player = server.addPlayer();
        writeSign(player, signAt(5, 64, 5), "[map]", "Late", "", "");
        blueMap.map("world").getMarkerSets().clear();

        server.getPluginManager().callEvent(new org.bukkit.event.world.WorldLoadEvent(world));

        assertThat(blueMap.signposts("world").get("5,64,5").getLabel()).isEqualTo("Late");
    }

    @Test
    @DisplayName("the same instance disabled and enabled again still writes to its store")
    void reEnabledInstanceStillWrites() {
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().enablePlugin(plugin);
        PlayerMock player = server.addPlayer();

        writeSign(player, signAt(2, 64, 2), "[map]", "Mill", "", "");
        server.getPluginManager().disablePlugin(plugin);
        plugin = MockBukkit.load(BlueMapSignPostsPlugin.class);

        assertThat(plugin.service().registry().get(new SignpostKey("world", 2, 64, 2)))
                .map(Signpost::label)
                .contains("Mill");
    }

    @Test
    @DisplayName("an unusable store disables the plugin instead of running without it")
    void badStoreDisables() throws Exception {
        Path store = plugin.getDataPath().resolve("data.db");
        server.getPluginManager().disablePlugin(plugin);
        Files.writeString(store, "not a database");

        plugin = MockBukkit.load(BlueMapSignPostsPlugin.class);

        assertThat(plugin.isEnabled()).isFalse();
        assertThat(Files.readString(store)).isEqualTo("not a database");
    }

    @Test
    @DisplayName("an invalid config disables the plugin")
    void badConfigDisables() {
        server.getPluginManager().disablePlugin(plugin);
        plugin = MockBukkit.load(BlueMapSignPostsPlugin.class);
        plugin.getConfig().set("required-playtime", "soon");
        plugin.saveConfig();
        server.getPluginManager().disablePlugin(plugin);

        plugin = MockBukkit.load(BlueMapSignPostsPlugin.class);

        assertThat(plugin.isEnabled()).isFalse();
    }

    private Block signAt(int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.OAK_SIGN);
        return block;
    }

    private SignChangeEvent writeSign(PlayerMock player, Block block, String... lines) {
        List<Component> components =
                List.of(
                        Component.text(lines[0]),
                        Component.text(lines[1]),
                        Component.text(lines[2]),
                        Component.text(lines[3]));
        SignChangeEvent event = new SignChangeEvent(block, player, components, Side.FRONT);
        server.getPluginManager().callEvent(event);
        return event;
    }

    /** Writes lines into the saved sign state, as the server does after the change event. */
    private static void applyLines(Block block, String... lines) {
        Sign sign = (Sign) block.getState();
        for (int index = 0; index < lines.length; index++) {
            sign.getSide(Side.FRONT).line(index, Component.text(lines[index]));
        }
        sign.update(true);
    }
}
