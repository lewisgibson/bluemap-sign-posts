package com.lewisgibson.bluemapsignposts.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lewisgibson.bluemapsignposts.core.signpost.StorageException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Schema")
class SchemaTest {

    @TempDir Path directory;

    private Connection connection;

    @BeforeEach
    void open() throws SQLException {
        connection = new SqliteConnectionSource(directory.resolve("schema.db")).open();
    }

    @AfterEach
    void close() throws SQLException {
        connection.close();
    }

    @Test
    @DisplayName("a new database starts at version 0 and is built up to the latest")
    void buildsNewDatabase() throws SQLException {
        Schema.migrate(connection, Dialect.SQLITE, "test");
        assertThat(Schema.version(connection)).isEqualTo(Schema.LATEST).isEqualTo(1);
        assertThat(tables()).contains("bmsp_schema", "bmsp_signposts", "bmsp_limit_overrides");
        assertThat(connection.getAutoCommit()).isTrue();

        Schema.migrate(connection, Dialect.SQLITE, "test");
        assertThat(Schema.version(connection)).isEqualTo(Schema.LATEST);
    }

    @Test
    @DisplayName("applies only the migrations after the recorded version, in order")
    void appliesPendingInOrder() throws SQLException {
        Schema.migrate(
                connection, Dialect.SQLITE, "test", List.of(migration("CREATE TABLE a (x)")));
        Schema.migrate(
                connection,
                Dialect.SQLITE,
                "test",
                List.of(
                        migration("CREATE TABLE a (x)"),
                        migration("CREATE TABLE b (x)"),
                        migration("INSERT INTO b VALUES (3)")));
        assertThat(Schema.version(connection)).isEqualTo(3);
        assertThat(tables()).contains("a", "b");
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM bmsp_schema")) {
            rows.next();
            assertThat(rows.getInt(1)).as("one row records the version").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a migration that fails is rolled back and leaves the version where it was")
    void failedMigrationRollsBack() throws SQLException {
        Schema.migrate(
                connection, Dialect.SQLITE, "test", List.of(migration("CREATE TABLE a (x)")));
        List<Function<Dialect, List<String>>> broken =
                List.of(
                        migration("CREATE TABLE a (x)"),
                        dialect -> List.of("CREATE TABLE half (x)", "NOT SQL"));
        assertThatThrownBy(() -> Schema.migrate(connection, Dialect.SQLITE, "test", broken))
                .isInstanceOf(SQLException.class);
        assertThat(Schema.version(connection)).isEqualTo(1);
        assertThat(tables()).doesNotContain("half");
        assertThat(connection.getAutoCommit()).isTrue();
    }

    @Test
    @DisplayName("refuses a database newer than this build, naming it")
    void refusesNewer() throws SQLException {
        Schema.migrate(
                connection,
                Dialect.SQLITE,
                "test",
                List.of(migration("CREATE TABLE a (x)"), migration("CREATE TABLE b (x)")));
        assertThatThrownBy(
                        () ->
                                Schema.migrate(
                                        connection,
                                        Dialect.SQLITE,
                                        "sqlite here",
                                        List.of(migration("CREATE TABLE a (x)"))))
                .isInstanceOf(StorageException.class)
                .hasMessage(
                        "sqlite here holds schema version 2, newer than this plugin understands"
                                + " (1); upgrade the plugin");
    }

    @Test
    @DisplayName("gives MySQL tables a case-sensitive UTF-8 collation, and no other database")
    void tableOptions() {
        List<String> mysql = Schema.MIGRATIONS.get(0).apply(Dialect.MYSQL);
        assertThat(mysql.get(0)).endsWith(" COLLATE=utf8mb4_bin");
        assertThat(mysql.get(2)).endsWith(" COLLATE=utf8mb4_bin");
        assertThat(Schema.MIGRATIONS.get(0).apply(Dialect.POSTGRES).get(0)).endsWith("z))");
    }

    @Test
    @DisplayName("cannot be instantiated")
    void utility() throws Exception {
        Constructor<Schema> constructor = Schema.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThat(constructor.newInstance()).isNotNull();
    }

    private static Function<Dialect, List<String>> migration(String sql) {
        return dialect -> List.of(sql);
    }

    private List<String> tables() throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        List<String> names = new java.util.ArrayList<>();
        try (ResultSet rows = metadata.getTables(null, null, "%", new String[] {"TABLE"})) {
            while (rows.next()) {
                names.add(rows.getString("TABLE_NAME"));
            }
        }
        return names;
    }
}
