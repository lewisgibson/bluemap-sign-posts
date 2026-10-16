package com.lewisgibson.bluemapsignposts.core.sign;

import java.util.Objects;

/**
 * The result of parsing a sign: its {@link ParseOutcome} and, for a {@link ParseOutcome#SIGNPOST},
 * the label. The label is the empty string for every other outcome.
 *
 * @param outcome what the text turned out to be
 * @param label the marker label, non-empty exactly when {@code outcome} is {@code SIGNPOST}
 */
public record ParseResult(ParseOutcome outcome, String label) {

    private static final ParseResult NOT_A_SIGNPOST =
            new ParseResult(ParseOutcome.NOT_A_SIGNPOST, "");
    private static final ParseResult EMPTY_LABEL = new ParseResult(ParseOutcome.EMPTY_LABEL, "");

    public ParseResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(label, "label");
        if ((outcome == ParseOutcome.SIGNPOST) == label.isEmpty()) {
            throw new IllegalArgumentException(
                    "label must be non-empty exactly when the outcome is SIGNPOST, was "
                            + outcome
                            + " with '"
                            + label
                            + "'");
        }
    }

    /**
     * @return the shared result for an ordinary sign
     */
    public static ParseResult notASignpost() {
        return NOT_A_SIGNPOST;
    }

    /**
     * @return the shared result for a trigger line with nothing under it
     */
    public static ParseResult emptyLabel() {
        return EMPTY_LABEL;
    }

    /**
     * @param label the non-empty label
     * @return a signpost result carrying {@code label}
     */
    public static ParseResult signpost(String label) {
        return new ParseResult(ParseOutcome.SIGNPOST, label);
    }

    /**
     * @return whether this is a usable signpost request
     */
    public boolean isSignpost() {
        return outcome == ParseOutcome.SIGNPOST;
    }
}
