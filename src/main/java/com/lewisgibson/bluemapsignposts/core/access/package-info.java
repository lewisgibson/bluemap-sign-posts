/**
 * Who may put a marker on the map, and how many: {@link
 * com.lewisgibson.bluemapsignposts.core.access.LimitResolver} turns an admin's {@link
 * com.lewisgibson.bluemapsignposts.core.access.LimitOverride}, the permission nodes and the config
 * default into one {@link com.lewisgibson.bluemapsignposts.core.access.EffectiveLimit} with its
 * {@link com.lewisgibson.bluemapsignposts.core.access.LimitSource}, and {@link
 * com.lewisgibson.bluemapsignposts.core.access.SignpostPolicy} decides from that, a permission
 * flag, a bypass flag and accumulated playtime, returning an {@link
 * com.lewisgibson.bluemapsignposts.core.access.AccessResult} whose {@link
 * com.lewisgibson.bluemapsignposts.core.access.DecisionReason} picks the message.
 */
package com.lewisgibson.bluemapsignposts.core.access;
