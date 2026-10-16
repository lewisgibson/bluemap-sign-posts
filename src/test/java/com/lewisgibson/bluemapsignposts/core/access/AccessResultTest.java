package com.lewisgibson.bluemapsignposts.core.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AccessResult")
class AccessResultTest {

    @Test
    @DisplayName("allowed carries its reason")
    void allowedCarriesReason() {
        AccessResult result = AccessResult.allowed(DecisionReason.BYPASS);
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isEqualTo(DecisionReason.BYPASS);
    }

    @Test
    @DisplayName("denied carries its reason")
    void deniedCarriesReason() {
        AccessResult result = AccessResult.denied(DecisionReason.LIMIT_REACHED);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo(DecisionReason.LIMIT_REACHED);
    }

    @Test
    @DisplayName("rejects a null reason")
    void rejectsNullReason() {
        assertThatNullPointerException().isThrownBy(() -> new AccessResult(true, null));
    }
}
