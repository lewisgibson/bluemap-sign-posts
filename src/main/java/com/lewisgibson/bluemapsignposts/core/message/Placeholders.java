package com.lewisgibson.bluemapsignposts.core.message;

import java.util.Map;
import java.util.Objects;

/**
 * Substitutes {@code %name%} placeholders in a message template.
 *
 * <p>Pure and platform-free: it operates on plain strings so it can be unit-tested without a server
 * and reused by any platform adapter. Colour/markup (MiniMessage) is resolved later, by the
 * adapter, after placeholders have been applied.
 */
public final class Placeholders {

    private Placeholders() {}

    /**
     * Replaces every {@code %key%} occurrence in {@code template} with its mapped value, in one
     * left-to-right pass, so a value that itself contains a token is inserted as written and the
     * result never depends on the order of {@code values}.
     *
     * <p>Unknown placeholders are left verbatim so a typo in a template surfaces to the operator
     * rather than silently rendering as an empty string.
     *
     * @param template the template containing {@code %key%} tokens
     * @param values the placeholder values, keyed by name (without the surrounding {@code %})
     * @return the template with all known placeholders substituted
     */
    public static String apply(String template, Map<String, String> values) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(values, "values");
        StringBuilder result = new StringBuilder(template.length());
        int cursor = 0;
        while (cursor < template.length()) {
            char current = template.charAt(cursor);
            int end = current == '%' ? template.indexOf('%', cursor + 1) : -1;
            String value = end < 0 ? null : values.get(template.substring(cursor + 1, end));
            if (value == null) {
                result.append(current);
                cursor++;
            } else {
                result.append(value);
                cursor = end + 1;
            }
        }
        return result.toString();
    }
}
