package com.lewisgibson.bluemapsignposts.storage;

import com.lewisgibson.bluemapsignposts.core.settings.StorageSettings;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostStore;
import java.nio.file.Path;
import java.util.Objects;

/** Builds and opens the store the settings name. */
public final class SignpostStores {

    private SignpostStores() {}

    /**
     * @param settings which backend and where
     * @param dataFolder the plugin's data folder, which the SQLite file lives under
     * @return an open store with its tables up to date
     * @throws com.lewisgibson.bluemapsignposts.core.signpost.StorageException if it cannot be
     *     opened; nothing is left open behind it
     */
    public static SignpostStore open(StorageSettings settings, Path dataFolder) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        JdbcSignpostStore store = new JdbcSignpostStore(source(settings, dataFolder));
        try {
            store.open();
        } catch (RuntimeException ex) {
            store.close();
            throw ex;
        }
        return store;
    }

    private static ConnectionSource source(StorageSettings settings, Path dataFolder) {
        return switch (settings.type()) {
            case SQLITE -> new SqliteConnectionSource(dataFolder.resolve(settings.sqliteFile()));
            case POSTGRES ->
                    PooledConnectionSource.start(
                            Dialect.POSTGRES,
                            DataSources.describe(settings.postgres()),
                            DataSources.postgres(settings.postgres()));
            case MYSQL ->
                    PooledConnectionSource.start(
                            Dialect.MYSQL,
                            DataSources.describe(settings.mysql()),
                            DataSources.mysql(settings.mysql()));
        };
    }
}
