/**
 * BlueMapSignPosts: allows players to add BlueMap markers using signs and banners.
 *
 * <p>A player writes a sign whose first line is the trigger (by default {@code [map]}) and whose
 * remaining lines name the place, or places a banner they renamed in an anvil, and a marker with
 * that name appears on the live web map at the block's position. Breaking the block removes it.
 * Nothing about the game changes: the sign and the banner behave exactly as they do in vanilla.
 *
 * <p>The code is split into three layers:
 *
 * <ul>
 *   <li>the {@code core} packages ({@code sign}, {@code signpost}, {@code access}, {@code time},
 *       {@code message}, {@code map}, {@code settings}) - the pure, platform-free logic: sign
 *       parsing, the signpost model, registry and service, the store interface, the who-may-create
 *       policy and the marker content;
 *   <li>the {@link com.lewisgibson.bluemapsignposts.storage storage} package - the JDBC store over
 *       SQLite, PostgreSQL or MySQL and the thread that writes it; and
 *   <li>the {@link com.lewisgibson.bluemapsignposts.bukkit bukkit} packages - a thin adapter
 *       binding that logic to the Bukkit/Paper API (events, configuration, the command, messaging)
 *       and to BlueMapAPI (the marker sets themselves).
 * </ul>
 *
 * <p>Keeping the domain free of any server or map API is what lets it be tested without a server.
 */
package com.lewisgibson.bluemapsignposts;
