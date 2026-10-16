package com.lewisgibson.bluemapsignposts.core.signpost;

import java.util.Objects;
import java.util.UUID;

/**
 * One marker request: where it is, what it says, who placed it and from what.
 *
 * <p>Immutable and validated in the constructor, so an unlabelled or over-long signpost can never
 * exist. The label ceiling is generous against what the game can produce (three sign lines or an
 * anvil name) and exists so a hand-edited store cannot push a paragraph onto the map.
 *
 * @param key the block position
 * @param label the marker text, trimmed and non-empty, at most {@link #MAX_LABEL_LENGTH}
 * @param owner the placing player's unique id
 * @param ownerName the placing player's name at the time, for display and lookups without a profile
 *     fetch
 * @param source whether it came from a sign or a banner
 * @param createdAtEpochMillis when it was placed, in milliseconds since the epoch
 */
public record Signpost(
        SignpostKey key,
        String label,
        UUID owner,
        String ownerName,
        SignpostSource source,
        long createdAtEpochMillis) {

    /** The longest label a signpost may carry. */
    public static final int MAX_LABEL_LENGTH = 100;

    public Signpost {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(source, "source");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        if (!label.equals(label.trim())) {
            throw new IllegalArgumentException("label must be trimmed, was '" + label + "'");
        }
        if (label.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException(
                    "label must be at most "
                            + MAX_LABEL_LENGTH
                            + " characters, was "
                            + label.length());
        }
        if (ownerName.isBlank()) {
            throw new IllegalArgumentException("ownerName must not be blank");
        }
        if (createdAtEpochMillis < 0) {
            throw new IllegalArgumentException(
                    "createdAtEpochMillis must be >= 0, was " + createdAtEpochMillis);
        }
    }

    /**
     * Clips a label to {@link #MAX_LABEL_LENGTH} so anything the game hands over fits the record.
     *
     * @param label the raw label, already trimmed
     * @return the label, cut to the ceiling if it was longer
     */
    public static String clip(String label) {
        Objects.requireNonNull(label, "label");
        if (label.length() <= MAX_LABEL_LENGTH) {
            return label;
        }
        int end = MAX_LABEL_LENGTH;
        if (Character.isHighSurrogate(label.charAt(end - 1))) {
            end--;
        }
        return label.substring(0, end).trim();
    }
}
