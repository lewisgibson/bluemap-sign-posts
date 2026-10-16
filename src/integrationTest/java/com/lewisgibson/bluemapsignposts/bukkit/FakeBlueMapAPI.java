package com.lewisgibson.bluemapsignposts.bukkit;

import de.bluecolored.bluemap.api.AssetStorage;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.RenderManager;
import de.bluecolored.bluemap.api.WebApp;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.plugin.Plugin;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.World;

/**
 * A stand-in for BlueMap in the integration tests: one map per named world, with a real, mutable
 * marker-set map exactly as BlueMap exposes it. Registering it fires every {@code
 * BlueMapAPI.onEnable} consumer the same way BlueMap's own enable does, so the plugin's re-publish
 * path is exercised for real.
 */
@SuppressWarnings("removal")
final class FakeBlueMapAPI extends BlueMapAPI {

    private final Map<String, FakeWorld> worlds = new LinkedHashMap<>();

    FakeBlueMapAPI(String... worldNames) {
        for (String name : worldNames) {
            worlds.put(name, new FakeWorld(name));
        }
    }

    void enable() throws Exception {
        registerInstance(this);
    }

    void disable() throws Exception {
        unregisterInstance(this);
    }

    /** The one map backing the named world. */
    FakeMap map(String worldName) {
        return worlds.get(worldName).map;
    }

    /** The plugin's marker set on the named world's map, or {@code null} if it never registered. */
    MarkerSet signposts(String worldName) {
        return map(worldName).getMarkerSets().get("signposts");
    }

    @Override
    public RenderManager getRenderManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WebApp getWebApp() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Plugin getPlugin() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<BlueMapMap> getMaps() {
        List<BlueMapMap> maps = new ArrayList<>();
        for (FakeWorld world : worlds.values()) {
            maps.add(world.map);
        }
        return maps;
    }

    @Override
    public Collection<BlueMapWorld> getWorlds() {
        return new ArrayList<>(worlds.values());
    }

    @Override
    public Optional<BlueMapWorld> getWorld(Object world) {
        if (world instanceof World bukkitWorld) {
            return Optional.ofNullable(worlds.get(bukkitWorld.getName()));
        }
        return Optional.empty();
    }

    @Override
    public Optional<BlueMapMap> getMap(String id) {
        for (FakeWorld world : worlds.values()) {
            if (world.map.getId().equals(id)) {
                return Optional.of(world.map);
            }
        }
        return Optional.empty();
    }

    @Override
    public String getBlueMapVersion() {
        return "fake";
    }

    static final class FakeWorld implements BlueMapWorld {
        private final String id;
        private final FakeMap map;

        FakeWorld(String id) {
            this.id = id;
            this.map = new FakeMap(this, id);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Path getSaveFolder() {
            return Path.of("fake", id);
        }

        @Override
        public Collection<BlueMapMap> getMaps() {
            return List.of(map);
        }
    }

    static final class FakeMap implements BlueMapMap {
        private final FakeWorld world;
        private final String id;
        private final Map<String, MarkerSet> markerSets = new LinkedHashMap<>();
        private boolean frozen;
        private Predicate<com.flowpowered.math.vector.Vector2i> tileFilter = tile -> true;

        FakeMap(FakeWorld world, String id) {
            this.world = world;
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return id;
        }

        @Override
        public BlueMapWorld getWorld() {
            return world;
        }

        @Override
        public AssetStorage getAssetStorage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, MarkerSet> getMarkerSets() {
            return markerSets;
        }

        @Override
        public com.flowpowered.math.vector.Vector2i getTileSize() {
            return com.flowpowered.math.vector.Vector2i.from(32, 32);
        }

        @Override
        public com.flowpowered.math.vector.Vector2i getTileOffset() {
            return com.flowpowered.math.vector.Vector2i.ZERO;
        }

        @Override
        public void setFrozen(boolean frozen) {
            this.frozen = frozen;
        }

        @Override
        public boolean isFrozen() {
            return frozen;
        }

        @Override
        public void setTileFilter(Predicate<com.flowpowered.math.vector.Vector2i> filter) {
            this.tileFilter = filter;
        }

        @Override
        public Predicate<com.flowpowered.math.vector.Vector2i> getTileFilter() {
            return tileFilter;
        }
    }
}
