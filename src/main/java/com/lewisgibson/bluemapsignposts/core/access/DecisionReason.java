package com.lewisgibson.bluemapsignposts.core.access;

/** Why a signpost was allowed or refused. */
public enum DecisionReason {
    /** The player holds the bypass permission, so no other check applies. */
    BYPASS,
    /** Every check passed. */
    ALLOWED,
    /** The player lacks the create permission. */
    NO_PERMISSION,
    /** The player has not yet played for the required time. */
    BELOW_THRESHOLD,
    /** The player already has as many signposts as the per-player limit allows. */
    LIMIT_REACHED
}
