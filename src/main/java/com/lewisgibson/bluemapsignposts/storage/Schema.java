package com.lewisgibson.bluemapsignposts.storage;

import com.lewisgibson.bluemapsignposts.core.signpost.StorageException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.function.Function;

/**
 * The store's tables, built by numbered migrations. {@code bmsp_schema} holds the number of the
 * last one applied; on open, every later migration runs in order, each in its own transaction
 * together with the new number, so a migration that fails leaves the number where it was.
 * PostgreSQL and SQLite roll the failed migration's changes back too; MySQL commits each {@code
 * CREATE} or {@code ALTER} as it runs, so a MySQL migration is kept to statements that are safe to
 * run again.
 *
 * <p>A database whose number is higher than this build knows was written by a newer plugin, and is
 * refused rather than read wrongly.
 *
 * <p>A change to the tables is a new migration appended to {@link #MIGRATIONS}, never an edit to
 * one that has shipped.
 */
final class Schema {

    /** Migration {@code n} is at index {@code n - 1}. */
    static final List<Function<Dialect, List<String>>> MIGRATIONS = List.of(Schema::version1);

    /** The schema version this build writes and understands. */
    static final int LATEST = MIGRATIONS.size();

    private Schema() {}

    /**
     * Brings the database up to {@link #LATEST}.
     *
     * @param connection an open connection, returned in the auto-commit mode it had
     * @param dialect the database's dialect
     * @param description the database, for the error message
     * @throws SQLException if a statement fails
     * @throws StorageException if the database is newer than this build
     */
    static void migrate(Connection connection, Dialect dialect, String description)
            throws SQLException {
        migrate(connection, dialect, description, MIGRATIONS);
    }

    static void migrate(
            Connection connection,
            Dialect dialect,
            String description,
            List<Function<Dialect, List<String>>> migrations)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS bmsp_schema (version INTEGER NOT NULL)"
                            + dialect.tableOptions());
        }
        int current = version(connection);
        if (current > migrations.size()) {
            throw new StorageException(
                    description
                            + " holds schema version "
                            + current
                            + ", newer than this plugin understands ("
                            + migrations.size()
                            + "); upgrade the plugin",
                    null);
        }
        for (int version = current + 1; version <= migrations.size(); version++) {
            apply(connection, version, migrations.get(version - 1).apply(dialect));
        }
    }

    /**
     * @return the version recorded in {@code bmsp_schema}, 0 for a new database
     */
    static int version(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT MAX(version) FROM bmsp_schema")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static void apply(Connection connection, int version, List<String> statements)
            throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement();
                PreparedStatement record =
                        connection.prepareStatement(
                                "INSERT INTO bmsp_schema (version) VALUES (?)")) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            statement.execute("DELETE FROM bmsp_schema");
            record.setInt(1, version);
            record.executeUpdate();
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    /** The first schema: one row per signpost block, and at most one limit override per player. */
    private static List<String> version1(Dialect dialect) {
        return List.of(
                "CREATE TABLE bmsp_signposts ("
                        + "world VARCHAR(128) NOT NULL, "
                        + "x INTEGER NOT NULL, "
                        + "y INTEGER NOT NULL, "
                        + "z INTEGER NOT NULL, "
                        + "label VARCHAR(100) NOT NULL, "
                        + "owner_uuid VARCHAR(36) NOT NULL, "
                        + "owner_name VARCHAR(64) NOT NULL, "
                        + "source VARCHAR(16) NOT NULL, "
                        + "created_millis BIGINT NOT NULL, "
                        + "PRIMARY KEY (world, x, y, z))"
                        + dialect.tableOptions(),
                "CREATE INDEX bmsp_signposts_owner ON bmsp_signposts (owner_uuid)",
                "CREATE TABLE bmsp_limit_overrides ("
                        + "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                        + "player_name VARCHAR(64) NOT NULL, "
                        + "limit_value INTEGER NOT NULL, "
                        + "actor VARCHAR(64) NOT NULL, "
                        + "at_millis BIGINT NOT NULL)"
                        + dialect.tableOptions());
    }
}
