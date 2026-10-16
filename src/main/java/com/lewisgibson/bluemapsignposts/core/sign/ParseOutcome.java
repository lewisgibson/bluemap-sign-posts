package com.lewisgibson.bluemapsignposts.core.sign;

/** What a sign's text turned out to be. */
public enum ParseOutcome {
    /** The trigger line is absent: an ordinary sign, of no interest to the map. */
    NOT_A_SIGNPOST,
    /** The trigger line is present but nothing follows it, so there is no label to show. */
    EMPTY_LABEL,
    /** A well-formed signpost request with a label. */
    SIGNPOST
}
