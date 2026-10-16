package com.lewisgibson.bluemapsignposts.core.sign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SignpostParser")
class SignpostParserTest {

    private final SignpostParser parser = new SignpostParser("[map]");

    @Test
    @DisplayName("normalises the trigger")
    void normalisesTrigger() {
        assertThat(new SignpostParser("  [MAP] ").parse(List.of("[map]", "Farm")).outcome())
                .isEqualTo(ParseOutcome.SIGNPOST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("rejects a blank trigger")
    void rejectsBlankTrigger(String trigger) {
        assertThatIllegalArgumentException().isThrownBy(() -> new SignpostParser(trigger));
    }

    @Test
    @DisplayName("rejects nulls")
    void rejectsNulls() {
        assertThatNullPointerException().isThrownBy(() -> new SignpostParser(null));
        assertThatNullPointerException().isThrownBy(() -> parser.parse(null));
    }

    @Test
    @DisplayName("an ordinary sign is not a signpost")
    void ordinarySign() {
        assertThat(parser.parse(List.of("Welcome", "to", "spawn", "")).outcome())
                .isEqualTo(ParseOutcome.NOT_A_SIGNPOST);
    }

    @Test
    @DisplayName("an empty sign is not a signpost")
    void emptySign() {
        assertThat(parser.parse(List.of()).outcome()).isEqualTo(ParseOutcome.NOT_A_SIGNPOST);
    }

    @Test
    @DisplayName("a null first line is not a signpost")
    void nullFirstLine() {
        assertThat(parser.parse(Arrays.asList(null, "x")).outcome())
                .isEqualTo(ParseOutcome.NOT_A_SIGNPOST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[map]", "[MAP]", " [Map] "})
    @DisplayName("matches the trigger case-insensitively and ignoring surrounding spaces")
    void matchesTriggerLoosely(String first) {
        ParseResult result = parser.parse(List.of(first, "Iron", "farm", ""));
        assertThat(result.isSignpost()).isTrue();
        assertThat(result.label()).isEqualTo("Iron farm");
    }

    @Test
    @DisplayName("the trigger must be the whole first line")
    void triggerMustBeWholeLine() {
        assertThat(parser.parse(List.of("[map] Iron farm", "", "", "")).outcome())
                .isEqualTo(ParseOutcome.NOT_A_SIGNPOST);
    }

    @Test
    @DisplayName("joins the following non-blank lines with one space, trimmed")
    void joinsLabelLines() {
        ParseResult result = parser.parse(List.of("[map]", "  The  ", "", " Iron farm "));
        assertThat(result.label()).isEqualTo("The Iron farm");
    }

    @Test
    @DisplayName("a null label line reads as blank")
    void nullLabelLine() {
        ParseResult result = parser.parse(Arrays.asList("[map]", null, "Farm", null));
        assertThat(result.label()).isEqualTo("Farm");
    }

    @Test
    @DisplayName("a trigger with nothing under it is an empty label")
    void emptyLabel() {
        assertThat(parser.parse(List.of("[map]", "", "  ", "")).outcome())
                .isEqualTo(ParseOutcome.EMPTY_LABEL);
        assertThat(parser.parse(List.of("[map]")).outcome()).isEqualTo(ParseOutcome.EMPTY_LABEL);
    }

    @Test
    @DisplayName("parseSides prefers the front")
    void parseSidesPrefersFront() {
        ParseResult result =
                parser.parseSides(
                        List.of("[map]", "Front", "", ""), List.of("[map]", "Back", "", ""));
        assertThat(result.label()).isEqualTo("Front");
    }

    @Test
    @DisplayName("parseSides falls through to the back only when the front is an ordinary sign")
    void parseSidesFallsThrough() {
        assertThat(
                        parser.parseSides(
                                        List.of("Hi", "", "", ""), List.of("[map]", "Back", "", ""))
                                .label())
                .isEqualTo("Back");
        assertThat(
                        parser.parseSides(
                                        List.of("[map]", "", "", ""),
                                        List.of("[map]", "Back", "", ""))
                                .outcome())
                .isEqualTo(ParseOutcome.EMPTY_LABEL);
        assertThat(
                        parser.parseSides(List.of("Hi", "", "", ""), List.of("Bye", "", "", ""))
                                .outcome())
                .isEqualTo(ParseOutcome.NOT_A_SIGNPOST);
    }
}
