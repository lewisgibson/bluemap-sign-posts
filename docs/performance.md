# Performance

The plugin handles every block break, block placement and chunk load, even though sign edits are rare. Those common paths must stay small beside a 50 ms server tick, or 50,000,000 ns.

## Design

A block break first calls `isEmptyIn(world)`: one hash lookup on the world name, with no allocation. Only a world containing signposts needs a block key and second lookup. Banner placement checks the enabled flag and block tag before reading the item's name.

The registry indexes signposts by world and chunk. A chunk-load miss needs one lookup for the world and one for the chunk, returning a shared empty list. Only chunks containing stored signposts read blocks, and they read only the stored positions.

There is no per-tick task or repeating timer. On Folia, chunk validation is scheduled on the chunk's own region thread. Other work is driven by events, commands and lifecycle changes.

Database writes are queued on the plugin's storage thread. Signpost additions and removals are expected only a few times an hour in ordinary use; each writes one row. Block events and chunk checks read the in-memory registry without opening database connections.

Marker objects are built for actual marker changes and republishing, not for every unrelated block event. Republishing also handles BlueMap enabling or reloading, plugin reloads, explicit commands and worlds loading after startup.

## Measurements

JMH 1.37 benchmarks measure the core hot paths with `./gradlew jmh`. These average times use a registry containing 1,000 signposts across 1,000 chunks.

| Benchmark           | ns/op | Path                                                                |
| ------------------- | ----: | ------------------------------------------------------------------- |
| `emptyWorldCheck`   |  ~7.9 | Block event in a world without signposts                            |
| `blockLookupMiss`   |  ~8.8 | Block lookup miss in a world containing signposts                   |
| `chunkLookupMiss`   | ~28.6 | Chunk lookup miss in a world containing signposts                   |
| `chunkLookupHit`    | ~34.2 | Lookup of a chunk containing one signpost, before reading its block |
| `parseOrdinarySign` |  ~8.6 | Sign rejected on its first line                                     |
| `parseSignpost`     | ~18.7 | Signpost parsed with a name built from three lines                  |

Hardware changes the absolute numbers. Registry methods are synchronised, and an uncontended monitor accounts for most of a lookup's cost. This is a deliberate trade-off for infrequent signpost changes; these measurements do not show a need for a lock-free registry.

## Reading the results

At these measured lookup costs, 10,000 block breaks in a world containing signposts account for about 90 microseconds, or 0.2% of a tick. A burst of 1,000 chunk lookups accounts for about 30 microseconds.

These are estimates for the measured core paths, not timings for complete server events. They exclude reading blocks, creating markers and database writes. Chunk validation reads world state only when a chunk contains stored signposts, and only at those positions.

The common lookup costs are negligible beside the tick budget. Keep that property when adding event handling, and benchmark changes to the registry or parser.
