package com.lewisgibson.bluemapsignposts.core.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EffectiveLimit")
class EffectiveLimitTest {

    @Test
    @DisplayName("a numeric limit allows up to but not past itself")
    void numeric() {
        EffectiveLimit limit = new EffectiveLimit(3, LimitSource.TIER_PERMISSION);
        assertThat(limit.isUnlimited()).isFalse();
        assertThat(limit.allows(2)).isTrue();
        assertThat(limit.allows(3)).isFalse();
        assertThat(limit.describe()).isEqualTo("3");
        assertThat(limit.source()).isEqualTo(LimitSource.TIER_PERMISSION);
    }

    @Test
    @DisplayName("zero is unlimited")
    void unlimited() {
        EffectiveLimit limit = new EffectiveLimit(0, LimitSource.OVERRIDE);
        assertThat(limit.isUnlimited()).isTrue();
        assertThat(limit.allows(Integer.MAX_VALUE)).isTrue();
        assertThat(limit.describe()).isEqualTo("unlimited");
    }

    @Test
    @DisplayName("rejects a negative limit and a null source")
    void rejectsBad() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EffectiveLimit(-1, LimitSource.DEFAULT));
        assertThatNullPointerException().isThrownBy(() -> new EffectiveLimit(1, null));
        assertThat(LimitSource.valueOf("DEFAULT")).isEqualTo(LimitSource.DEFAULT);
    }
}
