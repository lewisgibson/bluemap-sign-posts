# Commands and permissions

Use `/signposts`, or `/bluemapsignposts` if another plugin already uses that name. Subcommands, player names and world names support tab completion.

## Who can run what

| Command                           | Who can run it                                                       |
| --------------------------------- | -------------------------------------------------------------------- |
| `/signposts` or `/signposts list` | Any player, to see their own markers                                 |
| Everything else                   | Anyone with `bluemapsignposts.admin`, including operators by default |

The console has admin access. Players without `bluemapsignposts.admin` receive a permission denial for admin commands. Their tab completion offers only `list`, without other players' names.

## Naming a player

Commands resolve `<player>` in this order, without contacting Mojang:

1. An online player.
2. A player in the server's cache of previous visitors.
3. A name recorded on a marker or limit override.

Matching ignores capitals. You can use these commands for offline players, from the console or in game.

## The commands

### /signposts list

Shows your markers and limit:

```
BlueMapSignPosts
You have: 3 of 10
Plains Village: world -220 77 393 by Cartographer
North Farm: world 412 64 -1080 by Cartographer
Nether Hub: world_nether 52 70 -134 by Cartographer
```

When `required-playtime` is enabled, another line gives the requirement. From the console, `/signposts list` without arguments asks which list you want.

Lists include stored signposts in unloaded worlds and worlds without a BlueMap map. Being listed does not mean a marker is currently visible.

### /signposts list all

Shows every stored marker and its owner:

```
BlueMapSignPosts
Signposts on the map: 2
Plains Village: world -220 77 393 by Cartographer
Miner's Rest: world 412 64 -1080 by Builder
```

### /signposts list <player>

Shows one player's markers and limit. For `/signposts list Cartographer`:

```
BlueMapSignPosts
Cartographer has: 1 of 10
Plains Village: world -220 77 393 by Cartographer
```

### /signposts list world <world>

Shows every marker in a world, using its folder name. For example, `/signposts list world world_nether`.

### /signposts remove <world> <x> <y> <z>

Removes a marker from the map and store without touching its block. Use the sign's or banner's own block coordinates, as shown by `/signposts list`.

```
/signposts remove world -220 77 393
Removed "Plains Village" at world -220 77 393 from the map. The block is untouched.
```

A sign still carrying `[map]` can create a marker again when someone edits it, subject to the usual checks. Break or edit the sign as well if you want it to stay off the map.

### /signposts limit <player>

Shows a player's limit, usage and the rule that decided it:

```
/signposts limit Cartographer
BlueMapSignPosts
Cartographer's limit: 25 (1 used)
Decided by: the permission tier bluemapsignposts.limit.25 (the highest held)
```

Permission tiers and the unlimited permission cannot be read for offline players. If that leaves the default deciding the result, `/signposts limit` explains the limitation. `/signposts list <player>` shows the number without that explanation.

This does not let anyone exceed their real limit. Limits are enforced when players add markers, while they are online.

### /signposts limit set <player> <n|unlimited>

Sets a per-player override, ahead of every other limit rule. Use a whole number of at least 1 or `unlimited`. The override is stored with the markers, so a configuration reload does not lose it.

```
/signposts limit set Cartographer 25
/signposts limit set Builder unlimited
```

To prevent new markers, remove the create permission as described [below](#stopping-a-player-adding-markers).

### /signposts limit clear <player>

Removes a player's override. Their permissions, operator status and the configured default decide the limit again.

### /signposts reload

Reads and applies `config.yml`, then republishes markers. If storage settings change, it opens the new store and replaces the map's markers with that store's markers. Pending writes to the old store are saved before it closes.

If configuration is invalid or the new store cannot be opened or read, the old settings and store remain active. The command reports the problem.

### /signposts republish

Puts every stored marker back on the map without rereading configuration. The plugin already republishes after BlueMap starts or reloads and restores markers when worlds load. You should only need this command if another plugin or a manual edit has cleared its markers.

## Permissions

| Permission                         | Default   | Allows                                                                                                                       |
| ---------------------------------- | --------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `bluemapsignposts.create`          | everyone  | Add markers within the limit and playtime requirement                                                                        |
| `bluemapsignposts.limit.<n>`       | nobody    | A limit of `n`, such as `bluemapsignposts.limit.25`; the highest held tier wins. `bluemapsignposts.limit.0` means unlimited. |
| `bluemapsignposts.limit.unlimited` | nobody    | No limit                                                                                                                     |
| `bluemapsignposts.bypass`          | operators | Skip the create permission, limit and playtime requirement                                                                   |
| `bluemapsignposts.admin`           | operators | All admin subcommands; a player's own `list` needs no permission                                                             |

You can rename the create and bypass nodes in [config.yml](configuration.md#create-permission-and-bypass-permission).

## Limits

The first applicable rule decides the limit:

1. A per-player override set with `/signposts limit set`.
2. `bluemapsignposts.limit.unlimited`, giving no limit.
3. Operator status when `limits.operators-unlimited` is `true`, giving no limit.
4. The highest held `bluemapsignposts.limit.<n>` tier.
5. `limits.default`, which defaults to 10. A value of `0` means unlimited.

The bypass permission skips limit enforcement entirely. Operators have it by default. To enforce a limit for an operator, remove `bluemapsignposts.bypass` and set `operators-unlimited: false`.

Zero means unlimited in a permission tier or stored override; it does not prohibit markers. The command accepts `unlimited` for an unlimited override. Remove the create permission to stop new markers.

Examples with `limits.default: 10`:

| Player's rules                                             | Shown limit | Reason                                                                        |
| ---------------------------------------------------------- | ----------- | ----------------------------------------------------------------------------- |
| No special rules                                           | 10          | Default                                                                       |
| `bluemapsignposts.limit.25`                                | 25          | Permission tier                                                               |
| `bluemapsignposts.limit.5` and `bluemapsignposts.limit.25` | 25          | Highest tier                                                                  |
| Only `bluemapsignposts.limit.5`                            | 5           | A tier takes precedence even when lower than the default                      |
| `bluemapsignposts.limit.25` and an override of 3           | 3           | Override                                                                      |
| Operator with `operators-unlimited: true`                  | unlimited   | Operator status                                                               |
| Operator with an override of 3                             | 3           | Override, but not enforced while the operator holds `bluemapsignposts.bypass` |

Editing your own marker uses no extra slot. Breaking one frees a slot immediately. Lowering a limit never removes existing markers; it prevents additions until the player is below the limit.

## With LuckPerms

```
# Everyone in the default group may have 5, and VIPs 25.
/lp group default permission set bluemapsignposts.limit.5 true
/lp group vip permission set bluemapsignposts.limit.25 true

# Moderators can manage everyone's markers.
/lp group mod permission set bluemapsignposts.admin true
```

### Stopping a player adding markers

```
/lp user Griefer permission set bluemapsignposts.create false
```

This stops new markers unless the player holds the bypass permission. They keep existing markers and can still rename their own by editing the signs. Use `/signposts remove` to remove those markers.

Their signs and banners still work normally in the game.

### Only some players may add markers

Remove the permission from the default group and grant it to your chosen group:

```
/lp group default permission set bluemapsignposts.create false
/lp group member permission set bluemapsignposts.create true
```

Players with the bypass permission still skip this requirement.
