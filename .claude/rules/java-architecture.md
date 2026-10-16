# Architecture

The code is split three ways, and every change keeps to the split.

## The seam

- **`core` is pure.** Everything under `com.lewisgibson.bluemapsignposts.core` (sign parsing, the signpost model, registry and service, the store interface, the limit model and create policy, durations, marker content, the settings and message model) holds the decision logic and imports no `org.bukkit.*` and no `de.bluecolored.*` type. It works on primitives and its own value types, so it is unit-tested without a server or a map. The map is reached through the `core.map.MarkerSink` port, which the adapter implements over BlueMapAPI and the tests over a fake.
- **`storage` is JDBC.** One `JdbcSignpostStore` over a `ConnectionSource` per database (a SQLite file, or a HikariCP pool for PostgreSQL and MySQL), the SQL that differs per database in `Dialect`, the numbered migrations in `Schema`, and the `StorageWorker` thread. It imports `java.sql`, HikariCP and the drivers, never `org.bukkit.*` or `de.bluecolored.*`. Block events never reach it: they read the in-memory `SignpostRegistry`, and the `SignpostService` hands writes to the worker.
- **`bukkit` is a thin adapter.** Everything under `...bukkit` (the plugin, the listener, the command, the config codec, the block reader, the BlueMap sink, the messenger, the limit and playtime readers) turns Bukkit and BlueMap types into primitives, asks `core` for a decision and acts on it. It holds no decision logic of its own.

New logic goes in `core` wherever it can. An `org.bukkit.*` or `de.bluecolored.*` import under `core` or `storage` is a bug, and so is a store call from a block event.

## Value types and invariants

- Domain types are immutable records that validate their invariants in the constructor, so an invalid instance cannot exist: a signpost always has a trimmed, non-empty, bounded label and a non-blank world.
- The registry is the truth and `SignpostService` is its only writer. Every add or remove updates the registry first, queues the store write on the storage thread, then updates the map, so the three disagree for at most one failed write, which is logged. Limit overrides take the same path into the same store, never into `config.yml`.
- The runtime settings are swapped as one snapshot on reload, so each event reads one consistent set.
- A schema change is a new migration appended to `Schema.MIGRATIONS`, never an edit to one that has shipped.

## Platform safety

The plugin uses only stable, high-level API: block events, `Statistic`, config, commands, Adventure text and BlueMapAPI's public marker classes. No NMS, no CraftBukkit internals, no reflection into server or BlueMap classes (the Folia class-name check is the one exception). That is what keeps a jar built once working on later server versions, and what lets the plugin declare Folia support: every API it touches is safe on region threads, and the registry is synchronised.
