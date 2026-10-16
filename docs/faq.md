# FAQ

## Do players need to install anything?

No. This is a server plugin. You can use ordinary signs and banners in an unmodded game.

## Does it change the game?

No. It observes what the game allows without cancelling events, changing blocks or adding anything to the world. If you are refused a marker, your sign or banner still behaves normally.

## Does it slow the server down?

Frequent block and chunk events usually need one or two memory lookups. The core benchmarks measure these in nanoseconds, and database writes run on a separate thread. Sign edits are rare, and there is no per-tick task. See [performance](performance.md) for measurements and their limits.

## Can I change `[map]` to something else?

Yes. Set [`sign.trigger`](configuration.md#signtrigger), for example to `[poi]` or `[waypoint]`.

## Can players choose the icon or the colour of their marker?

No. All signposts use BlueMap's default pin and share one layer. You can change the popup's HTML through [`marker.detail`](configuration.md#detail).

## Can I hide who placed a marker?

Yes. Set `marker.detail: '%label%'` to show only the place's name. Admins can still see owners through `/signposts list`.

## Who owns a marker if someone else edits the sign?

The last player to edit it, provided they pass the checks for adding a marker. It then counts towards their limit. If they do not pass, the marker is removed. See [renaming a place](usage.md#renaming-a-place). Wax the sign with honeycomb to prevent edits.

## Does it work in the Nether and the End?

Yes. Signposts appear on every map BlueMap has for their world, including each dimension's own map. The Overworld, Nether and End are tested on a real server.

## What about worlds loaded after startup?

Their markers are restored when they load. Unloading a world keeps its signposts in the store and `/signposts list`, and loading it again puts them back on the map. This is tested with a world created, unloaded and reloaded while a real server runs.

A world without a BlueMap map keeps its signposts but cannot display them until BlueMap maps it.

## What happens to markers when I restart the server or BlueMap?

They return automatically. The plugin rebuilds its live marker sets from its own store whenever BlueMap becomes ready, including after `/bluemap reload`. See [keeping markers across restarts](bluemap-markers.md#keeping-markers-across-restarts).

## Does it work with BlueMap's SQL storage?

Yes. The plugin uses BlueMapAPI and never accesses BlueMap's storage directly. BlueMap saves the markers with each map.

File, SQLite, PostgreSQL with TLS `verify-full`, and MariaDB storage were tested on a real server. MySQL is supported by BlueMap through the same mechanism but was not tested. No plugin setting is needed; see [BlueMap storage examples](bluemap-markers.md#bluemaps-storage).

## Which databases can it keep its markers in?

SQLite by default, which needs no setup. PostgreSQL, MySQL and MariaDB work too. See [storage](storage.md).

## I already have `[map]` signs from before I installed it. Do they show up?

Only after someone edits them. Right-click each sign and click Done without changing its text. It then becomes a marker if you meet the permission, limit and playtime checks.

## Can I add a marker for a place without a sign?

This plugin uses player-written signs and renamed banners. For fixed markers without those blocks, use BlueMap's own [marker config](https://bluemap.bluecolored.de/wiki/customization/Markers.html).

## Does it work with Dynmap, squaremap or Pl3xMap?

No. It works only with BlueMap.

## How is this different from other sign-marker plugins?

BlueMapSignPosts keeps its own record of markers, restores them after BlueMap reloads, and follows edits, breaking, explosions, pistons and loss of support. Chunk-load checks remove stored markers whose blocks no longer match.

It also supports renamed banners, permission-based limits, per-player admin overrides, an optional playtime requirement and Folia. Player text is handled as plain text in chat and escaped in map popups.

## Is it free?

Yes. It is open source under the [GPL-3.0](../LICENSE).
