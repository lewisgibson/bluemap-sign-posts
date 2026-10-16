package com.lewisgibson.bluemapsignposts.storage;

import com.lewisgibson.bluemapsignposts.core.access.LimitOverride;
import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostKey;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostSource;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostStore;
import com.lewisgibson.bluemapsignposts.core.signpost.StorageException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The one store implementation, over any {@link ConnectionSource}. The SQL is the same on every
 * database except the two upserts, which come from the source's {@link Dialect}, and the tables are
 * built and upgraded by {@link Schema}.
 *
 * <p>Two tables: {@code bmsp_signposts}, one row per block, and {@code bmsp_limit_overrides}, at
 * most one row per player. Timestamps are epoch milliseconds and player ids are text, so the rows
 * read the same in every database.
 */
public final class JdbcSignpostStore implements SignpostStore {

    private final ConnectionSource source;
    private volatile boolean closed;

    /**
     * @param source where to connect; the store closes it when it is closed
     */
    public JdbcSignpostStore(ConnectionSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public void open() {
        try (Connection connection = connection()) {
            Schema.migrate(connection, source.dialect(), source.description());
        } catch (SQLException ex) {
            throw failure("open", ex);
        }
    }

    @Override
    public String description() {
        return source.description();
    }

    @Override
    public List<Signpost> loadSignposts() {
        List<Signpost> signposts = new ArrayList<>();
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT world, x, y, z, label, owner_uuid, owner_name, source,"
                                        + " created_millis FROM bmsp_signposts")) {
            while (rows.next()) {
                signposts.add(readSignpost(rows));
            }
        } catch (SQLException ex) {
            throw failure("read signposts", ex);
        }
        return signposts;
    }

    @Override
    public Map<UUID, LimitOverride> loadLimitOverrides() {
        Map<UUID, LimitOverride> overrides = new HashMap<>();
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT player_uuid, player_name, limit_value, actor, at_millis"
                                        + " FROM bmsp_limit_overrides")) {
            while (rows.next()) {
                LimitOverride override = readLimitOverride(rows);
                overrides.put(override.player(), override);
            }
        } catch (SQLException ex) {
            throw failure("read limit overrides", ex);
        }
        return overrides;
    }

    private Signpost readSignpost(ResultSet rows) throws SQLException {
        String world = rows.getString(1);
        int x = rows.getInt(2);
        int y = rows.getInt(3);
        int z = rows.getInt(4);
        try {
            return new Signpost(
                    new SignpostKey(world, x, y, z),
                    rows.getString(5),
                    UUID.fromString(rows.getString(6)),
                    rows.getString(7),
                    SignpostSource.valueOf(rows.getString(8)),
                    rows.getLong(9));
        } catch (IllegalArgumentException ex) {
            throw new StorageException(
                    "malformed row in bmsp_signposts at "
                            + world
                            + " "
                            + x
                            + " "
                            + y
                            + " "
                            + z
                            + " ("
                            + source.description()
                            + "): "
                            + ex.getMessage(),
                    ex);
        }
    }

    private LimitOverride readLimitOverride(ResultSet rows) throws SQLException {
        String player = rows.getString(1);
        try {
            return new LimitOverride(
                    UUID.fromString(player),
                    rows.getString(2),
                    rows.getInt(3),
                    rows.getString(4),
                    Instant.ofEpochMilli(rows.getLong(5)));
        } catch (IllegalArgumentException ex) {
            throw new StorageException(
                    "malformed row in bmsp_limit_overrides for player "
                            + player
                            + " ("
                            + source.description()
                            + "): "
                            + ex.getMessage(),
                    ex);
        }
    }

    @Override
    public void putSignpost(Signpost signpost) {
        Objects.requireNonNull(signpost, "signpost");
        SignpostKey key = signpost.key();
        try (Connection connection = connection();
                PreparedStatement upsert =
                        connection.prepareStatement(source.dialect().upsertSignpost())) {
            upsert.setString(1, key.world());
            upsert.setInt(2, key.x());
            upsert.setInt(3, key.y());
            upsert.setInt(4, key.z());
            upsert.setString(5, signpost.label());
            upsert.setString(6, signpost.owner().toString());
            upsert.setString(7, signpost.ownerName());
            upsert.setString(8, signpost.source().name());
            upsert.setLong(9, signpost.createdAtEpochMillis());
            upsert.executeUpdate();
        } catch (SQLException ex) {
            throw failure("write signpost", ex);
        }
    }

    @Override
    public void removeSignpost(SignpostKey key) {
        Objects.requireNonNull(key, "key");
        try (Connection connection = connection();
                PreparedStatement delete =
                        connection.prepareStatement(
                                "DELETE FROM bmsp_signposts"
                                        + " WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            delete.setString(1, key.world());
            delete.setInt(2, key.x());
            delete.setInt(3, key.y());
            delete.setInt(4, key.z());
            delete.executeUpdate();
        } catch (SQLException ex) {
            throw failure("remove signpost", ex);
        }
    }

    @Override
    public void putLimitOverride(LimitOverride override) {
        Objects.requireNonNull(override, "override");
        try (Connection connection = connection();
                PreparedStatement upsert =
                        connection.prepareStatement(source.dialect().upsertLimitOverride())) {
            upsert.setString(1, override.player().toString());
            upsert.setString(2, override.playerName());
            upsert.setInt(3, override.limit());
            upsert.setString(4, override.actor());
            upsert.setLong(5, override.at().toEpochMilli());
            upsert.executeUpdate();
        } catch (SQLException ex) {
            throw failure("write limit override", ex);
        }
    }

    @Override
    public void removeLimitOverride(UUID player) {
        Objects.requireNonNull(player, "player");
        try (Connection connection = connection();
                PreparedStatement delete =
                        connection.prepareStatement(
                                "DELETE FROM bmsp_limit_overrides WHERE player_uuid = ?")) {
            delete.setString(1, player.toString());
            delete.executeUpdate();
        } catch (SQLException ex) {
            throw failure("remove limit override", ex);
        }
    }

    /** Closes the connection source; the store cannot be used again. Idempotent. */
    @Override
    public void close() {
        closed = true;
        source.close();
    }

    private Connection connection() throws SQLException {
        if (closed) {
            throw new SQLException("the store is closed");
        }
        return source.open();
    }

    private StorageException failure(String operation, SQLException cause) {
        return new StorageException(
                "could not " + operation + " (" + source.description() + "): " + cause.getMessage(),
                cause);
    }
}
