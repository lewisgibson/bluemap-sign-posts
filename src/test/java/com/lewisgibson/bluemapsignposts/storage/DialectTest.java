package com.lewisgibson.bluemapsignposts.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The SQLite and PostgreSQL text runs in the store tests; the MySQL text runs against real MySQL
 * and MariaDB servers in the server tests. Here each is pinned down as written.
 */
@DisplayName("Dialect")
class DialectTest {

    @Test
    @DisplayName("SQLite and PostgreSQL share ON CONFLICT upserts")
    void onConflict() {
        assertThat(Dialect.SQLITE.upsertSignpost())
                .isEqualTo(Dialect.POSTGRES.upsertSignpost())
                .isEqualTo(
                        "INSERT INTO bmsp_signposts (world, x, y, z, label, owner_uuid, owner_name,"
                                + " source, created_millis) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON"
                                + " CONFLICT (world, x, y, z) DO UPDATE SET label = excluded.label,"
                                + " owner_uuid = excluded.owner_uuid, owner_name ="
                                + " excluded.owner_name, source = excluded.source, created_millis ="
                                + " excluded.created_millis");
        assertThat(Dialect.SQLITE.upsertLimitOverride())
                .isEqualTo(Dialect.POSTGRES.upsertLimitOverride())
                .isEqualTo(
                        "INSERT INTO bmsp_limit_overrides (player_uuid, player_name, limit_value,"
                                + " actor, at_millis) VALUES (?, ?, ?, ?, ?) ON CONFLICT"
                                + " (player_uuid) DO UPDATE SET player_name ="
                                + " excluded.player_name, limit_value = excluded.limit_value,"
                                + " actor = excluded.actor, at_millis = excluded.at_millis");
    }

    @Test
    @DisplayName("MySQL uses ON DUPLICATE KEY UPDATE, never REPLACE")
    void onDuplicateKey() {
        assertThat(Dialect.MYSQL.upsertSignpost())
                .isEqualTo(
                        "INSERT INTO bmsp_signposts (world, x, y, z, label, owner_uuid, owner_name,"
                                + " source, created_millis) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON"
                                + " DUPLICATE KEY UPDATE label = VALUES(label), owner_uuid ="
                                + " VALUES(owner_uuid), owner_name = VALUES(owner_name), source ="
                                + " VALUES(source), created_millis = VALUES(created_millis)");
        assertThat(Dialect.MYSQL.upsertLimitOverride())
                .isEqualTo(
                        "INSERT INTO bmsp_limit_overrides (player_uuid, player_name, limit_value,"
                                + " actor, at_millis) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY"
                                + " UPDATE player_name = VALUES(player_name), limit_value ="
                                + " VALUES(limit_value), actor = VALUES(actor), at_millis ="
                                + " VALUES(at_millis)");
    }

    @Test
    @DisplayName("only MySQL tables carry options")
    void tableOptions() {
        assertThat(Dialect.SQLITE.tableOptions()).isEmpty();
        assertThat(Dialect.POSTGRES.tableOptions()).isEmpty();
        assertThat(Dialect.MYSQL.tableOptions())
                .isEqualTo(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
    }
}
