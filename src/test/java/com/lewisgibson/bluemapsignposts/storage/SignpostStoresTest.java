package com.lewisgibson.bluemapsignposts.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lewisgibson.bluemapsignposts.core.settings.StorageSettings;
import com.lewisgibson.bluemapsignposts.core.settings.StorageType;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostStore;
import com.lewisgibson.bluemapsignposts.core.signpost.StorageException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SignpostStores")
class SignpostStoresTest {

    private static final StorageSettings DEFAULTS = StorageSettings.sqliteDefault();

    @TempDir Path folder;

    @Test
    @DisplayName("opens the SQLite default under the data folder")
    void opensSqlite() {
        SignpostStore store = SignpostStores.open(DEFAULTS, folder);
        assertThat(folder.resolve("data.db")).exists();
        assertThat(store.loadSignposts()).isEmpty();
        assertThat(store.description())
                .isEqualTo("sqlite " + folder.resolve("data.db").toAbsolutePath());
        store.close();
    }

    @Test
    @DisplayName("closes the store again when opening it fails")
    void closesOnFailure() throws Exception {
        try (Connection connection = new SqliteConnectionSource(folder.resolve("data.db")).open();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE bmsp_schema (version INTEGER NOT NULL)");
            statement.execute("INSERT INTO bmsp_schema VALUES (99)");
        }
        assertThatThrownBy(() -> SignpostStores.open(DEFAULTS, folder))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("schema version 99");
    }

    @Test
    @DisplayName("connects to PostgreSQL as configured and fails plainly when it is down")
    void postgresUnreachable() throws Exception {
        int port = closedPort();
        StorageSettings settings =
                new StorageSettings(
                        StorageType.POSTGRES,
                        "data.db",
                        new StorageSettings.Postgres(
                                "127.0.0.1", port, "bmsp", "u", "p", "s", "disable", ""),
                        DEFAULTS.mysql());
        assertThatThrownBy(() -> SignpostStores.open(settings, folder))
                .isInstanceOf(StorageException.class)
                .hasMessageStartingWith(
                        "could not connect (postgres u@127.0.0.1:" + port + "/bmsp schema s");
    }

    @Test
    @DisplayName("connects to MySQL as configured and fails plainly when it is down")
    void mysqlUnreachable() throws Exception {
        int port = closedPort();
        StorageSettings settings =
                new StorageSettings(
                        StorageType.MYSQL,
                        "data.db",
                        DEFAULTS.postgres(),
                        new StorageSettings.MySql(
                                "127.0.0.1", port, "bmsp", "u", "p", "disable", ""));
        assertThatThrownBy(() -> SignpostStores.open(settings, folder))
                .isInstanceOf(StorageException.class)
                .hasMessageStartingWith(
                        "could not connect (mysql u@127.0.0.1:" + port + "/bmsp sslmode disable)");
    }

    @Test
    @DisplayName("rejects null arguments")
    void rejectsNulls() {
        assertThatNullPointerException().isThrownBy(() -> SignpostStores.open(null, folder));
        assertThatNullPointerException().isThrownBy(() -> SignpostStores.open(DEFAULTS, null));
    }

    private static int closedPort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
