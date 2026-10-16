package com.lewisgibson.bluemapsignposts.bukkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import com.lewisgibson.bluemapsignposts.core.settings.StorageSettings;
import com.lewisgibson.bluemapsignposts.core.settings.StorageType;
import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostKey;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostSource;
import com.lewisgibson.bluemapsignposts.core.signpost.StorageException;
import com.lewisgibson.bluemapsignposts.storage.SignpostStores;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
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
 * Every way a block can stop being a signpost, every edit that is not a plain create, and the
 * failure paths of the store and of BlueMap, on a mocked server with the fake BlueMap registered.
 */
@DisplayName("Signpost lifecycle edge cases (integration)")
class SignpostLifecycleIntegrationTest {

    private ServerMock server;
    private WorldMock world;
    private FakeBlueMapAPI blueMap;
    private BlueMapSignPostsPlugin plugin;
    private PlayerMock alice;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        blueMap = new FakeBlueMapAPI("world");
        blueMap.enable();
        plugin = MockBukkit.load(BlueMapSignPostsPlugin.class);
        alice = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() throws Exception {
        blueMap.disable();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a sign change on a block that is no longer a sign does nothing")
    void signChangeOnNonSign() {
        Block stone = world.getBlockAt(1, 64, 1);
        stone.setType(Material.STONE);
        server.getPluginManager()
                .callEvent(new SignChangeEvent(stone, alice, lines("[map]", "Farm"), Side.FRONT));
        assertThat(plugin.service().registry().size()).isZero();
        assertThat(alice.nextMessage()).isNull();
    }

    @Test
    @DisplayName("the back of a sign is read when the front is an ordinary sign")
    void backSideEdit() {
        Block block = sign(1, "Welcome", "");
        server.getPluginManager()
                .callEvent(new SignChangeEvent(block, alice, lines("[map]", "Back"), Side.BACK));
        assertThat(blueMap.signposts("world").get("1,64,1").getLabel()).isEqualTo("Back");
    }

    @Test
    @DisplayName("another player's edit makes the signpost theirs, and a denied one removes it")
    void someoneElsesEdit() {
        Block block = sign(1, "[map]", "Farm");
        write(alice, block, "[map]", "Farm");
        PlayerMock bob = server.addPlayer("Bob");

        write(bob, block, "[map]", "Bob's farm");
        Signpost now = plugin.service().registry().get(key(1)).orElseThrow();
        assertThat(now.owner()).isEqualTo(bob.getUniqueId());
        assertThat(bob.nextMessage()).contains("Added");

        PlayerMock carol = server.addPlayer("Carol");
        carol.addAttachment(plugin, "bluemapsignposts.create", false);
        write(carol, block, "[map]", "Carol's");
        assertThat(plugin.service().registry().get(key(1))).isEmpty();
        assertThat(carol.nextMessage()).contains("can't add markers");
    }

    @Test
    @DisplayName("placing a block that is not a banner, or a banner with no item meta, is ignored")
    void placementsThatAreNotNamedBanners() {
        Block stone = world.getBlockAt(5, 64, 5);
        stone.setType(Material.STONE);
        place(stone, named(Material.STONE, "Rock"));
        Block banner = world.getBlockAt(6, 64, 6);
        banner.setType(Material.WHITE_BANNER);
        place(banner, new ItemStack(Material.AIR));
        place(banner, named(Material.WHITE_BANNER, "   "));
        assertThat(plugin.service().registry().size()).isZero();
    }

    @Test
    @DisplayName(
            "breaking a block that is not a signpost, or in a world without any, changes nothing")
    void breaksThatAreNotSignposts() {
        server.getPluginManager().callEvent(new BlockBreakEvent(world.getBlockAt(9, 64, 9), alice));
        write(alice, sign(1, "", ""), "[map]", "Farm");
        alice.nextMessage();
        server.getPluginManager().callEvent(new BlockBreakEvent(world.getBlockAt(9, 64, 9), alice));
        assertThat(plugin.service().registry().size()).isEqualTo(1);
        assertThat(alice.nextMessage()).isNull();
    }

    @Test
    @DisplayName("a block destroyed by losing its support loses its signpost")
    void destroyedBlock() {
        Block block = sign(1, "", "");
        server.getPluginManager()
                .callEvent(
                        new BlockDestroyEvent(
                                block, Material.AIR.createBlockData(), null, 0, true));
        write(alice, block, "[map]", "Farm");
        server.getPluginManager()
                .callEvent(
                        new BlockDestroyEvent(
                                block, Material.AIR.createBlockData(), null, 0, true));
        assertThat(plugin.service().registry().size()).isZero();
        assertThat(blueMap.signposts("world").get("1,64,1")).isNull();
    }

    @Test
    @DisplayName("entity and block explosions take the signposts in their block list")
    void explosions() {
        Block first = sign(1, "", "");
        Block second = sign(2, "", "");
        Block other = world.getBlockAt(3, 64, 3);
        server.getPluginManager()
                .callEvent(
                        new EntityExplodeEvent(
                                alice,
                                first.getLocation(),
                                List.of(),
                                1f,
                                ExplosionResult.DESTROY));
        server.getPluginManager()
                .callEvent(
                        new EntityExplodeEvent(
                                alice,
                                first.getLocation(),
                                new ArrayList<>(List.of(first)),
                                1f,
                                ExplosionResult.DESTROY));
        write(alice, first, "[map]", "One");
        write(alice, second, "[map]", "Two");

        server.getPluginManager()
                .callEvent(
                        new EntityExplodeEvent(
                                alice,
                                first.getLocation(),
                                new ArrayList<>(List.of(other, first)),
                                1f,
                                ExplosionResult.DESTROY));
        assertThat(plugin.service().registry().get(key(1))).isEmpty();
        assertThat(plugin.service().registry().get(key(2))).isPresent();

        server.getPluginManager()
                .callEvent(
                        new BlockExplodeEvent(
                                other,
                                other.getState(),
                                new ArrayList<>(List.of(second)),
                                1f,
                                ExplosionResult.DESTROY));
        assertThat(plugin.service().registry().size()).isZero();
    }

    @Test
    @DisplayName("a piston pushing or pulling a signpost block takes its marker")
    void pistons() {
        Block first = sign(1, "", "");
        Block second = sign(2, "", "");
        write(alice, first, "[map]", "One");
        write(alice, second, "[map]", "Two");
        Block piston = world.getBlockAt(0, 64, 0);

        server.getPluginManager()
                .callEvent(new BlockPistonExtendEvent(piston, List.of(first), BlockFace.EAST));
        assertThat(plugin.service().registry().get(key(1))).isEmpty();
        server.getPluginManager()
                .callEvent(new BlockPistonRetractEvent(piston, List.of(second), BlockFace.WEST));
        assertThat(plugin.service().registry().size()).isZero();
    }

    @Test
    @DisplayName("a chunk load keeps what still matches and drops a changed kind, trigger or label")
    void chunkLoadComparesEverything() {
        Block banner = world.getBlockAt(1, 64, 1);
        banner.setType(Material.WHITE_BANNER);
        setBannerName(banner, "Base");
        place(banner, named(Material.WHITE_BANNER, "Base"));

        Block becameBanner = sign(2, "", "");
        write(alice, becameBanner, "[map]", "Was a sign");
        becameBanner.setType(Material.WHITE_BANNER);
        setBannerName(becameBanner, "Was a sign");

        Block lostTrigger = sign(3, "", "");
        write(alice, lostTrigger, "[map]", "Shop");
        applyLines(lostTrigger, "Shop", "", "", "");

        Block relabelled = sign(4, "", "");
        write(alice, relabelled, "[map]", "Old");
        applyLines(relabelled, "[map]", "New", "", "");

        server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(0, 0), false));

