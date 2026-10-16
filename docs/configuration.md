# Configuration

Edit `plugins/BlueMapSignPosts/config.yml`, then run `/signposts reload` or restart your server. Every setting can be applied without a restart. The plugin creates this file with defaults on its first start.

Missing settings use their defaults. This includes `marker.detail`: an older file without that key gets the same popup as a new file.

Invalid values, such as a blank trigger, `2 hours` for playtime, a negative limit, blank `marker.detail`, an unknown storage type or an unknown TLS mode, are refused with an explanation. At startup, the plugin disables itself. During `/signposts reload`, it keeps the old settings and reports the problem.

Wrong types are handled differently. Text where a number or boolean is expected, including quoted `'false'`, silently falls back to the default.

A successful reload republishes markers with the new appearance. If storage settings change, the map switches to the markers in the new store. See [storage](#storage).

The [whole file](#the-whole-file) below shows every default.

## Contents

- [sign.trigger](#signtrigger)
- [banners.enabled](#bannersenabled)
- [required-playtime](#required-playtime)
- [create-permission and bypass-permission](#create-permission-and-bypass-permission)
- [limits](#limits)
- [marker-set](#marker-set)
- [marker](#marker)
- [storage](#storage)
- [messages](#messages)

## sign.trigger

The first line that turns a sign into a marker. Default: `[map]`.

```yaml
sign:
    trigger: '[map]'
```

Matching ignores capitals and surrounding spaces. With the default, `[map]`, `[MAP]` and `[Map]` all work. The trigger must occupy the whole first line, so `[map] Spawn` does not count.

The trigger cannot be blank. Quote `[map]`, because YAML reads it as a list without quotes. Other examples are `'[poi]'` and `'[Waypoint]'`.

Changing the trigger does not immediately remove existing markers. Each stored sign is checked when its chunk next loads and loses its marker if it no longer matches. Run `/signposts list all` before changing the trigger to see what exists.

## banners.enabled

Whether placing a banner renamed in an anvil creates a marker. Default: `true`.

```yaml
banners:
    enabled: true
```

Setting this to `false` stops new banner markers. Existing banner markers remain until their banners are broken.

## required-playtime

How long you must have played before adding a marker. Default: `0s`, which disables the requirement.

```yaml
required-playtime: 2h
```

Every value needs a unit. Write `0s`, not `0`. Supported units are `d` for days, `h` for hours, `m` for minutes and `s` for seconds. You can combine them: `90m`, `1d12h`, `1d 12h` and `2h30m` are valid. Values such as `2 hours`, `2.5h` and `-1h` are refused.

Playtime comes from Minecraft's **Time Played** statistic, visible in the Statistics screen. It includes time on this server before the plugin was installed.

The [bypass permission](#create-permission-and-bypass-permission) skips this check. Editing a marker you already own does not check playtime again.

## create-permission and bypass-permission

The permission nodes checked before a marker is added. Defaults: `bluemapsignposts.create` and `bluemapsignposts.bypass`.

```yaml
create-permission: bluemapsignposts.create
bypass-permission: bluemapsignposts.bypass
```

You need the create permission to add a marker. Everyone has `bluemapsignposts.create` unless your permissions plugin takes it away.

The bypass permission skips the create permission, playtime requirement and limit. Operators have `bluemapsignposts.bypass` by default.

You can use your own nodes, for example `create-permission: myserver.members`. Neither setting may be blank. [Commands and permissions](commands-and-permissions.md) includes LuckPerms examples.

## limits

How many markers each player may have at once.

```yaml
limits:
    default: 10
    operators-unlimited: true
```

| Key                   | Default | Meaning                                                                                                             |
| --------------------- | ------- | ------------------------------------------------------------------------------------------------------------------- |
| `default`             | `10`    | The limit when no other rule applies. `0` means unlimited. Negative numbers are refused.                            |
| `operators-unlimited` | `true`  | Operators have no limit. Setting this to `false` only enforces a limit if they also lose `bluemapsignposts.bypass`. |

Permission tiers, `bluemapsignposts.limit.<n>` and `bluemapsignposts.limit.unlimited`, and per-player overrides set with `/signposts limit set` live outside this file. Overrides are stored with the markers, so editing or reloading `config.yml` does not lose them. [Commands and permissions](commands-and-permissions.md#limits) explains their precedence.

Lowering a limit never removes markers. Players over the new limit keep their existing markers and cannot add more until they are below it.

## marker-set

The layer in BlueMap's marker menu.

```yaml
marker-set:
    label: Signposts
    toggleable: true
    default-hidden: false
```

| Key              | Default     | Meaning                                                               |
| ---------------- | ----------- | --------------------------------------------------------------------- |
| `label`          | `Signposts` | The layer's name. Cannot be blank.                                    |
| `toggleable`     | `true`      | Visitors can show or hide the layer.                                  |
| `default-hidden` | `false`     | The layer starts visible. With `true`, visitors need to switch it on. |

## marker

The appearance of each marker.

```yaml
marker:
    max-distance: 100000
    detail: '%label%<br><small>placed by %owner%</small>'
```

### max-distance

The greatest distance from the camera, in blocks, at which a marker is visible. Default: `100000`, effectively always visible in normal use.

For a crowded map, a smaller value such as `1000` shows markers only when the camera is nearby. The value must be greater than 0.

### detail

The popup shown when a visitor hovers over or clicks a marker. Default: `'%label%<br><small>placed by %owner%</small>'`.

The template is HTML. You can use tags such as `<br>`, `<b>` and `<small>`, with these placeholders:

| Placeholder | Value                                       | Example          |
| ----------- | ------------------------------------------- | ---------------- |
| `%label%`   | The marker's name                           | `Plains Village` |
| `%owner%`   | The owner's name when the marker was placed | `Cartographer`   |
| `%world%`   | The world's folder name                     | `world`          |
| `%x%`       | The block's X coordinate                    | `-220`           |
| `%y%`       | The block's Y coordinate                    | `77`             |
| `%z%`       | The block's Z coordinate                    | `393`            |

All placeholder values are HTML-escaped. A sign saying `<script>` displays that text; it does not run a script. Only your template is treated as HTML.

A blank `detail` is refused because BlueMap would then interpret the player-written marker name as HTML. Startup fails with an explanation; a failed `/signposts reload` leaves the old settings active. Omitting the key uses the default popup.

```yaml
# The name alone.
detail: '%label%'
# The name in bold and the coordinates.
detail: '<b>%label%</b><br>%x%, %y%, %z%'
# The name and where it is, without the player's name.
detail: '%label%<br><small>%world% %x% %y% %z%</small>'
```

## storage

Where the plugin keeps signposts and per-player limit overrides. The default is a SQLite file in the plugin folder and needs no setup. PostgreSQL, MySQL and MariaDB are there for servers that already run one. [Storage](storage.md) covers setting each one up, TLS, backups and moving between them.

These settings are for the plugin's own store. They do not configure BlueMap's storage.

```yaml
storage:
    type: sqlite
    sqlite:
        file: data.db
    postgres:
        host: localhost
        port: 5432
        database: bluemapsignposts
        user: bluemapsignposts
        password: ''
        schema: ''
        ssl-mode: prefer
        ssl-root-cert: ''
    mysql:
        host: localhost
        port: 3306
        database: bluemapsignposts
        user: bluemapsignposts
        password: ''
        ssl-mode: disable
        ssl-ca-cert: ''
```

| Key                      | Default            | Meaning                                                                                                                    |
| ------------------------ | ------------------ | -------------------------------------------------------------------------------------------------------------------------- |
| `type`                   | `sqlite`           | `sqlite`, `postgres` or `mysql`. `postgresql` and `mariadb` also work, and capitals are ignored. Other values are refused. |
| `sqlite.file`            | `data.db`          | A path relative to `plugins/BlueMapSignPosts/`, or a full path.                                                            |
| `postgres.host`          | `localhost`        | The PostgreSQL server's host name or address.                                                                              |
| `postgres.port`          | `5432`             | Its port.                                                                                                                  |
| `postgres.database`      | `bluemapsignposts` | An existing database.                                                                                                      |
| `postgres.user`          | `bluemapsignposts` | The database user.                                                                                                         |
| `postgres.password`      | `''`               | That user's password.                                                                                                      |
| `postgres.schema`        | `''`               | An existing schema. Blank uses the user's default schema, normally `public`.                                               |
| `postgres.ssl-mode`      | `prefer`           | `disable`, `allow`, `prefer`, `require`, `verify-ca` or `verify-full`. See [TLS](storage.md#tls).                          |
| `postgres.ssl-root-cert` | `''`               | The CA certificate for `verify-ca` and `verify-full`.                                                                      |
| `mysql.host`             | `localhost`        | The MySQL or MariaDB server's host name or address.                                                                        |
| `mysql.port`             | `3306`             | Its port.                                                                                                                  |
| `mysql.database`         | `bluemapsignposts` | An existing database.                                                                                                      |
| `mysql.user`             | `bluemapsignposts` | The database user.                                                                                                         |
| `mysql.password`         | `''`               | That user's password.                                                                                                      |
| `mysql.ssl-mode`         | `disable`          | `disable`, `trust`, `verify-ca` or `verify-full`. See [TLS](storage.md#tls).                                               |
| `mysql.ssl-ca-cert`      | `''`               | The CA certificate for `verify-ca` and `verify-full`.                                                                      |

When a storage setting changes, a reload opens and reads the new store, finishes any writes still queued for the old one, and replaces the old store's markers on the map with the new store's. It does not copy data between stores. With the storage settings unchanged, the store stays open.

## messages

Every player-facing message is a [MiniMessage](https://docs.advntr.dev/minimessage/format) template. You can use colours and formatting such as `<green>`, `<bold>` and `<gradient:gold:yellow>`.

Set a message to `''` to silence it. A missing key uses the supplied default.

Placeholder values are inserted as plain text. A sign named `<red>Hi` produces the literal text `<red>Hi` in chat, without changing its colour or adding click actions or commands.

| Key                 | Sent when                                             | Placeholders                            |
| ------------------- | ----------------------------------------------------- | --------------------------------------- |
| `created`           | A player adds a marker                                | `%label%`                               |
| `updated`           | A player renames their own marker by editing its sign | `%label%`                               |
| `removed`           | A player breaks a signpost; sent to whoever broke it  | `%label%`                               |
| `denied-permission` | The player lacks the create permission                | none                                    |
| `denied-playtime`   | The player has not met `required-playtime`            | `%required%`, `%played%`, `%remaining%` |
| `denied-limit`      | The player has reached their limit                    | `%count%`, `%limit%`                    |
| `empty-label`       | The sign has the trigger but no name                  | `%trigger%`                             |

Playtime placeholders use a format such as `1d2h30m`. `%limit%` is a number or `unlimited`.

```yaml
messages:
    created: '<green>Added to the map: <white>%label%</white></green>'
    updated: '<green>Updated on the map: <white>%label%</white></green>'
    removed: '<gray>Removed from the map: <white>%label%</white></gray>'
    denied-permission: "<red>You can't add markers to the map.</red>"
    denied-playtime: "<red>You need <white>%required%</white> of playtime to add markers to the map. You've played <white>%played%</white> - <white>%remaining%</white> to go.</red>"
    denied-limit: '<red>You already have <white>%count%</white> markers on the map (the limit is <white>%limit%</white>). Break one of your signposts to free a slot.</red>'
    empty-label: '<red>Write the name of the place on the lines under <white>%trigger%</white>.</red>'
```

## The whole file

Every default written on first start:

```yaml
sign:
    trigger: '[map]'

banners:
    enabled: true

required-playtime: 0s

create-permission: bluemapsignposts.create

bypass-permission: bluemapsignposts.bypass

limits:
    default: 10
    operators-unlimited: true

marker-set:
    label: Signposts
    toggleable: true
    default-hidden: false

marker:
    max-distance: 100000
    detail: '%label%<br><small>placed by %owner%</small>'

storage:
    type: sqlite
    sqlite:
        file: data.db
    postgres:
        host: localhost
        port: 5432
        database: bluemapsignposts
        user: bluemapsignposts
        password: ''
        schema: ''
        ssl-mode: prefer
        ssl-root-cert: ''
    mysql:
        host: localhost
        port: 3306
        database: bluemapsignposts
        user: bluemapsignposts
        password: ''
        ssl-mode: disable
        ssl-ca-cert: ''

messages:
    created: '<green>Added to the map: <white>%label%</white></green>'
    updated: '<green>Updated on the map: <white>%label%</white></green>'
    removed: '<gray>Removed from the map: <white>%label%</white></gray>'
    denied-permission: "<red>You can't add markers to the map.</red>"
    denied-playtime: "<red>You need <white>%required%</white> of playtime to add markers to the map. You've played <white>%played%</white> - <white>%remaining%</white> to go.</red>"
    denied-limit: '<red>You already have <white>%count%</white> markers on the map (the limit is <white>%limit%</white>). Break one of your signposts to free a slot.</red>'
    empty-label: '<red>Write the name of the place on the lines under <white>%trigger%</white>.</red>'
```

The generated file also includes comments explaining each setting.
