package com.lewisgibson.bluemapsignposts.bukkit.bluemap;

import com.lewisgibson.bluemapsignposts.core.map.MarkerContent;
import com.lewisgibson.bluemapsignposts.core.map.MarkerSink;
import com.lewisgibson.bluemapsignposts.core.settings.BlueMapSignPostsSettings;
import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostKey;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Server;
import org.bukkit.World;

/**
 * Shows signposts on BlueMap: one marker set (id {@value #MARKER_SET_ID}) on every map of the
 * signpost's world, one POI marker per signpost keyed by its block.
 *
 * <p>The marker set is created on demand with {@code computeIfAbsent} and its label and toggles are
 * re-applied on every publish, so a {@code /bluemap reload} (which hands back maps with no marker
 * sets) and a config reload (which may rename the layer) both heal on the next publish. BlueMap not
 * being enabled is a no-op, not an error: the plugin re-publishes everything when it comes up.
 */
public final class BlueMapMarkerSink implements MarkerSink {

    /** The marker set id on every map; chosen not to collide with other addons' sets. */
    public static final String MARKER_SET_ID = "signposts";

    private final Server server;
    private final Supplier<BlueMapSignPostsSettings> settings;

    /**
     * @param server the server, to resolve world names to the world objects BlueMap keys on
     * @param settings the current settings, read at publish time so a reload takes effect
     */
    public BlueMapMarkerSink(Server server, Supplier<BlueMapSignPostsSettings> settings) {
        this.server = server;
        this.settings = settings;
    }

    @Override
    public void publish(Signpost signpost) {
        Optional<BlueMapWorld> world = blueMapWorld(signpost.key().world());
        if (world.isEmpty()) {
            return;
        }
        BlueMapSignPostsSettings current = settings.get();
        SignpostKey key = signpost.key();
        for (BlueMapMap map : world.get().getMaps()) {
            MarkerSet set = markerSet(map, current);
            POIMarker marker =
                    POIMarker.builder()
                            .label(signpost.label())
                            .detail(MarkerContent.render(current.markerDetail(), signpost))
                            .position(key.x() + 0.5d, key.y() + 0.5d, key.z() + 0.5d)
                            .defaultIcon()
                            .maxDistance(current.markerMaxDistance())
                            .build();
            set.put(key.markerId(), marker);
        }
    }

    @Override
    public void retract(SignpostKey key) {
        Optional<BlueMapWorld> world = blueMapWorld(key.world());
        if (world.isEmpty()) {
            return;
        }
        for (BlueMapMap map : world.get().getMaps()) {
            MarkerSet set = map.getMarkerSets().get(MARKER_SET_ID);
            if (set != null) {
                set.remove(key.markerId());
            }
        }
    }

    private Optional<BlueMapWorld> blueMapWorld(String worldName) {
        Optional<BlueMapAPI> api = BlueMapAPI.getInstance();
        if (api.isEmpty()) {
            return Optional.empty();
        }
        World world = server.getWorld(worldName);
        if (world == null) {
            return Optional.empty();
        }
        return api.get().getWorld(world);
    }

    private static MarkerSet markerSet(BlueMapMap map, BlueMapSignPostsSettings current) {
        MarkerSet set =
                map.getMarkerSets()
                        .computeIfAbsent(
                                MARKER_SET_ID,
                                id -> MarkerSet.builder().label(current.markerSetLabel()).build());
        set.setLabel(current.markerSetLabel());
        set.setToggleable(current.markerSetToggleable());
        set.setDefaultHidden(current.markerSetDefaultHidden());
        return set;
    }
}