        assertThat(plugin.service().registry().get(key(1))).isPresent();
        assertThat(plugin.service().registry().get(key(2))).isEmpty();
        assertThat(plugin.service().registry().get(key(3))).isEmpty();
        assertThat(plugin.service().registry().get(key(4))).isEmpty();
    }

    @Test
    @DisplayName("with BlueMap down a signpost is still kept, and a missing marker set is no error")
    void blueMapDownAndMissingSet() throws Exception {
        blueMap.disable();
        Block block = sign(1, "", "");
        write(alice, block, "[map]", "Farm");
        assertThat(plugin.service().registry().size()).isEqualTo(1);

        blueMap.enable();
        blueMap.map("world").getMarkerSets().clear();
        server.getPluginManager().callEvent(new BlockBreakEvent(block, alice));
        assertThat(plugin.service().registry().size()).isZero();
        assertThat(blueMap.signposts("world")).isNull();
    }

    @Test
    @DisplayName("a signpost in a world the server no longer has is kept but not shown")
    void signpostInAMissingWorld() {
        plugin.service()
                .place(
                        new Signpost(
                                new SignpostKey("deleted_world", 1, 64, 1),
                                "Old base",
                                UUID.randomUUID(),
                                "Bob",
                                SignpostSource.SIGN,
                                0L));
        assertThat(plugin.service().publishAll()).isEqualTo(1);
        assertThat(blueMap.signposts("world")).isNull();
    }

    @Test
    @DisplayName("a message set blank in config is not sent")
    void blankMessageIsSilent() {
        plugin.getConfig().set("messages.created", "");
        plugin.saveConfig();
        plugin.reloadSettings();
        write(alice, sign(1, "", ""), "[map]", "Farm");
        assertThat(plugin.service().registry().size()).isEqualTo(1);
        assertThat(alice.nextMessage()).isNull();
    }

    @Test
    @DisplayName("a store write that fails is logged, and the marker stays")
    void failedWriteIsLogged() throws Exception {
        List<LogRecord> records = capture(plugin.getLogger());
        Path store = plugin.getDataPath().resolve("data.db");
        Files.delete(store);
        Files.createDirectory(store);

        write(alice, sign(1, "", ""), "[map]", "Farm");
        server.getPluginManager().disablePlugin(plugin);

        assertThat(records)
                .anySatisfy(
                        record -> {
                            assertThat(record.getLevel()).isEqualTo(Level.SEVERE);
                            assertThat(record.getMessage()).isEqualTo("A store write failed");
                        });
        assertThat(blueMap.signposts("world").get("1,64,1")).isNotNull();
    }

    @Test
    @DisplayName("a reload onto a store with a malformed row fails and keeps the current store")
    void reloadOntoMalformedStore() throws Exception {
        write(alice, sign(1, "", ""), "[map]", "Farm");
        StorageSettings bad =
                new StorageSettings(
                        StorageType.SQLITE,
                        "bad.db",
                        StorageSettings.sqliteDefault().postgres(),
                        StorageSettings.sqliteDefault().mysql());
        SignpostStores.open(bad, plugin.getDataPath()).close();
        try (Connection connection =
                        DriverManager.getConnection(
                                "jdbc:sqlite:" + plugin.getDataPath().resolve("bad.db"));
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO bmsp_signposts VALUES ('world', 0, 64, 0, 'X', '"
                            + UUID.randomUUID()
                            + "', 'Bob', 'NOT_A_SOURCE', 0)");
        }
        plugin.getConfig().set("storage.sqlite.file", "bad.db");
        plugin.saveConfig();

        assertThatThrownBy(plugin::reloadSettings)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("malformed row");
        assertThat(plugin.service().registry().get(key(1))).isPresent();
        assertThat(plugin.runtime().settings().storage().sqliteFile()).isEqualTo("data.db");
    }

    @Test
    @DisplayName("a wait on the storage thread that runs out of time is logged, and only then")
    void timedOutWaitIsLogged() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        List<LogRecord> records = capture(logger);
        BlueMapSignPostsPlugin.warnUnless(true, logger, "fine");
        BlueMapSignPostsPlugin.warnUnless(false, logger, "Store writes were still pending");
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getLevel()).isEqualTo(Level.WARNING);
        assertThat(records.get(0).getMessage()).isEqualTo("Store writes were still pending");
    }

    private static List<LogRecord> capture(Logger logger) {
        List<LogRecord> records = new ArrayList<>();
        logger.addHandler(
                new Handler() {
                    @Override
                    public void publish(LogRecord record) {
                        records.add(record);
                    }

                    @Override
                    public void flush() {}

                    @Override
                    public void close() {}
                });
        return records;
    }

    private static SignpostKey key(int xz) {
        return new SignpostKey("world", xz, 64, xz);
    }

    private Block sign(int xz, String first, String second) {
        Block block = world.getBlockAt(xz, 64, xz);
        block.setType(Material.OAK_SIGN);
        applyLines(block, first, second, "", "");
        return block;
    }

    private void write(PlayerMock player, Block block, String first, String second) {
        server.getPluginManager()
                .callEvent(new SignChangeEvent(block, player, lines(first, second), Side.FRONT));
        applyLines(block, first, second, "", "");
    }

    private void place(Block block, ItemStack item) {
        server.getPluginManager()
                .callEvent(
                        new BlockPlaceEvent(
                                block,
                                block.getState(),
                                block.getRelative(BlockFace.DOWN),
                                item,
                                alice,
                                true,
                                EquipmentSlot.HAND));
    }

    private static ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    private static void setBannerName(Block block, String name) {
        Banner banner = (Banner) block.getState();
        banner.customName(Component.text(name));
        banner.update(true);
    }

    private static List<Component> lines(String first, String second) {
        return List.of(
                Component.text(first),
                Component.text(second),
                Component.empty(),
                Component.empty());
    }

    private static void applyLines(Block block, String... lines) {
        Sign sign = (Sign) block.getState();
        for (int index = 0; index < lines.length; index++) {
            sign.getSide(Side.FRONT).line(index, Component.text(lines[index]));
        }
        sign.update(true);
    }
}
