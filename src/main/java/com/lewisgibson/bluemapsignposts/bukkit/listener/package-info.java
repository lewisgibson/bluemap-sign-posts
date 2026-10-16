/**
 * Event listeners: {@link com.lewisgibson.bluemapsignposts.bukkit.listener.SignpostListener}
 * creates a signpost from a sign edit or a named banner placement, removes it when the block goes
 * (broken, blown up, pushed, or knocked off its support), and checks stored signposts against the
 * world as chunks load so nothing that happened while a chunk was unloaded leaves a stale marker.
 */
package com.lewisgibson.bluemapsignposts.bukkit.listener;
