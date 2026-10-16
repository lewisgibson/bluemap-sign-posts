package com.lewisgibson.bluemapsignposts.core.signpost;

import com.lewisgibson.bluemapsignposts.core.map.MarkerSink;
import java.util.LinkedHashMap;
import java.util.Map;

/** A sink that keeps what it was told to show, for the service tests. */
final class FakeMarkerSink implements MarkerSink {

    final Map<SignpostKey, Signpost> shown = new LinkedHashMap<>();
    int publishes;

    @Override
    public void publish(Signpost signpost) {
        shown.put(signpost.key(), signpost);
        publishes++;
    }

    @Override
    public void retract(SignpostKey key) {
        shown.remove(key);
    }
}
