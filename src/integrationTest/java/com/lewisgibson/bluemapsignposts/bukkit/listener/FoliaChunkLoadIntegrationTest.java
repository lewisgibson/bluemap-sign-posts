package com.lewisgibson.bluemapsignposts.bukkit.listener;

import static org.assertj.core.api.Assertions.assertThat;

import com.lewisgibson.bluemapsignposts.bukkit.BlueMapSignPostsPlugin;
import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostKey;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostSource;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.ChunkLoadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * On Folia the chunk-load check is handed to the region scheduler instead of reading block states
 * on whatever thread fired the event. MockBukkit has no region scheduler, so this one is a
 * recording stand-in that holds each task until the test runs it.
 */
@DisplayName("SignpostListener chunk load on Folia (integration)")
class FoliaChunkLoadIntegrationTest {

    /** A scheduled region task and the chunk it was scheduled for. */
    private record Scheduled(World world, int chunkX, int chunkZ, Runnable task) {}

    private final List<Scheduled> scheduled = new ArrayList<>();
    private ServerMock server;
    private WorldMock world;
    private BlueMapSignPostsPlugin plugin;

    @BeforeEach
    void setUp() {
        RegionScheduler recorder =
                (RegionScheduler)
                        Proxy.newProxyInstance(
                                RegionScheduler.class.getClassLoader(),
                                new Class<?>[] {RegionScheduler.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("execute")) {
                                        scheduled.add(
                                                new Scheduled(
                                                        (World) args[1],
                                                        (Integer) args[2],
                                                        (Integer) args[3],
                                                        (Runnable) args[4]));
                                        return null;
                                    }
                                    throw new UnsupportedOperationException(method.getName());
                                });
        server =
                MockBukkit.mock(
                        new ServerMock() {
                            @Override
                            public RegionScheduler getRegionScheduler() {
                                return recorder;
                            }
                        });
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.load(BlueMapSignPostsPlugin.class);
        HandlerList.unregisterAll(plugin);
        server.getPluginManager().registerEvents(new SignpostListener(plugin, true), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("reads the chunk's blocks only when the region scheduler runs the task")
    void defersToTheRegionScheduler() {
        SignpostKey key = new SignpostKey("world", 3, 64, 3);
        plugin.service()
                .place(
                        new Signpost(
                                key, "Gone", UUID.randomUUID(), "Alice", SignpostSource.SIGN, 0L));

        server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(0, 0), false));

        assertThat(scheduled).hasSize(1);
        assertThat(scheduled.get(0).world()).isSameAs(world);
        assertThat(scheduled.get(0).chunkX()).isZero();
        assertThat(scheduled.get(0).chunkZ()).isZero();
        assertThat(plugin.service().registry().get(key)).isPresent();

        scheduled.get(0).task().run();

        assertThat(plugin.service().registry().get(key)).isEmpty();
    }

    @Test
    @DisplayName("skips a chunk that unloaded before the region scheduler ran the task")
    void skipsAChunkThatUnloaded() {
        SignpostKey key = new SignpostKey("world", 3, 64, 3);
        plugin.service()
                .place(
                        new Signpost(
                                key, "Gone", UUID.randomUUID(), "Alice", SignpostSource.SIGN, 0L));
        server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(0, 0), false));
        world.unloadChunk(0, 0);

        scheduled.get(0).task().run();

        assertThat(plugin.service().registry().get(key)).isPresent();
    }

    @Test
    @DisplayName("Folia is detected by its region server class being on the classpath")
    void detectsFoliaByClass() {
        assertThat(SignpostListener.classExists("java.lang.String")).isTrue();
        assertThat(
                        SignpostListener.classExists(
                                "io.papermc.paper.threadedregions.RegionizedServer"))
                .isFalse();
    }

    @Test
    @DisplayName("schedules nothing for a chunk that holds no signposts")
    void ignoresEmptyChunks() {
        server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(5, 5), false));

        assertThat(scheduled).isEmpty();
    }
}
