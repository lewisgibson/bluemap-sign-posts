package com.lewisgibson.bluemapsignposts.storage;

import static com.lewisgibson.bluemapsignposts.Fixtures.ALICE;
import static com.lewisgibson.bluemapsignposts.Fixtures.BOB;
import static com.lewisgibson.bluemapsignposts.Fixtures.override;
import static com.lewisgibson.bluemapsignposts.Fixtures.signpost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostKey;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostSource;
import com.lewisgibson.bluemapsignposts.core.signpost.StorageException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("JdbcSignpostStore")
class JdbcSignpostStoreTest {

    @TempDir Path directory;

    /** The store contract, run once per kind of connection source. */
    abstract class Contract {

        ConnectionSource source;
        JdbcSignpostStore store;

        abstract ConnectionSource source(Path file);

        @BeforeEach
        void open() {
            source = source(directory.resolve("nested/data.db"));
            store = new JdbcSignpostStore(source);
            store.open();
        }

        @AfterEach
        void close() {
            store.close();
        }

        @Test
        @DisplayName("creates the tables, and opening again changes nothing")
        void createsSchema() throws SQLException {
            assertThat(directory.resolve("nested/data.db")).exists();
            store.open();
            assertThat(store.loadSignposts()).isEmpty();
            assertThat(store.loadLimitOverrides()).isEmpty();
            try (Connection connection = source.open()) {
                assertThat(Schema.version(connection)).isEqualTo(Schema.LATEST);
            }
        }

        @Test
        @DisplayName("refuses a database written by a newer plugin")
        void refusesNewerSchema() throws SQLException {
            execute("UPDATE bmsp_schema SET version = 99");
            assertThatThrownBy(store::open)
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("schema version 99")
                    .hasMessageContaining("upgrade the plugin");
        }

        @Test
        @DisplayName("stores, replaces, reloads and removes signposts, keyed by block")
        void signposts() {
            Signpost farm = signpost("world", 1, 64, -3, "Farm & <shop>", ALICE);
            Signpost portal =
                    new Signpost(
                            new SignpostKey("world_nether", -100, 30, 7),
                            "Portal",
                            BOB,
                            "Bob",
                            SignpostSource.BANNER,
                            5L);
            store.putSignpost(farm);
            store.putSignpost(portal);
            assertThat(store.loadSignposts()).containsExactlyInAnyOrder(farm, portal);

            Signpost renamed = signpost("world", 1, 64, -3, "Iron farm", BOB);
            store.putSignpost(renamed);
            assertThat(store.loadSignposts()).containsExactlyInAnyOrder(renamed, portal);

            store.removeSignpost(farm.key());
            store.removeSignpost(farm.key());
            assertThat(store.loadSignposts()).containsExactly(portal);
        }

        @Test
        @DisplayName("keeps worlds whose names differ only in case apart")
        void worldNamesAreCaseSensitive() {
            Signpost lower = signpost("world", 0, 64, 0, "Lower", ALICE);
            Signpost upper = signpost("World", 0, 64, 0, "Upper", ALICE);
            store.putSignpost(lower);
            store.putSignpost(upper);
            assertThat(store.loadSignposts()).containsExactlyInAnyOrder(lower, upper);
        }

        @Test
        @DisplayName("stores, replaces, reloads and removes limit overrides")
        void limitOverrides() {
            store.putLimitOverride(override(ALICE, 3));
            store.putLimitOverride(override(BOB, 0));
            assertThat(store.loadLimitOverrides()).containsOnlyKeys(ALICE, BOB);
            assertThat(store.loadLimitOverrides().get(ALICE)).isEqualTo(override(ALICE, 3));

            store.putLimitOverride(override(ALICE, 25));
            assertThat(store.loadLimitOverrides().get(ALICE)).isEqualTo(override(ALICE, 25));

            store.removeLimitOverride(ALICE);
            store.removeLimitOverride(ALICE);
            assertThat(store.loadLimitOverrides()).containsOnlyKeys(BOB);
        }

        @Test
        @DisplayName("a malformed signpost row fails loading, naming the table and the block")
        void malformedSignpostRow() throws SQLException {
            execute(
                    "INSERT INTO bmsp_signposts VALUES ('world', 1, 64, -3, 'Farm', 'not-a-uuid',"
                            + " 'Alice', 'SIGN', 0)");
            assertThatThrownBy(() -> store.loadSignposts())
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("bmsp_signposts")
                    .hasMessageContaining("world 1 64 -3");
        }

        @Test
        @DisplayName("a malformed limit override row fails loading, naming the player")
        void malformedLimitOverrideRow() throws SQLException {
            execute("INSERT INTO bmsp_limit_overrides VALUES ('nope', 'Alice', 3, 'console', 0)");
            assertThatThrownBy(() -> store.loadLimitOverrides())
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("bmsp_limit_overrides")
                    .hasMessageContaining("nope");
        }

