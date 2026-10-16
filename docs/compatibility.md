# Compatibility

These tables separate what is supported from what the tests actually run. Version `1.0.0+mc26.3` is built for Paper 26.3 build 174 and BlueMap 5.29, using BlueMapAPI 2.8.2.

## Servers and Java

| Server or runtime           | Status                | Testing and limits                                                                                                                                                                                                                                                                                                              |
| --------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Paper 26.3                  | Supported             | The full real-server behaviour suite runs on Paper 26.3 build 174 on every build.                                                                                                                                                                                                                                               |
| Purpur 26.3                 | Supported             | Every build boots the jar with BlueMap and waits for the plugin to publish its markers. Behaviour tests run on Paper.                                                                                                                                                                                                           |
| Folia 26.3                  | Supported, not tested | Folia has no 26.3 release yet, so the jar cannot be run on Folia today. The plugin declares Folia support, and its Folia code path (the chunk-load check on the chunk's own region thread) is covered by the MockBukkit tests. The build boots the jar on Folia too once Folia 26.3 is out. See [Folia](architecture.md#folia). |
| Paper 26.2 and older        | Not supported         | The jar declares `api-version: 26.3`; older servers refuse to load it.                                                                                                                                                                                                                                                          |
| Spigot and CraftBukkit      | Not supported         | The plugin requires Paper's API, including Adventure text and Paper block events.                                                                                                                                                                                                                                               |
| Fabric, NeoForge and Sponge | Not supported         | These require server mods or a different plugin API. BlueMap runs there, but this plugin does not.                                                                                                                                                                                                                              |
| Java 25                     | Supported             | Used by the Minecraft 26.3 server tests and required by Minecraft 26.3 itself.                                                                                                                                                                                                                                                  |

The plugin uses Paper's stable API without server internals. Its only reflection is a class-name check for Folia. A jar built for 26.3 is expected to work on later Paper builds without recompilation. A weekly check starts the current jar with the newest Paper release and BlueMap, waits for marker publication and opens an issue if it fails.

On Folia, the chunk-load check runs on the chunk's own region thread.

## BlueMap

| Version or setup                                    | Status        | Testing and limits                                                                                                                                                                  |
| --------------------------------------------------- | ------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| BlueMap 5.29                                        | Supported     | Tested on every build with the real-server suite.                                                                                                                                   |
| BlueMap 5.24 to 5.28                                | Supported     | Uses the same BlueMapAPI 2.8 line. Version 5.24, the first BlueMap for Minecraft 26.3, was boot-tested by hand with a stored signpost published. The current build suite uses 5.29. |
| BlueMap older than 5.24                             | Not supported | These versions do not run on Minecraft 26.3.                                                                                                                                        |
| BlueMap absent or unable to load                    | Not supported | BlueMap is a required dependency. BlueMapSignPosts does not load without it.                                                                                                        |
| BlueMap as a Paper plugin on the same server        | Supported     | This is the setup used by the server tests.                                                                                                                                         |
| Standalone BlueMap CLI or BlueMap on another server | Not supported | These cannot see this plugin's live API marker sets.                                                                                                                                |
| `/bluemap reload` and BlueMap restarts              | Supported     | Real-server tests confirm that markers are restored automatically.                                                                                                                  |
| `/signposts reload`                                 | Supported     | Tested, including replacing map markers when switching the plugin's database.                                                                                                       |

## BlueMap storage

The plugin never reads or writes BlueMap's storage. It changes BlueMapAPI marker sets, which BlueMap saves with each map. No plugin setting is needed for these backends.

| BlueMap backend           | Status                | Testing                                                                                         |
| ------------------------- | --------------------- | ----------------------------------------------------------------------------------------------- |
| File, the BlueMap default | Supported             | Tested on a real server; saved marker state read back.                                          |
| SQLite                    | Supported             | Tested on the same server using sqlite-jdbc 3.49.1.0; saved marker state read back.             |
| PostgreSQL                | Supported             | PostgreSQL 16 tested with TLS required and `sslmode=verify-full`; saved marker state read back. |
| MariaDB                   | Supported             | MariaDB 11.8 tested on the same server; saved marker state read back.                           |
| MySQL                     | Supported, not tested | Supported by BlueMap through the same marker API path; no MySQL storage test was run.           |

The real-server test moved every map through the four tested backends using `/bluemap reload`. For SQL, BlueMap received each JDBC driver through `driver-jar` and `driver-class`. See [BlueMap storage examples](bluemap-markers.md#bluemaps-storage).

## Plugin store and databases

| Store or database                                        | Status                | Testing and limits                                                                                                                     |
| -------------------------------------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| SQLite, through the driver Paper ships (3.49.1.0)        | Supported             | The store's unit tests use a real SQLite file through the same driver version, and the built jar is tested on a real server.           |
| PostgreSQL 16, plain connection                          | Supported             | Tested by the store tests against a real server and by the plugin on a real Paper server.                                              |
| PostgreSQL 16, TLS with `verify-full`                    | Supported             | Tested against a database refusing non-TLS connections, with a certificate signed by a self-signed CA. A plain connection was refused. |
| PostgreSQL 9.5 or newer, other than 16                   | Supported, not tested | The SQL needs `ON CONFLICT`, added in PostgreSQL 9.5.                                                                                  |
| MySQL 8.4 and MariaDB 11.8, plain connection             | Supported             | Tested by the store tests against real servers and by the plugin on a real Paper server.                                               |
| MySQL 8.4 and MariaDB 11.8, TLS with `trust`             | Supported             | Tested against both servers, with the session's TLS cipher checked.                                                                    |
| MySQL and MariaDB, TLS with `verify-ca` or `verify-full` | Supported, not tested | The setting is passed to the MariaDB driver unchanged.                                                                                 |
| Other MySQL and MariaDB versions                         | Supported, not tested | The plugin sends the same SQL to every version; only the versions above are tested.                                                    |
| Other databases                                          | Not supported         | `storage.type` accepts `sqlite`, `postgres` or `mysql`.                                                                                |

## Worlds, blocks and removal

| Behaviour or block type                                                                     | Status    | Testing and limits                                                                                                                                                                                |
| ------------------------------------------------------------------------------------------- | --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Overworld, Nether and End, each on its own map                                              | Supported | Tested on a real server.                                                                                                                                                                          |
| Extra worlds created, unloaded and loaded again while the server runs                       | Supported | A real-server test creates a world, adds a signpost, unloads and reloads it, and confirms that the signpost remains listed. Markers are restored when worlds load.                                |
| Worlds without a BlueMap map                                                                | Supported | Signposts remain stored and listed, but display requires a map for that world.                                                                                                                    |
| Standing, wall and hanging signs, every wood, both sides                                    | Supported | Sign behaviour is covered by MockBukkit and real-server tests; support includes all these variants.                                                                                               |
| Standing and wall banners                                                                   | Supported | Banner placement is covered by the adapter and real-server tests, including refusing a marker when the game rejects placement.                                                                    |
| Breaking a signpost                                                                         | Supported | Tested on a real server.                                                                                                                                                                          |
| Destruction by an explosion                                                                 | Supported | Tested with a real explosion on a real server.                                                                                                                                                    |
| Removal through a piston                                                                    | Supported | Tested with MockBukkit through the piston's own events, for both pushing and pulling.                                                                                                             |
| Loss of the block a signpost stands or hangs on                                             | Supported | Tested with MockBukkit.                                                                                                                                                                           |
| Editing a sign                                                                              | Supported | Tested with MockBukkit and on a real server.                                                                                                                                                      |
| Block changes without events, including commands, WorldEdit, rollbacks and restored backups | Supported | The next chunk load removes stored signposts that no longer match their blocks. The reconciliation path is tested with MockBukkit. Restored signs are not discovered as new markers until edited. |

## Other plugins

The plugin observes events at `MONITOR` priority after other plugins have had their say. It never cancels events or changes blocks. The compatibility rules below describe how it interacts with other plugins; they do not claim individual tests of every named product.

| Plugin type                                                         | Status                | Behaviour and testing basis                                                                                                                                                                               |
| ------------------------------------------------------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Protection plugins, including WorldGuard, GriefPrevention and Lands | Supported, not tested | Cancelled placement and editing events are ignored. If protection stops the action, no marker is added. The event-handling rule is tested; each named plugin is not individually verified.                |
| Logging and rollback plugins, including CoreProtect and Prism       | Supported, not tested | Signs and banners are logged normally. A restored `[map]` sign needs an edit to gain a marker; a removed sign loses its marker on the next chunk load. The reconciliation path is tested with MockBukkit. |
| Anti-cheat plugins                                                  | Supported, not tested | The plugin changes no packets. Individual anti-cheat integrations are not claimed as tested.                                                                                                              |
| Other BlueMap marker plugins                                        | Supported, not tested | Marker sets remain separate; BlueMapSignPosts only changes `signposts`. Individual plugin combinations are not claimed as tested.                                                                         |
| Sign editors using Paper's `SignChangeEvent`                        | Supported, not tested | Changes are handled like player edits. The event path is tested, but individual editor plugins are not claimed as tested.                                                                                 |
| Sign editors changing text without `SignChangeEvent`                | Supported, not tested | Stored signposts are checked when the chunk next loads. This path is tested with MockBukkit; it does not discover previously unregistered signs.                                                          |
