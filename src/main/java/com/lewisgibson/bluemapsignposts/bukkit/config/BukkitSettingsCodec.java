package com.lewisgibson.bluemapsignposts.bukkit.config;

import com.lewisgibson.bluemapsignposts.core.message.Messages;
import com.lewisgibson.bluemapsignposts.core.settings.BlueMapSignPostsSettings;
import com.lewisgibson.bluemapsignposts.core.settings.StorageSettings;
import com.lewisgibson.bluemapsignposts.core.settings.StorageType;
import com.lewisgibson.bluemapsignposts.core.time.PlaytimeDuration;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Translates the on-disk {@link FileConfiguration} into the immutable {@link
 * BlueMapSignPostsSettings} domain model.
 *
 * <p>The messages are read WITHOUT an explicit default: an explicit default stops Bukkit consulting
 * the jar's own config.yml, so a config file that omits a message would silence it. Read bare, a
 * missing key means the shipped line and only a blank value means "say nothing".
 */
public final class BukkitSettingsCodec {

    /**
     * Reads settings from {@code config}.
     *
     * @param config the plugin configuration
     * @return the resolved settings
     * @throws IllegalArgumentException if a configured value is invalid (a blank trigger, a bad
     *     duration, a negative limit, an unknown storage type or TLS mode), so the plugin surfaces
     *     the error instead of running with it
     */
    public BlueMapSignPostsSettings load(FileConfiguration config) {
        long requiredPlaytime =
                PlaytimeDuration.parseTicks(config.getString("required-playtime", "0s"));
        Messages messages =
                new Messages(
                        message(config, "created"),
                        message(config, "updated"),
                        message(config, "removed"),
                        message(config, "denied-permission"),
                        message(config, "denied-playtime"),
                        message(config, "denied-limit"),
                        message(config, "empty-label"));
        StorageSettings defaults = StorageSettings.sqliteDefault();
        StorageSettings storage =
                new StorageSettings(
                        StorageType.fromToken(
                                config.getString("storage.type", defaults.type().token())),
                        config.getString("storage.sqlite.file", defaults.sqliteFile()),
                        postgres(config, defaults.postgres()),
                        mysql(config, defaults.mysql()));
        return new BlueMapSignPostsSettings(
                config.getString("sign.trigger", "[map]"),
                config.getBoolean("banners.enabled", true),
                requiredPlaytime,
                config.getInt("limits.default", 10),
                config.getBoolean("limits.operators-unlimited", true),
                config.getString("create-permission", "bluemapsignposts.create"),
                config.getString("bypass-permission", "bluemapsignposts.bypass"),
                config.getString("marker-set.label", "Signposts"),
                config.getBoolean("marker-set.toggleable", true),
                config.getBoolean("marker-set.default-hidden", false),
                config.getDouble("marker.max-distance", 100000.0d),
                config.getString("marker.detail", "%label%<br><small>placed by %owner%</small>"),
                storage,
                messages);
    }

    private static StorageSettings.Postgres postgres(
            FileConfiguration config, StorageSettings.Postgres defaults) {
        return new StorageSettings.Postgres(
                config.getString("storage.postgres.host", defaults.host()),
                config.getInt("storage.postgres.port", defaults.port()),
                config.getString("storage.postgres.database", defaults.database()),
                config.getString("storage.postgres.user", defaults.user()),
                config.getString("storage.postgres.password", defaults.password()),
                config.getString("storage.postgres.schema", defaults.schema()),
                config.getString("storage.postgres.ssl-mode", defaults.sslMode()),
                config.getString("storage.postgres.ssl-root-cert", defaults.sslRootCert()));
    }

    private static StorageSettings.MySql mysql(
            FileConfiguration config, StorageSettings.MySql defaults) {
        return new StorageSettings.MySql(
                config.getString("storage.mysql.host", defaults.host()),
                config.getInt("storage.mysql.port", defaults.port()),
                config.getString("storage.mysql.database", defaults.database()),
                config.getString("storage.mysql.user", defaults.user()),
                config.getString("storage.mysql.password", defaults.password()),
                config.getString("storage.mysql.ssl-mode", defaults.sslMode()),
                config.getString("storage.mysql.ssl-ca-cert", defaults.sslCaCert()));
    }

    private static String message(FileConfiguration config, String key) {
        String value = config.getString("messages." + key);
        return value == null ? "" : value;
    }
}
