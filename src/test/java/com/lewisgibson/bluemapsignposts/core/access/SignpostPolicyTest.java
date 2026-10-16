package com.lewisgibson.bluemapsignposts.core.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignpostPolicy")
class SignpostPolicyTest {

    private static final EffectiveLimit TWO = new EffectiveLimit(2, LimitSource.DEFAULT);
    private static final EffectiveLimit NONE = new EffectiveLimit(0, LimitSource.OPERATOR);

    private final SignpostPolicy policy = new SignpostPolicy(100L);

    @Test
    @DisplayName("exposes its setting and rejects a negative one")
    void settings() {
        assertThat(policy.requiredPlaytimeTicks()).isEqualTo(100L);
        assertThatIllegalArgumentException().isThrownBy(() -> new SignpostPolicy(-1L));
        assertThatNullPointerException()
                .isThrownBy(() -> policy.evaluate(true, false, 100L, 0, null));
    }

    @Test
    @DisplayName("bypass wins over everything")
    void bypass() {
        AccessResult result = policy.evaluate(false, true, 0L, 99, TWO);
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isEqualTo(DecisionReason.BYPASS);
    }

    @Test
    @DisplayName("no permission is refused before playtime is looked at")
    void noPermission() {
        assertThat(policy.evaluate(false, false, 1000L, 0, TWO).reason())
                .isEqualTo(DecisionReason.NO_PERMISSION);
    }

    @Test
    @DisplayName("below the playtime threshold is refused")
    void belowThreshold() {
        AccessResult result = policy.evaluate(true, false, 99L, 0, TWO);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo(DecisionReason.BELOW_THRESHOLD);
    }

    @Test
    @DisplayName("at the limit is refused, under it is allowed, unlimited always allows")
    void limit() {
        assertThat(policy.evaluate(true, false, 100L, 2, TWO).reason())
                .isEqualTo(DecisionReason.LIMIT_REACHED);
        assertThat(policy.evaluate(true, false, 100L, 1, TWO).reason())
                .isEqualTo(DecisionReason.ALLOWED);
        assertThat(policy.evaluate(true, false, 100L, 1_000_000, NONE).reason())
                .isEqualTo(DecisionReason.ALLOWED);
    }

    @Test
    @DisplayName("a zero threshold means no playtime gate")
    void zeroThreshold() {
        SignpostPolicy open = new SignpostPolicy(0L);
        assertThat(open.evaluate(true, false, 0L, 0, TWO).allowed()).isTrue();
        assertThat(DecisionReason.valueOf("ALLOWED")).isEqualTo(DecisionReason.ALLOWED);
    }
}
