package com.lewisgibson.bluemapsignposts.storage;

/**
 * The SQL that differs between the databases the store supports. Everything else the store runs is
 * the same text on all of them.
 *
 * <p>SQLite (3.24 or newer) and PostgreSQL (9.5 or newer) share {@code INSERT ... ON CONFLICT DO
 * UPDATE}. MySQL and MariaDB use {@code ON DUPLICATE KEY UPDATE} with {@code VALUES()}, which MySQL
 * 8.4 still accepts and which is the only form both servers run. {@code REPLACE} is not used
 * because it deletes the row and inserts a new one.
 */
public enum Dialect {

    /** SQLite. */
    SQLITE,

    /** PostgreSQL. */
    POSTGRES,

    /** MySQL and MariaDB. */
    MYSQL;

    private static final String UPSERT_SIGNPOST =
            "INSERT INTO bmsp_signposts"
                    + " (world, x, y, z, label, owner_uuid, owner_name, source, created_millis)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPSERT_LIMIT_OVERRIDE =
            "INSERT INTO bmsp_limit_overrides"
                    + " (player_uuid, player_name, limit_value, actor, at_millis)"
                    + " VALUES (?, ?, ?, ?, ?)";

    /**
     * @return what follows the closing parenthesis of a {@code CREATE TABLE}. On MySQL that is a
     *     case-sensitive UTF-8 collation, so the worlds {@code world} and {@code World} stay two
     *     keys as they are on the server.
     */
    String tableOptions() {
        return this == MYSQL ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin" : "";
    }

    /**
     * @return the insert-or-replace for one signpost, its nine columns bound in table order
     */
    String upsertSignpost() {
        return switch (this) {
            case SQLITE, POSTGRES ->
                    UPSERT_SIGNPOST
                            + " ON CONFLICT (world, x, y, z) DO UPDATE SET"
                            + " label = excluded.label, owner_uuid = excluded.owner_uuid,"
                            + " owner_name = excluded.owner_name, source = excluded.source,"
                            + " created_millis = excluded.created_millis";
            case MYSQL ->
                    UPSERT_SIGNPOST
                            + " ON DUPLICATE KEY UPDATE"
                            + " label = VALUES(label), owner_uuid = VALUES(owner_uuid),"
                            + " owner_name = VALUES(owner_name), source = VALUES(source),"
                            + " created_millis = VALUES(created_millis)";
        };
    }

    /**
     * @return the insert-or-replace for one limit override, its five columns bound in table order
     */
    String upsertLimitOverride() {
        return switch (this) {
            case SQLITE, POSTGRES ->
                    UPSERT_LIMIT_OVERRIDE
                            + " ON CONFLICT (player_uuid) DO UPDATE SET"
                            + " player_name = excluded.player_name,"
                            + " limit_value = excluded.limit_value,"
                            + " actor = excluded.actor, at_millis = excluded.at_millis";
            case MYSQL ->
                    UPSERT_LIMIT_OVERRIDE
                            + " ON DUPLICATE KEY UPDATE"
                            + " player_name = VALUES(player_name),"
                            + " limit_value = VALUES(limit_value),"
                            + " actor = VALUES(actor), at_millis = VALUES(at_millis)";
        };
    }
}
