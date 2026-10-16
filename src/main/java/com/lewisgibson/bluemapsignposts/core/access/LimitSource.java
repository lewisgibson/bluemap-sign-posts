package com.lewisgibson.bluemapsignposts.core.access;

/** Which rule decided a player's effective limit, highest priority first. */
public enum LimitSource {
    /** An admin set it by hand with {@code /signposts limit set}. */
    OVERRIDE,
    /** The player holds the {@code limit.unlimited} permission node. */
    UNLIMITED_PERMISSION,
    /** The player is an operator and operators are unlimited by config. */
    OPERATOR,
    /** The highest {@code limit.<n>} permission tier the player holds. */
    TIER_PERMISSION,
    /** Nothing above applied; the config default. */
    DEFAULT
}
