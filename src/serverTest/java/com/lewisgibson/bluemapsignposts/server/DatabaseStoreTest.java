package com.lewisgibson.bluemapsignposts.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lewisgibson.bluemapsignposts.core.access.LimitOverride;
import com.lewisgibson.bluemapsignposts.core.settings.StorageSettings;
import com.lewisgibson.bluemapsignposts.core.settings.StorageType;
import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostKey;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostSource;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostStore;
import com.lewisgibson.bluemapsignposts.core.signpost.StorageException;
import com.lewisgibson.bluemapsignposts.storage.DataSources;
import com.lewisgibson.bluemapsignposts.storage.SignpostStores;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The store contract against real database servers: the same operations the SQLite unit test
 * proves, so the SQL each dialect sends is shown to work on the server it is written for, through
 * the same connection pool the plugin uses.
 */
@DisplayName("The store on real database servers")
class DatabaseStoreTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Path WORK = Path.of(System.getProperty("bmsp.workDir"));
    private static final Instant T1 = Instant.parse("2026-09-10T12:00:00Z");
    private static final Path FOLDER = WORK.resolve("store");

    @BeforeAll
    static void requireDocker() {
        Docker.requireDocker();
    }

    @Test
    @DisplayName("PostgreSQL, in a named schema, without TLS")
    void postgres() throws Exception {
        try (PostgresServer postgres = new PostgresServer()) {
            postgres.start();
            postgres.query("CREATE SCHEMA plugins");
            StorageSettings settings = postgres(postgres, "plugins", "disable", "");
            contract(
                    settings,
                    sql -> postgres.query(sql.replace("bmsp_schema", "plugins.bmsp_schema")));
        }
    }

    @Test
    @DisplayName("PostgreSQL over TLS: verify-full against the server's CA works, plain is refused")
    void postgresTls() throws Exception {
        try (PostgresServer postgres = new PostgresServer()) {
            postgres.start();
            postgres.requireTls(WORK.resolve("postgres-store-tls"));
            String ca = postgres.caCertificate().toString();

            // pg_hba rejects every TCP connection without TLS, so getting through proves TLS.
            SignpostStore store =
                    SignpostStores.open(postgres(postgres, "", "verify-full", ca), FOLDER);
            Signpost spawn = signpost("world", 0, "Spawn");
            store.putSignpost(spawn);
            assertThat(store.loadSignposts()).containsExactly(spawn);
            store.close();

            assertThatThrownBy(
                            () ->
                                    SignpostStores.open(
                                            postgres(postgres, "", "disable", ""), FOLDER))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("could not connect");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(MySqlServer.Flavour.class)
    @DisplayName("MySQL and MariaDB, without TLS")
    void mysql(MySqlServer.Flavour flavour) throws Exception {
        try (MySqlServer server = new MySqlServer(flavour)) {
            server.start();
            contract(mysql(server, "disable"), server::query);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(MySqlServer.Flavour.class)
    @DisplayName("MySQL and MariaDB over TLS")
    void mysqlTls(MySqlServer.Flavour flavour) throws Exception {
        try (MySqlServer server = new MySqlServer(flavour)) {
            server.start();
            StorageSettings settings = mysql(server, "trust");
            try (Connection connection = DataSources.mysql(settings.mysql()).getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rows =
                            statement.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'")) {
                rows.next();
                assertThat(rows.getString(2)).as("the TLS cipher in use").isNotBlank();
            }
            SignpostStore store = SignpostStores.open(settings, FOLDER);
            Signpost spawn = signpost("world", 0, "Spawn");
            store.putSignpost(spawn);
            assertThat(store.loadSignposts()).containsExactly(spawn);
            store.close();
        }
    }

    /**
     * Opens the store twice, writes, replaces and removes rows of both kinds, keeps two worlds
     * whose names differ only in case apart, then refuses a schema newer than the plugin.
     */
    private static void contract(StorageSettings settings, Consumer<String> sql) {
        SignpostStore store = SignpostStores.open(settings, FOLDER);
        store.close();
        store = SignpostStores.open(settings, FOLDER);
        assertThat(store.loadSignposts()).isEmpty();

        Signpost farm = signpost("world", 1, "Farm");
        Signpost renamed =
                new Signpost(farm.key(), "Iron farm", STEVE, "Steve", SignpostSource.BANNER, 6L);
        Signpost otherWorld = signpost("World", 1, "Other world");
        store.putSignpost(farm);
        store.putSignpost(renamed);
        store.putSignpost(otherWorld);
        assertThat(store.loadSignposts()).containsExactlyInAnyOrder(renamed, otherWorld);
        store.removeSignpost(farm.key());
        assertThat(store.loadSignposts()).containsExactly(otherWorld);

        store.putLimitOverride(new LimitOverride(STEVE, "Steve", 3, "Alex", T1));
        store.putLimitOverride(new LimitOverride(STEVE, "Steve", 0, "Bob", T1));
        assertThat(store.loadLimitOverrides().get(STEVE))
                .isEqualTo(new LimitOverride(STEVE, "Steve", 0, "Bob", T1));
        store.removeLimitOverride(STEVE);
        assertThat(store.loadLimitOverrides()).isEmpty();
        store.close();

        sql.accept("UPDATE bmsp_schema SET version = 99");
        assertThatThrownBy(() -> SignpostStores.open(settings, FOLDER))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("schema version 99");
    }

    private static Signpost signpost(String world, int x, String label) {
        return new Signpost(
                new SignpostKey(world, x, 64, -3), label, STEVE, "Steve", SignpostSource.SIGN, 5L);
    }

    private static StorageSettings postgres(
            PostgresServer postgres, String schema, String sslMode, String sslRootCert) {
        StorageSettings defaults = StorageSettings.sqliteDefault();
        return new StorageSettings(
                StorageType.POSTGRES,
                defaults.sqliteFile(),
                new StorageSettings.Postgres(
                        "127.0.0.1",
                        postgres.hostPort(),
                        PostgresServer.DATABASE,
                        PostgresServer.USER,
                        PostgresServer.PASSWORD,
                        schema,
                        sslMode,
                        sslRootCert),
                defaults.mysql());
    }

    private static StorageSettings mysql(MySqlServer server, String sslMode) {
        StorageSettings defaults = StorageSettings.sqliteDefault();
        return new StorageSettings(
                StorageType.MYSQL,
                defaults.sqliteFile(),
                defaults.postgres(),
                new StorageSettings.MySql(
                        "127.0.0.1",
                        server.hostPort(),
                        MySqlServer.DATABASE,
                        MySqlServer.USER,
                        MySqlServer.PASSWORD,
                        sslMode,
                        ""));
    }
}
