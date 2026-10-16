package com.lewisgibson.bluemapsignposts.core.time;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats human-friendly playtime durations expressed in Minecraft game ticks (20 ticks
 * per second).
 *
 * <p>Accepts compound values such as {@code "36h"}, {@code "1d12h"}, {@code "1d 12h"}, {@code
 * "90m"}, and {@code "45s"}. Recognised units are {@code d} (days), {@code h} (hours), {@code m}
 * (minutes), and {@code s} (seconds); matching is case-insensitive and internal whitespace is
 * ignored. Parsing is strict: any stray or unknown character causes an {@link
 * IllegalArgumentException} rather than a silently truncated value, so operator typos surface
 * immediately.
 */
public final class PlaytimeDuration {

    /** Ticks in one second of real time (the Minecraft server target tick rate). */
    public static final long TICKS_PER_SECOND = 20L;

    /** Ticks in one minute. */
    public static final long TICKS_PER_MINUTE = 60L * TICKS_PER_SECOND;

    /** Ticks in one hour. */
    public static final long TICKS_PER_HOUR = 60L * TICKS_PER_MINUTE;

    /** Ticks in one day. */
    public static final long TICKS_PER_DAY = 24L * TICKS_PER_HOUR;

    private static final Pattern WHOLE =
            Pattern.compile("(?:\\d+[dhms])+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN =
            Pattern.compile("(\\d+)([dhms])", Pattern.CASE_INSENSITIVE);

    private PlaytimeDuration() {}

    /**
     * Parses a compound duration string into ticks.
     *
     * @param value e.g. {@code "36h"}, {@code "1d12h"}, {@code "90m"}
     * @return the duration in ticks
     * @throws IllegalArgumentException if {@code value} is {@code null}, blank, contains no unit
     *     tokens, or contains any unexpected character
     * @throws ArithmeticException if the total overflows a {@code long}
     */
    public static long parseTicks(String value) {
        if (value == null) {
            throw new IllegalArgumentException("duration must not be null");
        }
        String compact = value.replaceAll("\\s+", "");
        if (compact.isEmpty()) {
            throw new IllegalArgumentException("duration must not be blank");
        }
        if (!WHOLE.matcher(compact).matches()) {
            throw new IllegalArgumentException(
                    "invalid duration '" + value + "' (expected e.g. '36h', '1d12h', '90m')");
        }
        Matcher matcher = TOKEN.matcher(compact);
        long ticks = 0L;
        while (matcher.find()) {
            long amount = Long.parseLong(matcher.group(1));
            long unitTicks =
                    switch (Character.toLowerCase(matcher.group(2).charAt(0))) {
                        case 'd' -> Math.multiplyExact(amount, TICKS_PER_DAY);
                        case 'h' -> Math.multiplyExact(amount, TICKS_PER_HOUR);
                        case 'm' -> Math.multiplyExact(amount, TICKS_PER_MINUTE);
                        // Only 's' reaches the default: the regex above allows just d, h, m and s,
                        // and folding seconds in here avoids an unreachable branch.
                        default -> Math.multiplyExact(amount, TICKS_PER_SECOND);
                    };
            ticks = Math.addExact(ticks, unitTicks);
        }
        return ticks;
    }

    /**
     * Formats a tick count back into the compact human form, using the largest units first and
     * omitting any zero component (e.g. {@code 36h}, {@code 1d12h}). Zero formats as {@code "0s"}.
     *
     * @param ticks the duration in ticks; must be {@code >= 0}
     * @return the compact string form
     * @throws IllegalArgumentException if {@code ticks} is negative
     */
    public static String format(long ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be >= 0, was " + ticks);
        }
        long remaining = ticks;
        long days = remaining / TICKS_PER_DAY;
        remaining %= TICKS_PER_DAY;
        long hours = remaining / TICKS_PER_HOUR;
        remaining %= TICKS_PER_HOUR;
        long minutes = remaining / TICKS_PER_MINUTE;
        remaining %= TICKS_PER_MINUTE;
        long seconds = remaining / TICKS_PER_SECOND;

        StringBuilder out = new StringBuilder();
        if (days > 0) {
            out.append(days).append('d');
        }
        if (hours > 0) {
            out.append(hours).append('h');
        }
        if (minutes > 0) {
            out.append(minutes).append('m');
        }
        if (seconds > 0) {
            out.append(seconds).append('s');
        }
        return out.isEmpty() ? "0s" : out.toString();
    }
}
