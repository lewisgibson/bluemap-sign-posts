package com.lewisgibson.bluemapsignposts.core.access;

import static com.lewisgibson.bluemapsignposts.Fixtures.ALICE;
import static com.lewisgibson.bluemapsignposts.Fixtures.override;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LimitResolver")
class LimitResolverTest {

    private static final String PREFIX = "bluemapsignposts.limit.";

    @Test
    @DisplayName("an override beats everything, even an unlimited node")
    void overrideWins() {
        EffectiveLimit limit =
                LimitResolver.resolve(
                        Optional.of(override(ALICE, 3)), true, true, true, OptionalInt.of(99), 10);
        assertThat(limit).isEqualTo(new EffectiveLimit(3, LimitSource.OVERRIDE));
    }

    @Test
    @DisplayName("the unlimited node beats operator status and tiers")
    void unlimitedNode() {
        assertThat(
                        LimitResolver.resolve(
                                Optional.empty(), true, false, false, OptionalInt.of(5), 10))
                .isEqualTo(new EffectiveLimit(0, LimitSource.UNLIMITED_PERMISSION));
    }

    @Test
    @DisplayName("an operator is unlimited only while config says so")
    void operator() {
        assertThat(
                        LimitResolver.resolve(
                                Optional.empty(), false, true, true, OptionalInt.of(5), 10))
                .isEqualTo(new EffectiveLimit(0, LimitSource.OPERATOR));
        assertThat(
                        LimitResolver.resolve(
                                Optional.empty(), false, true, false, OptionalInt.of(5), 10))
                .isEqualTo(new EffectiveLimit(5, LimitSource.TIER_PERMISSION));
        assertThat(
                        LimitResolver.resolve(
                                Optional.empty(), false, false, true, OptionalInt.empty(), 10))
                .isEqualTo(new EffectiveLimit(10, LimitSource.DEFAULT));
    }

    @Test
    @DisplayName("a tier beats the default, and the default is last")
    void tierThenDefault() {
        assertThat(
                        LimitResolver.resolve(
                                Optional.empty(), false, false, true, OptionalInt.of(25), 10))
                .isEqualTo(new EffectiveLimit(25, LimitSource.TIER_PERMISSION));
        assertThat(
                        LimitResolver.resolve(
                                Optional.empty(), false, false, true, OptionalInt.empty(), 0))
                .isEqualTo(new EffectiveLimit(0, LimitSource.DEFAULT));
    }

    @Test
    @DisplayName("rejects nulls and a negative default")
    void rejectsBad() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                LimitResolver.resolve(
                                        null, false, false, true, OptionalInt.empty(), 1));
        assertThatNullPointerException()
                .isThrownBy(
                        () -> LimitResolver.resolve(Optional.empty(), false, false, true, null, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                LimitResolver.resolve(
                                        Optional.empty(),
                                        false,
                                        false,
                                        true,
                                        OptionalInt.empty(),
                                        -1));
        assertThatNullPointerException().isThrownBy(() -> LimitResolver.highestTier(null, PREFIX));
        assertThatNullPointerException()
                .isThrownBy(() -> LimitResolver.highestTier(List.of(), null));
    }

    @Test
    @DisplayName("highestTier takes the largest numeric suffix and ignores everything else")
    void highestTier() {
        List<String> nodes =
                Arrays.asList(
                        "bluemapsignposts.create",
                        PREFIX + "5",
                        PREFIX + "25",
                        PREFIX + "unlimited",
                        PREFIX + "",
                        PREFIX + "1x",
                        PREFIX + "-3",
                        PREFIX + "99999999999999999999",
                        "other.limit.100",
                        null,
                        PREFIX + "7");
        assertThat(LimitResolver.highestTier(nodes, PREFIX)).hasValue(25);
        assertThat(LimitResolver.highestTier(List.of("a", "b"), PREFIX)).isEmpty();
        assertThat(LimitResolver.highestTier(List.of(PREFIX + "0"), PREFIX)).hasValue(0);
    }
}