        @Test
        @DisplayName("describes itself as its source does")
        void description() {
            assertThat(store.description()).isEqualTo(source.description());
        }

        @Test
        @DisplayName("refuses every operation once closed, for good")
        void closed() {
            store.close();
            store.close();
            assertThatThrownBy(() -> store.loadSignposts())
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("closed");
            assertThatThrownBy(store::open)
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("closed");
        }

        void execute(String sql) throws SQLException {
            try (Connection connection = source.open();
                    Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    @Nested
    @DisplayName("on a SQLite file")
    class OnSqlite extends Contract {

        @Override
        ConnectionSource source(Path file) {
            return new SqliteConnectionSource(file);
        }
    }

    /**
     * The same contract through the connection pool, with the PostgreSQL dialect (whose SQL SQLite
     * shares), so the pooled path is exercised without a database server.
     */
    @Nested
    @DisplayName("through the connection pool")
    class ThroughThePool extends Contract {

        @Override
        ConnectionSource source(Path file) {
            try {
                Files.createDirectories(file.getParent());
            } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
            return PooledConnectionSource.start(
                    Dialect.POSTGRES,
                    "pooled " + file,
                    new DriverDataSource(
                            new org.sqlite.JDBC(), "jdbc:sqlite:" + file, new Properties()));
        }
    }

    @Nested
    @DisplayName("when the database cannot be reached")
    class Unreachable {

        private final ConnectionSource broken =
                new ConnectionSource() {
                    @Override
                    public Connection open() throws SQLException {
                        throw new SQLException("no such host");
                    }

                    @Override
                    public Dialect dialect() {
                        return Dialect.POSTGRES;
                    }

                    @Override
                    public String description() {
                        return "broken";
                    }

                    @Override
                    public void close() {}
                };

        private final JdbcSignpostStore unreachable = new JdbcSignpostStore(broken);

        @Test
        @DisplayName("every operation fails with a StorageException naming the source")
        void everyOperationFails() {
            assertFails(unreachable::open, "open");
            assertFails(unreachable::loadSignposts, "read signposts");
            assertFails(unreachable::loadLimitOverrides, "read limit overrides");
            assertFails(
                    () -> unreachable.putSignpost(signpost("world", 1, 64, 1, "A", ALICE)),
                    "write signpost");
            assertFails(
                    () -> unreachable.removeSignpost(new SignpostKey("world", 1, 64, 1)),
                    "remove signpost");
            assertFails(
                    () -> unreachable.putLimitOverride(override(ALICE, 1)), "write limit override");
            assertFails(() -> unreachable.removeLimitOverride(ALICE), "remove limit override");
        }

        private void assertFails(Runnable operation, String what) {
            assertThatThrownBy(operation::run)
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("could not " + what)
                    .hasMessageContaining("(broken)")
                    .hasMessageContaining("no such host");
        }
    }

    @Test
    @DisplayName("rejects null arguments")
    void rejectsNulls() {
        JdbcSignpostStore store =
                new JdbcSignpostStore(new SqliteConnectionSource(directory.resolve("data.db")));
        assertThatNullPointerException().isThrownBy(() -> store.putSignpost(null));
        assertThatNullPointerException().isThrownBy(() -> store.removeSignpost(null));
        assertThatNullPointerException().isThrownBy(() -> store.putLimitOverride(null));
        assertThatNullPointerException().isThrownBy(() -> store.removeLimitOverride(null));
        assertThatNullPointerException().isThrownBy(() -> new JdbcSignpostStore(null));
        assertThatNullPointerException().isThrownBy(() -> new SqliteConnectionSource(null));
    }

    @Test
    @DisplayName("a SQLite file whose folder cannot be created fails with a SQLException")
    void unwritableParent() throws Exception {
        Path file = directory.resolve("file");
        Files.writeString(file, "not a directory");
        SqliteConnectionSource bad = new SqliteConnectionSource(file.resolve("data.db"));
        assertThatThrownBy(bad::open)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("cannot create");
    }

    @Test
    @DisplayName("a SQLite source speaks SQLite, describes its file and holds nothing open")
    void sqliteSource() throws SQLException {
        SqliteConnectionSource sqlite = new SqliteConnectionSource(directory.resolve("data.db"));
        assertThat(sqlite.dialect()).isEqualTo(Dialect.SQLITE);
        assertThat(sqlite.description())
                .isEqualTo("sqlite " + directory.resolve("data.db").toAbsolutePath());
        sqlite.close();
        try (Connection connection = sqlite.open();
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery("PRAGMA busy_timeout")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(5000);
        }
    }
}
