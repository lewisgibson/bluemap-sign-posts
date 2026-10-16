/**
 * Playtime durations: {@link com.lewisgibson.bluemapsignposts.core.time.PlaytimeDuration} parses
 * and formats human-friendly durations (e.g. {@code 36h}, {@code 1d12h}) as Minecraft game ticks,
 * with strict validation and overflow-safe arithmetic. It is the same gate SpawnBuildRights uses,
 * so a server can hold both plugins to one requirement written the same way.
 */
package com.lewisgibson.bluemapsignposts.core.time;
