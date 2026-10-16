package com.lewisgibson.bluemapsignposts.core.sign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ParseResult")
class ParseResultTest {

    @Test
    @DisplayName("the shared non-signpost results carry an empty label")
    void sharedResults() {
        assertThat(ParseResult.notASignpost().outcome()).isEqualTo(ParseOutcome.NOT_A_SIGNPOST);
        assertThat(ParseResult.notASignpost().label()).isEmpty();
        assertThat(ParseResult.notASignpost().isSignpost()).isFalse();
        assertThat(ParseResult.emptyLabel().outcome()).isEqualTo(ParseOutcome.EMPTY_LABEL);
        assertThat(ParseResult.emptyLabel().isSignpost()).isFalse();
        assertThat(ParseResult.notASignpost()).isSameAs(ParseResult.notASignpost());
    }

    @Test
    @DisplayName("a signpost result carries its label")
    void signpostResult() {
        ParseResult result = ParseResult.signpost("Iron farm");
        assertThat(result.outcome()).isEqualTo(ParseOutcome.SIGNPOST);
        assertThat(result.label()).isEqualTo("Iron farm");
        assertThat(result.isSignpost()).isTrue();
    }

    @Test
    @DisplayName("rejects a signpost with an empty label")
    void rejectsEmptySignpostLabel() {
        assertThatIllegalArgumentException().isThrownBy(() -> ParseResult.signpost(""));
    }

    @Test
    @DisplayName("rejects a non-signpost carrying a label")
    void rejectsLabelledNonSignpost() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ParseResult(ParseOutcome.EMPTY_LABEL, "x"));
    }

    @Test
    @DisplayName("rejects nulls")
    void rejectsNulls() {
        assertThatNullPointerException().isThrownBy(() -> new ParseResult(null, ""));
        assertThatNullPointerException()
                .isThrownBy(() -> new ParseResult(ParseOutcome.SIGNPOST, null));
    }

    @Test
    @DisplayName("the outcome enum lists the three outcomes")
    void outcomes() {
        assertThat(ParseOutcome.values())
                .containsExactly(
                        ParseOutcome.NOT_A_SIGNPOST,
                        ParseOutcome.EMPTY_LABEL,
                        ParseOutcome.SIGNPOST);
        assertThat(ParseOutcome.valueOf("SIGNPOST")).isEqualTo(ParseOutcome.SIGNPOST);
    }
}
