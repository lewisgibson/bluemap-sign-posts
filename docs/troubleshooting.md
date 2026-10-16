# Troubleshooting

Start with the relevant symptom below. Server messages are in `logs/latest.log`. If these checks do not explain the problem, see [reporting a bug](#reporting-a-bug).

## The plugin doesn't load

Find `BlueMapSignPosts` near the start of the log.

| Log message or symptom                                                                                                | What to do                                                                                                                                          |
| --------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Could not load 'plugins/BlueMapSignPosts-<version>.jar'` followed by `Unknown/missing dependency plugins: [BlueMap]` | BlueMap is absent or failed to load. Install [BlueMap](https://modrinth.com/plugin/bluemap) for Paper, fix any BlueMap startup error and restart.   |
| An error mentioning `api-version`                                                                                     | The server is older than Minecraft 26.3. Check [compatibility](compatibility.md).                                                                   |
| An error downloading `HikariCP`, `postgresql` or `mariadb-java-client` from Maven Central                             | The first start needs internet access to download them. Allow that download or follow [the drivers](storage.md#the-drivers).                        |
| `Invalid configuration or unusable store: ...` followed by `Disabling BlueMapSignPosts; fix config.yml and restart.`  | The preceding error identifies a bad setting or an inaccessible database. Fix it and restart. [Configuration](configuration.md) lists valid values. |

A blank `marker.detail` is invalid. Use `%label%` if you want only the name, or remove the key to use the default popup.

## A sign doesn't become a marker

Check these in order:

1. Put the trigger alone on the first line. `[map] Spawn` does not count, and `[map]` on the second line does not count. Capitals and surrounding spaces are ignored. See [writing a sign](usage.md#writing-a-sign).
2. Check the player's chat for a permission, playtime, limit or empty-name message. See [the list](usage.md#when-a-player-isnt-allowed-a-marker). A message set to `''` is silent; use `/signposts list` to check the player's current markers.
3. Check both sides. A front side containing `[map]` takes precedence even with no name. Add a name there, or remove its trigger to use the back.
4. Check whether protection prevented the placement or edit. Cancelled actions do not create markers.
5. If a command, structure or backup placed the sign, right-click it and click Done. The plugin learns about new signs when a player writes them.

Use `/signposts list <player>` to see the stored markers. If yours is listed, continue below.

## A marker is in /signposts list but not on the map

1. Open BlueMap's menu, then Markers, and enable **Signposts**. With `marker-set.default-hidden: true`, the layer starts hidden.
2. Check that BlueMap maps the marker's world. Unmapped worlds keep their signposts without displaying them.
3. Check whether the world is loaded. Unloading keeps its signposts stored and listed; loading it restores its markers automatically.
4. Zoom in if `marker.max-distance` is small.
5. Wait for the web app's next marker fetch. Refresh the page if it has been open a long time.
6. Run `/signposts republish` if BlueMap was reloaded unusually. If that fixes it, include the reload method in a bug report; normal BlueMap reloads republish automatically.

A world loaded after startup should receive its markers without a manual command. Once BlueMap has a map for a previously unmapped world, reload BlueMap to publish them there.

## A banner doesn't become a marker

Use a custom name from an anvil and check that `banners.enabled` is `true`. The usual create permission, playtime and limit checks also apply.

A banner the game refuses to place, including one inside spawn protection, creates no marker.

## A marker stays after its sign is gone

Normal breaking, editing, explosions, pistons and loss of support remove markers as the events happen.

Changes without events, such as `/setblock`, `/fill`, WorldEdit, rollbacks and restored backups, are checked when the chunk next loads. To remove a stale marker immediately, run `/signposts remove <world> <x> <y> <z>` using coordinates from `/signposts list`.

## Markers disappeared after a restart

The plugin republishes stored markers when BlueMap starts.

1. Find `BlueMap is up: published N signpost(s) to the map.` If N is 0, check whether `storage` changed or now points to an empty store. The preceding `Store: ...` line identifies it.
2. Confirm the plugin started. See [the plugin doesn't load](#the-plugin-doesnt-load).
3. Check whether `data.db` was deleted or replaced, including by an older backup of the plugin folder.

A reload that switches databases deliberately replaces the map's markers with those from the selected database. It does not copy the previous store's contents.

## "Reload failed: ..."

`/signposts reload` found invalid configuration or could not open or read the selected store. The old settings and store remain active. Correct the reported problem and run the command again.

## The plugin can't connect to its database

The log line starting `Invalid configuration or unusable store: could not connect` gives the database's own reason.

- Check the host, port, database name, user and password under `storage.postgres` or `storage.mysql`, and that the database server accepts connections from the Minecraft server's address.
- The database and, for PostgreSQL, a named schema must already exist. See [storage](storage.md).
- With TLS, check the `ssl-mode` and the CA certificate path. With `verify-full`, the certificate must also match the host name or address in `host`. See [TLS](storage.md#tls).

These settings are only for the plugin's own store. BlueMap's SQL storage is set up in BlueMap's own storage files.

## "A store write failed" in the log

The database could not accept a write. Common causes are a full disk, a read-only file or a database server that has gone away.

The change is already in memory and on the map, but not in the database. After a restart, a failed addition is missing and a failed removal returns until its chunk loads and is checked again. Fix the underlying cause. The next change to that signpost is written normally.

## A player's limit isn't what you expect

Run `/signposts limit <player>` while the player is online. Its `Decided by` line identifies the rule. See [limits](commands-and-permissions.md#limits) for precedence.

| Cause                    | Explanation or fix                                                                                                                                |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| A per-player override    | It takes precedence over every limit rule. Remove it with `/signposts limit clear <player>`.                                                      |
| Operator bypass          | Operators hold `bluemapsignposts.bypass` by default. `limits.operators-unlimited: false` only enforces a limit if you remove that permission too. |
| A tier below the default | `bluemapsignposts.limit.5` gives a limit of 5 even when `limits.default` is 10.                                                                   |

## Reporting a bug

Open an [issue](https://github.com/lewisgibson/bluemap-sign-posts/issues/new/choose) with:

- Your Minecraft, Paper or fork, BlueMap and BlueMapSignPosts versions. Use `/version BlueMapSignPosts` for the plugin version.
- The exact sign text or banner name and what you expected to see.
- The server-start section of `logs/latest.log`, any stack trace and the relevant `/signposts list <player>` output.
