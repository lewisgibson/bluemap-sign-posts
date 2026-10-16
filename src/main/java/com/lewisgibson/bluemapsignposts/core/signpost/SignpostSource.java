package com.lewisgibson.bluemapsignposts.core.signpost;

/** The kind of block a signpost was read from. */
public enum SignpostSource {
    /** A sign (standing, wall or hanging) whose first line is the trigger. */
    SIGN,
    /** A banner carrying a custom name given in an anvil. */
    BANNER
}
