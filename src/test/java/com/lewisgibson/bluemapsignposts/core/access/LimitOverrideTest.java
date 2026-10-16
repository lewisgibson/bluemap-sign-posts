package com.lewisgibson.bluemapsignposts.core.access;

import static com.lewisgibson.bluemapsignposts.Fixtures.ALICE;
import static com.lewisgibson.bluemapsignposts.Fixtures.T1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LimitOverride")
class LimitOverrideTest {

    @Test
    @DisplayName("exposes its fields and knows when it is unlimited")
    void fields() {
        LimitOverride override = new LimitOverride(ALICE, "Alice", 25, "Bob", T1);
        assertThat(override.player()).isEqualTo(ALICE);
        assertThat(override.playerName()).isEqualTo("Alice");
        assertThat(override.limit()).isEqualTo(25);
        assertThat(override.actor()).isEqualTo("Bob");
        assertThat(override.at()).isEqualTo(T1);
        assertThat(override.isUnlimited()).isFalse();
        assertThat(new LimitOverride(ALICE, "Alice", 0, "console", T1).isUnlimited()).isTrue();
    }

    @Test
    @DisplayName("rejects nulls, blanks and a negative limit")
    void rejectsBad() {
        assertThatNullPointerException().isThrownBy(() -> new LimitOverride(null, "A", 1, "c", T1));
        assertThatNullPointerException()
                .isThrownBy(() -> new LimitOverride(ALICE, null, 1, "c", T1));
        assertThatNullPointerException()
                .isThrownBy(() -> new LimitOverride(ALICE, "A", 1, null, T1));
        assertThatNullPointerException()
                .isThrownBy(() -> new LimitOverride(ALICE, "A", 1, "c", null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LimitOverride(ALICE, " ", 1, "c", T1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LimitOverride(ALICE, "A", 1, " ", T1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LimitOverride(ALICE, "A", -1, "c", T1));
    }
}
