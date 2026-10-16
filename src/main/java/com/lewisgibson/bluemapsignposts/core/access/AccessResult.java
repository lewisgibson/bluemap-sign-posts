package com.lewisgibson.bluemapsignposts.core.access;

import java.util.Objects;

/**
 * The outcome of a create check: whether the signpost may be placed, and the {@link DecisionReason}
 * that explains it (used to pick the right player-facing message).
 *
 * @param allowed whether the signpost may be placed
 * @param reason the reason for the decision
 */
public record AccessResult(boolean allowed, DecisionReason reason) {

    public AccessResult {
        Objects.requireNonNull(reason, "reason");
    }

    /**
     * @return an allowed result with the given reason
     */
    public static AccessResult allowed(DecisionReason reason) {
        return new AccessResult(true, reason);
    }

    /**
     * @return a denied result with the given reason
     */
    public static AccessResult denied(DecisionReason reason) {
        return new AccessResult(false, reason);
    }
}
