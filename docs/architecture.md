# Architecture

The plugin separates decisions from server integration and storage. Contributor rules live in [.claude/rules/](../.claude/rules/).

## The three packages

Code under `com.lewisgibson.bluemapsignposts` has three packages. Review enforces their import boundaries.

| Package   | Responsibility                                                                                                                            | May import                                                 |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- |
| `core`    | Sign parsing, signpost model, registry, service, store interface, limits, create policy, durations, marker content, settings and messages | The JDK only                                               |
| `storage` | The JDBC store, its SQL dialects and schema migrations, the connection sources and pool, and the storage thread                           | The JDK, `java.sql`, `core`, HikariCP and the JDBC drivers |
| `bukkit`  | Plugin entry point, listener, command, configuration and block readers, limit and playtime readers, BlueMap sink and messenger            | Paper, BlueMapAPI, `core` and `storage`                    |

`core` has no `org.bukkit` or `de.bluecolored` imports. Plain JUnit tests exercise its logic without a server or map. The adapter converts Paper and BlueMap objects to plain values, asks `core` for a decision and applies it.

## One writer

`SignpostService` owns every signpost change. Additions and removals follow this order:

1. Update `SignpostRegistry` in memory so the next command or event sees the change.
2. Queue the write through `StorageWorker`, keeping database work out of the event handler.
3. Update the map through `MarkerSink`, implemented by `BlueMapMarkerSink` over BlueMapAPI.

Listeners and commands read the registry but never mutate the registry, store or map directly. Limit overrides follow the same service-to-store path.

## Events

Every `SignpostListener` handler uses `MONITOR` priority. Cancellable events use `ignoreCancelled`. The listener observes the outcome without cancelling events or changing blocks.

| Event                                       | Action                                                                          |
| ------------------------------------------- | ------------------------------------------------------------------------------- |
| `SignChangeEvent`                           | Parse the sign's post-edit text and add, update or remove its signpost          |
| `BlockPlaceEvent` for banners               | Add a named banner only when the game allows its placement                      |
| `BlockBreakEvent`                           | Remove the marker and notify the player who broke it                            |
| `BlockDestroyEvent`, explosions and pistons | Remove the marker silently                                                      |
| `ChunkLoadEvent`                            | Check stored signposts in that chunk against their blocks and remove mismatches |

Banner handling must respect a placement refusal as well as cancellation. A named banner rejected by spawn protection must not gain a marker.

Frequent handlers start with the cheapest check: whether the world contains any signposts, then whether the block or chunk contains one.

World loading also restores that world's markers, including worlds created or loaded after startup. Unloading retains their records. A world without a BlueMap map remains in the registry and store without visible markers.

## Keeping BlueMap in step

The plugin registers `BlueMapAPI.onEnable`. It fires when BlueMap becomes ready at startup and after `/bluemap reload`, and republishes the registry into the live marker sets.

The set is created with `computeIfAbsent`. Its label and visibility switches are reapplied on every publish, so configuration changes and fresh BlueMap instances receive the current settings. The plugin only manages the `signposts` set.

BlueMap saves marker state with each map in its configured storage. BlueMapSignPosts never accesses that storage; its own database remains the source for rebuilding the live sets. Tests read BlueMap's saved marker state from file, SQLite, PostgreSQL with TLS `verify-full`, and MariaDB storage.

## Storage

`JdbcSignpostStore` is the only store. It runs the same SQL on every database except the two upserts, which come from the connection source's `Dialect` (`ON CONFLICT` on SQLite and PostgreSQL, `ON DUPLICATE KEY UPDATE` on MySQL and MariaDB). `Schema` builds and upgrades the tables through numbered migrations, each applied in a transaction with its version number, and refuses a database newer than the plugin.

SQLite opens its file per operation. PostgreSQL and MySQL go through `PooledConnectionSource`, a HikariCP pool of at most two connections, over a `DriverDataSource` that wraps a driver instance directly, because a driver Paper loaded for the plugin is not visible to `DriverManager`. Every database call runs on `StorageWorker`'s single thread, except the full read at startup and on a reload that changes the storage settings.

## Settings

`BukkitSettingsCodec` reads `config.yml` into immutable `BlueMapSignPostsSettings`, whose constructor validates values.

The plugin holds settings, policy, parser and service in one `PluginRuntime` referenced by a `volatile` field. Reload swaps the whole runtime, so each event sees a consistent set.

The store is reopened only when storage settings change. That reload reads the new store, drains pending writes before closing the old store, and replaces the old map markers with the new store's markers. Invalid settings or an unusable new store leave the old runtime active.

Missing `marker.detail` uses the default template. Blank detail is invalid because BlueMap would otherwise treat the player-written label as HTML. Player-derived placeholder values are escaped before insertion into the template, and chat placeholders are plain text.

## Folia

The jar declares `folia-supported`. Its threading rules are:

- Block-event work runs on the thread owning the event's block.
- The registry is synchronised.
- Database writes run on the plugin's storage thread.
- BlueMapAPI marker sets are thread-safe.
- Chunk-load checks are handed to the chunk's region scheduler because the thread firing `ChunkLoadEvent` is not guaranteed to own that chunk.
- Reloads are serialised on the plugin because console and player commands may arrive concurrently.

MockBukkit tests exercise the Folia chunk-scheduling path. Folia has no 26.3 release yet; when it does, the build boots the jar on it like Paper and Purpur.

## Tests

| Source set            | Coverage                                                                                                                                                            | Command                     |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------- |
| `src/test`            | `core` and `storage` with JUnit and AssertJ, including a real SQLite file                                                                                           | `./gradlew test`            |
| `src/integrationTest` | The `bukkit` adapter on MockBukkit, with a fake BlueMapAPI registered so tests inspect real marker sets                                                             | `./gradlew integrationTest` |
| `src/serverTest`      | The built jar on Paper 26.3 build 174 with BlueMap 5.29 in Docker, exercised over RCON with each store; the store itself against real PostgreSQL, MySQL and MariaDB | `./gradlew serverTest`      |
| `src/jmh`             | Core hot-path benchmarks                                                                                                                                            | `./gradlew jmh`             |

Unit and integration tests together require 100% line and branch coverage across the plugin, with no excluded classes. `./gradlew check` enforces this.

The server suite loads a small fixture plugin from `src/serverTestFixture`. It fires real sign, place and break events for a fake player and reads the marker sets, observing BlueMapSignPosts from outside. Real-server coverage also includes explosions, pistons, dimensions, BlueMap reloads and runtime world loading.

The store runs its full contract against PostgreSQL 16, MySQL 8.4 and MariaDB 11.8, with and without TLS, and the plugin runs on Paper against each of them.

Every build also boots the jar with BlueMap on Paper and Purpur and waits for the markers to be published. That checks loading and publishing only; the behaviour tests run on Paper.
