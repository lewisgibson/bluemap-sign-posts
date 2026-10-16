# BlueMap markers

Signposts appear in their own BlueMap layer. This page explains how they are identified, which maps show them and how they survive reloads. It also covers BlueMap's storage for admins and integrations that work with these markers.

## The layer

The marker set has the id `signposts`. A signpost appears on every BlueMap map of its world. BlueMap's default setup has one map per dimension; if you add a flat or cave map for the same world, the signpost appears there too.

| Layer property       | Setting                                                                |
| -------------------- | ---------------------------------------------------------------------- |
| Name in the menu     | [`marker-set.label`](configuration.md#marker-set), default `Signposts` |
| Visitors can hide it | `marker-set.toggleable`, default `true`                                |
| Starts hidden        | `marker-set.default-hidden`, default `false`                           |

The plugin only changes its own `signposts` set. It leaves BlueMap's markers, markers from other plugins and BlueMap's `markers` configuration alone.

## Each marker

Each signpost is a POI marker, using BlueMap's default point-of-interest pin.

| Property     | Value                                                                                         |
| ------------ | --------------------------------------------------------------------------------------------- |
| Id           | Block coordinates as `x,y,z`, for example `-220,77,393`. Each block has at most one signpost. |
| Position     | The block centre: `x + 0.5`, `y + 0.5`, `z + 0.5`                                             |
| Label        | The signpost's name, shown in BlueMap's marker list                                           |
| Detail       | The filled-in [`marker.detail`](configuration.md#detail) template, shown on hover or click    |
| Icon         | BlueMap's default POI pin                                                                     |
| Max distance | [`marker.max-distance`](configuration.md#max-distance), default 100000 blocks                 |

Player-written values, including the label and owner's name, are HTML-escaped in the detail. Characters such as `<`, `>`, `&`, `"` and `'` display as text. Player text cannot run in a visitor's browser. A blank detail template is refused because BlueMap would otherwise treat the label as HTML.

## Which worlds

Signposts work in every world BlueMap maps, including the Overworld, Nether and End. Each dimension shows them on its own map.

If BlueMap has no map for a world, its signposts remain stored and listed but are not displayed. They appear when you add a map for that world and reload BlueMap. Changing BlueMap's mapped worlds does not delete signposts.

Worlds loaded after startup get their markers when they load. This includes worlds loaded on demand by a multiworld plugin and worlds loaded again after unloading. Unloading a world keeps its signposts in the store and `/signposts list`. A real-server test creates a world at runtime, adds a signpost, unloads and reloads the world, and confirms the signpost is still listed.

## Keeping markers across restarts

BlueMap saves marker state with each map, but the plugin's store remains the source for rebuilding its live marker sets. BlueMapSignPosts republishes stored signposts whenever BlueMap starts or reloads.

Markers are restored:

- When the server starts and BlueMap is ready.
- After `/bluemap reload` or a BlueMap restart.
- After `/signposts reload`, using the active store.
- When an admin runs `/signposts republish`.
- When a world loads after startup.

The plugin keeps its own copy in [storage](storage.md), so you do not need to recreate markers after a restart. When BlueMap starts or reloads, the server log reports the number published:

```
[BlueMapSignPosts] BlueMap is up: published 8 signpost(s) to the map.
```

`/signposts reload` and `/signposts republish` report their result to whoever runs them. If a reload switches the plugin to another store, its old markers are replaced with the new store's markers.

## BlueMap's storage

BlueMapSignPosts never reads or writes BlueMap's storage. It adds markers through BlueMapAPI's marker sets, and BlueMap saves them with each map in that map's chosen storage. No BlueMapSignPosts setting is needed for any of these storage types.

Each map selects a storage id in `plugins/BlueMap/maps/<map>.conf`:

```hocon
storage: "<id>"
```

That storage is configured in `plugins/BlueMap/storages/<id>.conf`. Paths in these examples are relative to the server folder.

| BlueMap storage                                  | Verification                                                              |
| ------------------------------------------------ | ------------------------------------------------------------------------- |
| File, BlueMap's default                          | Tested on a real server; saved marker state read back                     |
| SQLite                                           | Tested on the same server; saved marker state read back                   |
| PostgreSQL 16 with TLS and `sslmode=verify-full` | Tested on the same server with TLS required; saved marker state read back |
| MariaDB 11.8                                     | Tested on the same server; saved marker state read back                   |
| MySQL                                            | Supported by BlueMap and works through the same API; not tested           |

The test moved every map through file, SQLite, PostgreSQL and MariaDB storage with `/bluemap reload`. The signpost was present in the saved marker state every time.

SQL storage needs a JDBC driver available to BlueMap. The tests supplied each driver through BlueMap's `driver-jar` and `driver-class` settings. This is separate from the drivers Paper downloads for the plugin's own store.

### PostgreSQL with TLS

This is the tested BlueMap storage configuration, with placeholder host and credentials:

```hocon
storage-type: sql
connection-url: "jdbc:postgresql://db.example.internal:5432/bluemap?sslmode=verify-full&sslrootcert=plugins/BlueMap/ca.crt"
connection-properties: {
  user: "bluemap",
  password: "change-me"
}
max-connections: -1
driver-jar: "plugins/BlueMap/postgresql-42.7.14.jar"
driver-class: "org.postgresql.Driver"
compression: gzip
```

The test used PostgreSQL 16 with TLS required and a certificate signed by a self-signed CA. `verify-full` checks both the certificate's CA and its match to the host name or address.

### SQLite

The SQLite test used these connection and driver settings:

```hocon
connection-url: "jdbc:sqlite:plugins/BlueMap/bluemap.db"
driver-jar: "plugins/BlueMap/sqlite-jdbc-3.49.1.0.jar"
driver-class: "org.sqlite.JDBC"
```

### MariaDB

The MariaDB 11.8 test used these settings, with the host shown as a placeholder:

```hocon
connection-url: "jdbc:mariadb://db.example.internal:3306/bluemap"
driver-jar: "plugins/BlueMap/mariadb-java-client-3.5.10.jar"
driver-class: "org.mariadb.jdbc.Driver"
```

For the plugin's own store, see [storage](storage.md).

## How fast changes show up

The live marker set changes as soon as a sign is written or broken. An open browser fetches markers periodically, so visitors see the change on the next fetch without refreshing. BlueMap's web app controls the interval; it is 10 seconds in BlueMap 5.28.

## BlueMap's own behaviour

The pin and popup styling come from BlueMap's web app. Use its `webapp` settings or a custom stylesheet to change them.

Markers can appear over unrendered areas. They remain above blank ground until BlueMap renders it.

Live player positions, BlueMap's own markers and search are controlled by BlueMap and its settings.
