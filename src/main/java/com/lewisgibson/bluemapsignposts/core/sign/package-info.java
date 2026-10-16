/**
 * Sign parsing: {@link com.lewisgibson.bluemapsignposts.core.sign.SignpostParser} decides whether
 * the plain text of a sign is a signpost request and, if so, what the marker's label is. It works
 * on lists of strings so it is unit-tested without a server and shared by every route that reads
 * sign text (the edit event, the chunk-load verification).
 */
package com.lewisgibson.bluemapsignposts.core.sign;
