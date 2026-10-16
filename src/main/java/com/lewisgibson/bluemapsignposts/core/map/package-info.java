/**
 * The map-facing port: {@link com.lewisgibson.bluemapsignposts.core.map.MarkerSink} is what the
 * domain publishes signposts through (the adapter implements it over BlueMapAPI, tests with a
 * fake), and {@link com.lewisgibson.bluemapsignposts.core.map.MarkerContent} renders the marker
 * popup from a template with every player-supplied value HTML-escaped.
 */
package com.lewisgibson.bluemapsignposts.core.map;
