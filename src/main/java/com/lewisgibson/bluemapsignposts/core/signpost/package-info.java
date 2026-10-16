/**
 * The signpost model and its keeping: a {@link
 * com.lewisgibson.bluemapsignposts.core.signpost.Signpost} is one marker request (where, what it
 * says, who placed it, from a sign or a banner), keyed by its {@link
 * com.lewisgibson.bluemapsignposts.core.signpost.SignpostKey} block position; the {@link
 * com.lewisgibson.bluemapsignposts.core.signpost.SignpostRegistry} is the thread-safe in-memory
 * copy of the {@link com.lewisgibson.bluemapsignposts.core.signpost.SignpostStore} with the
 * per-owner, per-world and per-chunk views the adapter needs; and the {@link
 * com.lewisgibson.bluemapsignposts.core.signpost.SignpostService} is the one writer that keeps
 * registry, store and map in step.
 */
package com.lewisgibson.bluemapsignposts.core.signpost;
