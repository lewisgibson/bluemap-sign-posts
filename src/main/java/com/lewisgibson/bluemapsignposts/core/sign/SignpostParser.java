package com.lewisgibson.bluemapsignposts.core.sign;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Decides whether a sign's text asks for a map marker, and extracts the label if so.
 *
 * <p>The trigger is the whole of the first line, compared case-insensitively after trimming, so
 * {@code [map]}, {@code [MAP]} and {@code " [Map] "} all match. The label is every following line
 * that is not blank, trimmed and joined with a single space, so a name can flow over the sign's
 * remaining lines however the writer likes to break it. The trigger deliberately lives on the FIRST
 * line rather than the last: it is the line a passer-by reads first, it is where every published
 * sign-marker plugin puts it, and it leaves the three lines under it for the name.
 *
 * <p>Pure and platform-free: it takes plain strings, so the adapter is responsible for flattening
 * whatever rich text the server hands it into plain text first.
 */
public final class SignpostParser {

    private final String trigger;

    /**
     * @param trigger the exact first-line text that marks a sign as a signpost request, e.g. {@code
     *     [map]}; matched case-insensitively and ignoring surrounding whitespace
     * @throws IllegalArgumentException if {@code trigger} is blank
     */
    public SignpostParser(String trigger) {
        Objects.requireNonNull(trigger, "trigger");
        String normalised = normalise(trigger);
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException("trigger must not be blank");
        }
        this.trigger = normalised;
    }

    /**
     * Parses one side of a sign.
     *
     * @param lines the side's lines as plain text, first line first; a missing line reads as blank
     * @return the outcome and the label
     */
    public ParseResult parse(List<String> lines) {
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty() || !isTrigger(lines.get(0))) {
            return ParseResult.notASignpost();
        }
        StringBuilder label = new StringBuilder();
        for (int index = 1; index < lines.size(); index++) {
            String piece = lines.get(index) == null ? "" : lines.get(index).trim();
            if (piece.isEmpty()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(piece);
        }
        if (label.isEmpty()) {
            return ParseResult.emptyLabel();
        }
        return ParseResult.signpost(label.toString());
    }

    /**
     * Parses a two-sided sign. The front is authoritative whenever it carries the trigger at all (a
     * front that says {@code [map]} with nothing under it is an empty label, not a fall-through to
     * the back); only a front that is an ordinary sign lets the back decide.
     *
     * @param front the front side's lines
     * @param back the back side's lines
     * @return the outcome and the label
     */
    public ParseResult parseSides(List<String> front, List<String> back) {
        ParseResult result = parse(front);
        if (result.outcome() != ParseOutcome.NOT_A_SIGNPOST) {
            return result;
        }
        return parse(back);
    }

    private boolean isTrigger(String line) {
        return line != null && normalise(line).equals(trigger);
    }

    private static String normalise(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
