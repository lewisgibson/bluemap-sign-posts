package com.lewisgibson.bluemapsignposts.core.signpost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.lewisgibson.bluemapsignposts.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Signpost")
class SignpostTest {

    private static final SignpostKey KEY = new SignpostKey("world", 1, 2, 3);

    @Test
    @DisplayName("exposes its fields when valid")
    void buildsValid() {
        Signpost signpost =
                new Signpost(KEY, "Farm", Fixtures.ALICE, "Alice", SignpostSource.BANNER, 5L);
        assertThat(signpost.key()).isEqualTo(KEY);
        assertThat(signpost.label()).isEqualTo("Farm");
        assertThat(signpost.owner()).isEqualTo(Fixtures.ALICE);
        assertThat(signpost.ownerName()).isEqualTo("Alice");
        assertThat(signpost.source()).isEqualTo(SignpostSource.BANNER);
        assertThat(signpost.createdAtEpochMillis()).isEqualTo(5L);
        assertThat(SignpostSource.valueOf("SIGN")).isEqualTo(SignpostSource.SIGN);
    }

    @Test
    @DisplayName("rejects nulls")
    void rejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new Signpost(
                                        null,
                                        "Farm",
                                        Fixtures.ALICE,
                                        "Alice",
                                        SignpostSource.SIGN,
                                        0));
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new Signpost(
                                        KEY,
                                        null,
                                        Fixtures.ALICE,
                                        "Alice",
                                        SignpostSource.SIGN,
                                        0));
        assertThatNullPointerException()
                .isThrownBy(() -> new Signpost(KEY, "Farm", null, "Alice", SignpostSource.SIGN, 0));
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new Signpost(
                                        KEY, "Farm", Fixtures.ALICE, null, SignpostSource.SIGN, 0));
        assertThatNullPointerException()
                .isThrownBy(() -> new Signpost(KEY, "Farm", Fixtures.ALICE, "Alice", null, 0));
    }

    @Test
    @DisplayName("rejects a blank, untrimmed or over-long label")
    void rejectsBadLabels() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new Signpost(
                                        KEY, " ", Fixtures.ALICE, "Alice", SignpostSource.SIGN, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new Signpost(
                                        KEY,
                                        " Farm",
                                        Fixtures.ALICE,
                                        "Alice",
                                        SignpostSource.SIGN,
                                        0));
        String tooLong = "x".repeat(Signpost.MAX_LABEL_LENGTH + 1);
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new Signpost(
                                        KEY,
                                        tooLong,
                                        Fixtures.ALICE,
                                        "Alice",
                                        SignpostSource.SIGN,
                                        0));
    }

    @Test
    @DisplayName("rejects a blank owner name and a negative timestamp")
    void rejectsBadOwnerAndTime() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new Signpost(
                                        KEY, "Farm", Fixtures.ALICE, "", SignpostSource.SIGN, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new Signpost(
                                        KEY,
                                        "Farm",
                                        Fixtures.ALICE,
                                        "Alice",
                                        SignpostSource.SIGN,
                                        -1));
    }

    @Test
    @DisplayName("clip leaves a short label alone and cuts a long one to the ceiling, trimmed")
    void clips() {
        assertThat(Signpost.clip("Farm")).isEqualTo("Farm");
        String exact = "x".repeat(Signpost.MAX_LABEL_LENGTH);
        assertThat(Signpost.clip(exact)).isEqualTo(exact);
        String spaced = "x".repeat(Signpost.MAX_LABEL_LENGTH - 1) + " yz";
        assertThat(Signpost.clip(spaced)).isEqualTo("x".repeat(Signpost.MAX_LABEL_LENGTH - 1));
        assertThatNullPointerException().isThrownBy(() -> Signpost.clip(null));
    }

    @Test
    @DisplayName("clip never leaves half of a surrogate pair")
    void clipsOnWholeCodePoints() {
        String label = "x".repeat(Signpost.MAX_LABEL_LENGTH - 1) + "\uD83D\uDE00 tail";
        assertThat(Signpost.clip(label)).isEqualTo("x".repeat(Signpost.MAX_LABEL_LENGTH - 1));
        String whole = "x".repeat(Signpost.MAX_LABEL_LENGTH - 2) + "\uD83D\uDE00 tail";
        assertThat(Signpost.clip(whole))
                .isEqualTo("x".repeat(Signpost.MAX_LABEL_LENGTH - 2) + "\uD83D\uDE00");
    }
}
