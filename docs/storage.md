# Storage

BlueMapSignPosts keeps a record of every signpost and every per-player limit override. The plugin puts the map's markers back from this record whenever BlueMap starts or reloads. See [keeping markers across restarts](bluemap-markers.md#keeping-markers-across-restarts).

This is separate from BlueMap's own storage. The plugin only hands markers to BlueMap, and BlueMap saves them with each map, so the two storage choices do not need to match. See [BlueMap's storage](bluemap-markers.md#bluemaps-storage).

## SQLite (the default)

The default store is `plugins/BlueMapSignPosts/data.db`. It needs no setup and suits almost every server.

Back up `plugins/BlueMapSignPosts/` with the rest of your server. Stop the server before copying the folder, or the copy may miss recent writes.

To move or rename the file, change [`storage.sqlite.file`](configuration.md#storage). Relative paths start inside `plugins/BlueMapSignPosts/`, and full paths work too.

## PostgreSQL

Create a database and a user for the plugin:

```sql
CREATE DATABASE bluemapsignposts;
CREATE USER bluemapsignposts WITH PASSWORD 'change-me';
ALTER DATABASE bluemapsignposts OWNER TO bluemapsignposts;
```

Then point the plugin at it:

```yaml
storage:
    type: postgres
    postgres:
        host: db.example.internal
        port: 5432
        database: bluemapsignposts
        user: bluemapsignposts
        password: 'change-me'
        schema: ''
```

The database, and the schema if you name one, must already exist. A blank `schema` uses the user's default schema, normally `public`. The plugin creates its tables on first start, so the user needs permission to create tables there.

PostgreSQL 9.5 or newer works. The tests use PostgreSQL 16.

## MySQL and MariaDB

Create a database and a user for the plugin:

```sql
CREATE DATABASE bluemapsignposts CHARACTER SET utf8mb4;
CREATE USER 'bluemapsignposts'@'%' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON bluemapsignposts.* TO 'bluemapsignposts'@'%';
```

Then point the plugin at it:

```yaml
storage:
    type: mysql
    mysql:
        host: db.example.internal
        port: 3306
        database: bluemapsignposts
        user: bluemapsignposts
        password: 'change-me'
```

`type: mariadb` means the same thing. Both servers are reached through the MariaDB driver, which supports MySQL as well. The tests use MySQL 8.4 and MariaDB 11.8.

The plugin's tables use a case-sensitive collation, so two worlds whose names differ only in capitals stay apart, as they do on the server.

## Sharing a database between servers

Several servers can share one PostgreSQL or MySQL database as long as their world names are different. Two servers with a world of the same name would each check their own blocks and remove the other's signposts as chunks load.

Each server reads the shared signposts at startup. Its player limits count markers across all the servers as of that read, and `/signposts list all` lists them all.

## TLS

Both server databases can connect over TLS. The startup line shows which mode is in use:

```
[BlueMapSignPosts] Store: postgres bluemapsignposts@db.example.internal:5432/bluemapsignposts sslmode verify-full
```

### PostgreSQL

`storage.postgres.ssl-mode` is the PostgreSQL driver's `sslmode`:

| Mode          | Meaning                                                                     |
| ------------- | --------------------------------------------------------------------------- |
| `disable`     | No TLS                                                                      |
| `allow`       | No TLS unless the server insists on it                                      |
| `prefer`      | TLS when the server offers it, plain otherwise. The default.                |
| `require`     | Always TLS, without checking the server's certificate                       |
| `verify-ca`   | TLS with a server certificate signed by the CA in `ssl-root-cert`           |
| `verify-full` | As `verify-ca`, and the certificate must also match `storage.postgres.host` |

For `verify-ca` and `verify-full`, set `ssl-root-cert` to the CA certificate's path. Left blank, the driver looks for `~/.postgresql/root.crt` in the home folder of the user running the server.

```yaml
storage:
    type: postgres
    postgres:
        host: db.example.internal
        port: 5432
        database: bluemapsignposts
        user: bluemapsignposts
        password: 'change-me'
        ssl-mode: verify-full
        ssl-root-cert: 'plugins/BlueMapSignPosts/ca.crt'
```

### MySQL and MariaDB

`storage.mysql.ssl-mode` is the MariaDB driver's `sslMode`:

| Mode          | Meaning                                                                  |
| ------------- | ------------------------------------------------------------------------ |
| `disable`     | No TLS. The default.                                                     |
| `trust`       | TLS, without checking the server's certificate                           |
| `verify-ca`   | TLS with a server certificate signed by the CA in `ssl-ca-cert`          |
| `verify-full` | As `verify-ca`, and the certificate must also match `storage.mysql.host` |

For `verify-ca` and `verify-full`, set `ssl-ca-cert` to the CA certificate's path. Left blank, Java's own list of trusted CAs is used.

Without TLS, the plugin lets the driver fetch the server's public key, which MySQL 8's default login method needs to send a password over a plain connection. Use TLS when the database is on another network.

## The drivers

The jar contains no database drivers.

- SQLite uses the driver Paper ships with the server.
- For PostgreSQL, MySQL and MariaDB, Paper downloads the [PostgreSQL driver](https://github.com/pgjdbc/pgjdbc), the [MariaDB driver](https://github.com/mariadb-corporation/mariadb-connector-j) and the [HikariCP](https://github.com/brettwooldridge/HikariCP) connection pool from Maven Central the first time the plugin loads, about 2.5 MB in all, and keeps them in the server's `libraries` folder.

If the server cannot reach the internet on that first start, the plugin cannot load. Copying the `libraries` folder from a server that has already run it works too.

## Connections

The SQLite store opens its file for each read or write and closes it again, so nothing is left open between writes.

PostgreSQL and MySQL go through a pool of at most two connections. The pool checks a connection before using it, replaces one the database or a firewall has dropped, and keeps an idle one alive. Connecting and each query time out, so a database that stops answering makes a write fail with an error in the log rather than wait forever.

## The tables

Every database gets the same three tables, created on first start:

| Table                  | One row per             | Columns                                                                                                                |
| ---------------------- | ----------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `bmsp_signposts`       | Signpost                | `world`, `x`, `y`, `z` (the key), `label`, `owner_uuid`, `owner_name`, `source` (`SIGN` or `BANNER`), `created_millis` |
| `bmsp_limit_overrides` | Player with an override | `player_uuid` (the key), `player_name`, `limit_value` (`0` is unlimited), `actor` (who set it), `at_millis`            |
| `bmsp_schema`          | The schema version      | `version`                                                                                                              |

Times are milliseconds since 1970 UTC.

The schema version is 1. When a later version of the plugin changes the tables, it upgrades them on startup, one numbered step at a time. Each step and its new version number are saved together, so a step that fails leaves the version where it was. A plugin older than the database refuses to start rather than read tables it does not understand.

## When it writes

A change goes into a queue at once and is written on the plugin's own background thread, so gameplay never waits for the database. Shutdown waits up to 10 seconds for queued writes. A crash can lose changes still in the queue.

The store is read at startup, and when a reload changes the storage settings. If it cannot be opened or read at startup, the plugin says why and disables itself rather than start with nothing.

A reload that changes the storage settings opens and reads the new store, finishes the old store's queued writes, then swaps the map's markers for the new store's. It does not copy data across. If the new store cannot be opened or read, the old one stays in use.

## Moving to another database

There is no built-in way to copy the data. Set `storage.type` and its connection settings, start the server once so the plugin creates empty tables, and stop it. Then copy the rows of `bmsp_signposts` and `bmsp_limit_overrides` across, with a tool such as `pgloader` or a CSV export and import, and start the server again.

The startup line says how many signposts it read:

```
[BlueMapSignPosts] Store: postgres bluemapsignposts@db.example.internal:5432/bluemapsignposts sslmode prefer (42 signposts, 3 limit overrides)
```
