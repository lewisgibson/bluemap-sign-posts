package com.lewisgibson.bluemapsignposts.bukkit;

import com.lewisgibson.bluemapsignposts.bukkit.bluemap.BlueMapMarkerSink;
import com.lewisgibson.bluemapsignposts.bukkit.command.BlueMapSignPostsCommand;
import com.lewisgibson.bluemapsignposts.bukkit.config.BukkitSettingsCodec;
import com.lewisgibson.bluemapsignposts.bukkit.listener.SignpostListener;
import com.lewisgibson.bluemapsignposts.bukkit.message.BukkitMessenger;
import com.lewisgibson.bluemapsignposts.core.map.MarkerSink;
import com.lewisgibson.bluemapsignposts.core.settings.BlueMapSignPostsSettings;
import com.lewisgibson.bluemapsignposts.core.settings.StorageSettings;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostRegistry;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostService;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostStore;
import com.lewisgibson.bluemapsignposts.storage.SignpostStores;
import com.lewisgibson.bluemapsignposts.storage.StorageWorker;
import de.bluecolored.bluemap.api.BlueMapAPI;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point: loads configuration into the pure domain model, opens the store and loads the
 * registry from it, registers the listener and the command, and hooks BlueMap's enable callback so
 * every stored signpost is re-published each time BlueMap comes up (it keeps no API markers of its
 * own across a restart or a {@code /bluemap reload}).
 */
public class BlueMapSignPostsPlugin extends JavaPlugin {

    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(10);

    private final BukkitMessenger messenger = new BukkitMessenger();
    private final BukkitSettingsCodec codec = new BukkitSettingsCodec();
    private final Consumer<BlueMapAPI> onBlueMapEnable = api -> republish();
    private volatile PluginRuntime runtime;
    // A new worker per enable: drain() on disable shuts the last one down for good, so a plugin
    // manager that disables and re-enables this same instance must not get a dead one back.
    private StorageWorker worker;
    // store and storeSettings are only ever read or written under the plugin's monitor: on Folia a
    // console reload (global region) and a player-issued one (that player's region) run at once.
    private SignpostStore store;
    private StorageSettings storeSettings;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        worker =
                new StorageWorker(
                        failure -> getLogger().log(Level.SEVERE, "A store write failed", failure));
        try {
            reloadSettings();
        } catch (RuntimeException ex) {
            getLogger().severe("Invalid configuration or unusable store: " + ex.getMessage());
            getLogger().severe("Disabling BlueMapSignPosts; fix config.yml and restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new SignpostListener(this), this);

        PluginCommand command =
                Objects.requireNonNull(
                        getCommand("bluemapsignposts"), "plugin.yml declares the command");
        BlueMapSignPostsCommand handler = new BlueMapSignPostsCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        // Fires now if BlueMap is already up, and again after every /bluemap reload.
        BlueMapAPI.onEnable(onBlueMapEnable);
    }

    @Override
    public void onDisable() {
        BlueMapAPI.unregisterListener(onBlueMapEnable);
        warnUnless(
                worker.drain(DRAIN_TIMEOUT),
                getLogger(),
                "Store writes were still pending after " + DRAIN_TIMEOUT);
        closeStore();
    }

    /**
     * Logs {@code warning} when a wait on the storage thread ran out of time. Kept apart so the
     * timeout path, which needs a write stuck for ten seconds to reach for real, can be tested.
     */
    static void warnUnless(boolean finished, Logger logger, String warning) {
        if (!finished) {
            logger.warning(warning);
        }
    }

    private synchronized void closeStore() {
        if (store != null) {
            store.close();
            store = null;
        }
    }

    /**
     * Reloads config.yml from disk and atomically rebuilds the runtime state. The store is reopened
     * and re-read only when its settings changed, so an ordinary reload keeps the same registry.
     *
     * <p>Synchronised, because on Folia the console (global region) and any number of players (each
     * on their own region thread) can run a reload at the same moment, and the config, the store
     * and the runtime swap must not interleave.
     *
     * @throws IllegalArgumentException if the configuration is invalid
     * @throws com.lewisgibson.bluemapsignposts.core.signpost.StorageException if the store cannot
     *     be opened or read, so the caller (startup or the reload command) can report it rather
     *     than run without it
     */
    public synchronized void reloadSettings() {
        reloadConfig();
        BlueMapSignPostsSettings settings = codec.load(getConfig());

        SignpostService service;
        if (store == null || !settings.storage().equals(storeSettings)) {
            SignpostStore opened = SignpostStores.open(settings.storage(), getDataPath());
            SignpostRegistry registry;
            try {
                registry = SignpostRegistry.load(opened);
            } catch (RuntimeException ex) {
                opened.close();
                throw ex;
            }
            if (store != null) {
                warnUnless(
                        worker.flush(DRAIN_TIMEOUT),
                        getLogger(),
                        "Store writes were still pending after "
                                + DRAIN_TIMEOUT
                                + "; some may be lost.");
                store.close();
                // The new store may not hold what the old one did; its markers are re-published
                // by the caller, and the old store's would otherwise stay on the map for good.
                runtime.service().retractAll();
            }
            store = opened;
            storeSettings = settings.storage();
            MarkerSink sink = new BlueMapMarkerSink(getServer(), () -> runtime.settings());
            service = new SignpostService(registry, opened, worker::submit, sink);
            getLogger()
                    .info(
                            "Store: "
                                    + opened.description()
                                    + " ("
                                    + registry.size()
                                    + " signposts, "
                                    + registry.limitOverrides().size()
                                    + " limit overrides)");
        } else {
            service = runtime.service();
        }
        this.runtime = PluginRuntime.from(settings, service);
    }

    private void republish() {
        int published = runtime.service().publishAll();
        getLogger().info("BlueMap is up: published " + published + " signpost(s) to the map.");
    }

    /**
     * @return the current runtime snapshot (settings, policy, parser and service), read atomically
     */
    public PluginRuntime runtime() {
        return runtime;
    }

    /**
     * @return the signpost service that keeps registry, store and map in step
     */
    public SignpostService service() {
        return runtime.service();
    }

    /**
     * @return the shared messenger for rendering and sending player messages
     */
    public BukkitMessenger messenger() {
        return messenger;
    }
}
