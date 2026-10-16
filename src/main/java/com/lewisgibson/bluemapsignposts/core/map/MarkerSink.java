package com.lewisgibson.bluemapsignposts.core.map;

import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostKey;

/**
 * Where signposts are shown. The domain publishes and retracts through this; whether anything is
 * listening (BlueMap may not be enabled yet) is the implementation's concern, and both calls must
 * be safe to repeat, because every signpost is re-published each time BlueMap enables.
 */
public interface MarkerSink {

    /**
     * Shows a signpost, replacing any marker already at its block.
     *
     * @param signpost the signpost to show
     */
    void publish(Signpost signpost);

    /**
     * Hides whatever marker is at a block, if any.
     *
     * @param key the block position
     */
    void retract(SignpostKey key);
}
