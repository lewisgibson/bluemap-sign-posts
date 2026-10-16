package com.lewisgibson.bluemapsignposts.bukkit.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.lewisgibson.bluemapsignposts.core.message.Messages;
import com.lewisgibson.bluemapsignposts.core.settings.BlueMapSignPostsSettings;
import com.lewisgibson.bluemapsignposts.core.settings.StorageSettings;
import com.lewisgibson.bluemapsignposts.core.settings.StorageType;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The codec against the config.yml the jar ships. Every setting is read with an explicit fallback,
 * which stops Bukkit consulting the shipped file, so each fallback must equal the shipped value or
 * a config.yml that leaves a key out would behave differently from a fresh one.
 */
@DisplayName("BukkitSettingsCodec")
class BukkitSettingsCodecIntegrationTest {

    private final BukkitSettingsCodec codec = new BukkitSettingsCodec();

    @Test
    @DisplayName("every fallback equals the value config.yml ships with")
    void fallbacksMatchTheShippedFile() throws Exception {
        BlueMapSignPostsSettings shipped = codec.load(shipped());
        BlueMapSignPostsSettings empty = codec.load(new YamlConfiguration());

        assertThat(empty).usingRecursiveComparison().ignoringFields("messages").isEqualTo(shipped);
        assertThat(shipped.markerDetail()).isEqualTo("%label%<br><small>placed by %owner%</small>");
    }

    @Test
    @DisplayName("reads every PostgreSQL and MySQL setting")
    void readsServerSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage.type", "mariadb");
        config.set("storage.postgres.host", "pg");
        config.set("storage.postgres.port", 6543);
        config.set("storage.postgres.database", "pgdb");
        config.set("storage.postgres.user", "pgu");
        config.set("storage.postgres.password", "pgp");
        config.set("storage.postgres.schema", "plugins");
        config.set("storage.postgres.ssl-mode", "verify-full");
        config.set("storage.postgres.ssl-root-cert", "/pg.crt");
        config.set("storage.mysql.host", "my");
        config.set("storage.mysql.port", 3307);
        config.set("storage.mysql.database", "mydb");
        config.set("storage.mysql.user", "myu");
        config.set("storage.mysql.password", "myp");
        config.set("storage.mysql.ssl-mode", "verify-ca");
        config.set("storage.mysql.ssl-ca-cert", "/my.crt");

        StorageSettings storage = codec.load(config).storage();

        assertThat(storage.type()).isEqualTo(StorageType.MYSQL);
        assertThat(storage.postgres())
                .isEqualTo(
                        new StorageSettings.Postgres(
                                "pg",
                                6543,
                                "pgdb",
                                "pgu",
                                "pgp",
                                "plugins",
                                "verify-full",
                                "/pg.crt"));
        assertThat(storage.mysql())
                .isEqualTo(
                        new StorageSettings.MySql(
                                "my", 3307, "mydb", "myu", "myp", "verify-ca", "/my.crt"));
    }

    @Test
    @DisplayName("a message missing with no shipped file behind it is blank, which means silent")
    void missingMessageIsBlank() {
        Messages messages = codec.load(new YamlConfiguration()).messages();
        assertThat(messages.created()).isEmpty();
        assertThat(messages.emptyLabel()).isEmpty();
    }

    private static YamlConfiguration shipped() throws Exception {
        try (InputStream in =
                BukkitSettingsCodecIntegrationTest.class.getResourceAsStream("/config.yml")) {
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }
}
