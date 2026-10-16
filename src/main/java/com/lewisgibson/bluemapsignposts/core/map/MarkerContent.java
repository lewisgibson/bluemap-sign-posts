package com.lewisgibson.bluemapsignposts.core.map;

import com.lewisgibson.bluemapsignposts.core.message.Placeholders;
import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import java.util.Map;
import java.util.Objects;

/**
 * Renders the marker's popup (BlueMap shows it as HTML) from a template, with every value a player
 * wrote escaped first so a sign reading {@code <script>} is text on the map and nothing else.
 */
public final class MarkerContent {

    private MarkerContent() {}

    /**
     * @param template the popup template with {@code %label%}, {@code %owner%}, {@code %x%}, {@code
     *     %y%}, {@code %z%} and {@code %world%} placeholders
     * @param signpost the signpost to render
     * @return the popup HTML
     */
    public static String render(String template, Signpost signpost) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(signpost, "signpost");
        return Placeholders.apply(
                template,
                Map.of(
                        "label", escape(signpost.label()),
                        "owner", escape(signpost.ownerName()),
                        "world", escape(signpost.key().world()),
                        "x", Integer.toString(signpost.key().x()),
                        "y", Integer.toString(signpost.key().y()),
                        "z", Integer.toString(signpost.key().z())));
    }

    /**
     * Escapes the five characters that carry meaning in HTML text and attributes.
     *
     * @param text any text
     * @return the text, safe to place inside HTML
     */
    public static String escape(String text) {
        Objects.requireNonNull(text, "text");
        StringBuilder out = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(character);
            }
        }
        return out.toString();
    }
}
