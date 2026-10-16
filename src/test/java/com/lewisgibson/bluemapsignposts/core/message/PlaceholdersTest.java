package com.lewisgibson.bluemapsignposts.core.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Placeholders")
class PlaceholdersTest {

    @Test
    @DisplayName("substitutes a single placeholder")
    void substitutesSingle() {
        assertThat(Placeholders.apply("hi %name%", Map.of("name", "Steve"))).isEqualTo("hi Steve");
    }

    @Test
    @DisplayName("substitutes multiple and repeated placeholders")
    void substitutesMultipleAndRepeated() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("a", "1");
        values.put("b", "2");
        assertThat(Placeholders.apply("%a%-%b%-%a%", values)).isEqualTo("1-2-1");
    }

    @Test
    @DisplayName("leaves unknown placeholders verbatim")
    void leavesUnknownPlaceholders() {
        assertThat(Placeholders.apply("%known% %other%", Map.of("known", "X")))
                .isEqualTo("X %other%");
    }

    @Test
    @DisplayName("returns the template unchanged when there is nothing to substitute")
    void emptyValuesReturnsTemplate() {
        assertThat(Placeholders.apply("no tokens here", Map.of())).isEqualTo("no tokens here");
    }

    @Test
    @DisplayName("inserts a value that itself contains a percent sign")
    void valueContainingPercentIsInserted() {
        assertThat(Placeholders.apply("progress: %pct%", Map.of("pct", "50%")))
                .isEqualTo("progress: 50%");
    }

    @Test
    @DisplayName("does not expand a token that a value contains, whatever the map order")
    void valueContainingTokenIsNotReExpanded() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("label", "%owner%");
        values.put("owner", "Alice");
        assertThat(Placeholders.apply("%label% by %owner%", values)).isEqualTo("%owner% by Alice");
        Map<String, String> reversed = new LinkedHashMap<>();
        reversed.put("owner", "Alice");
        reversed.put("label", "%owner%");
        assertThat(Placeholders.apply("%label% by %owner%", reversed))
                .isEqualTo("%owner% by Alice");
    }

    @Test
    @DisplayName("leaves a lone percent sign and an unterminated token alone")
    void strayPercent() {
        assertThat(Placeholders.apply("50% of %a% and 100%", Map.of("a", "X")))
                .isEqualTo("50% of X and 100%");
    }

    @Test
    @DisplayName("rejects a null template")
    void rejectsNullTemplate() {
        assertThatNullPointerException().isThrownBy(() -> Placeholders.apply(null, Map.of()));
    }

    @Test
    @DisplayName("rejects a null values map")
    void rejectsNullValues() {
        assertThatNullPointerException().isThrownBy(() -> Placeholders.apply("x", null));
    }
}
