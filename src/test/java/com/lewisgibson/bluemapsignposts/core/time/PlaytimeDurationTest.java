package com.lewisgibson.bluemapsignposts.core.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PlaytimeDuration")
class PlaytimeDurationTest {

    @Test
    @DisplayName("tick constants are correct")
    void tickConstants() {
        assertThat(PlaytimeDuration.TICKS_PER_SECOND).isEqualTo(20L);
        assertThat(PlaytimeDuration.TICKS_PER_MINUTE).isEqualTo(1_200L);
        assertThat(PlaytimeDuration.TICKS_PER_HOUR).isEqualTo(72_000L);
        assertThat(PlaytimeDuration.TICKS_PER_DAY).isEqualTo(1_728_000L);
    }

    @ParameterizedTest
    @CsvSource({
        "36h, 2592000",
        "36H, 2592000",
        "1d, 1728000",
        "90m, 108000",
        "45s, 900",
        "1d12h, 2592000",
        "'1d 12h', 2592000",
        "2d3h4m5s, 3676900",
        "'  2h  ', 144000",
    })
    @DisplayName("parseTicks parses single and compound durations")
    void parseTicksParses(String input, long expected) {
        assertThat(PlaytimeDuration.parseTicks(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("36h and 1d12h are the same number of ticks")
    void equivalentDurationsParseEqually() {
        assertThat(PlaytimeDuration.parseTicks("36h"))
                .isEqualTo(PlaytimeDuration.parseTicks("1d12h"));
    }

    @Test
    @DisplayName("parseTicks rejects null")
    void parseRejectsNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> PlaytimeDuration.parseTicks(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "36", "36x", "abc", "h", "36hh", "-5h", "3.5h", "36h!"})
    @DisplayName("parseTicks rejects malformed input")
    void parseRejectsMalformed(String input) {
        assertThatIllegalArgumentException().isThrownBy(() -> PlaytimeDuration.parseTicks(input));
    }

    @Test
    @DisplayName("parseTicks throws on overflow rather than wrapping")
    void parseThrowsOnOverflow() {
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> PlaytimeDuration.parseTicks("9223372036854775807s"));
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0s",
        "900, 45s",
        "144000, 2h",
        "864000, 12h",
        "1728000, 1d",
        "2592000, 1d12h",
        "3676900, 2d3h4m5s",
    })
    @DisplayName("format renders the compact human form, largest units first")
    void formatRenders(long ticks, String expected) {
        assertThat(PlaytimeDuration.format(ticks)).isEqualTo(expected);
    }

    @Test
    @DisplayName("format normalises 90 minutes into 1h30m")
    void formatNormalisesMinutes() {
        assertThat(PlaytimeDuration.format(108_000L)).isEqualTo("1h30m");
    }

    @Test
    @DisplayName("format rejects negative ticks")
    void formatRejectsNegative() {
        assertThatIllegalArgumentException().isThrownBy(() -> PlaytimeDuration.format(-1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1d12h", "2d3h4m5s", "45s", "0s", "12h", "1d"})
    @DisplayName("format is the inverse of parseTicks for canonical forms")
    void formatRoundTrips(String canonical) {
        assertThat(PlaytimeDuration.format(PlaytimeDuration.parseTicks(canonical)))
                .isEqualTo(canonical);
    }
}
