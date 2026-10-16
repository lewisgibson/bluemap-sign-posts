package com.lewisgibson.bluemapsignposts.core.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.lewisgibson.bluemapsignposts.Fixtures;
import com.lewisgibson.bluemapsignposts.core.message.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("BlueMapSignPostsSettings")
class BlueMapSignPostsSettingsTest {

    private static final StorageSettings STORAGE = StorageSettings.sqliteDefault();

    private static BlueMapSignPostsSettings build(
            String trigger,
            long playtime,
            int limit,
            String create,
            String bypass,
            String setLabel,
            double distance,
            String detail,
            StorageSettings storage,
            Messages messages) {
        return new BlueMapSignPostsSettings(
                trigger, true, playtime, limit, true, create, bypass, setLabel, true, false,
                distance, detail, storage, messages);
    }

    @Test
    @DisplayName("exposes its fields when valid")
    void buildsValid() {
        BlueMapSignPostsSettings settings = Fixtures.settings();
        assertThat(settings.trigger()).isEqualTo("[map]");
        assertThat(settings.bannersEnabled()).isTrue();
        assertThat(settings.requiredPlaytimeTicks()).isZero();
        assertThat(settings.defaultLimit()).isEqualTo(10);
        assertThat(settings.operatorsUnlimited()).isTrue();
        assertThat(settings.createPermission()).isEqualTo("bluemapsignposts.create");
        assertThat(settings.bypassPermission()).isEqualTo("bluemapsignposts.bypass");
        assertThat(settings.markerSetLabel()).isEqualTo("Signposts");
        assertThat(settings.markerSetToggleable()).isTrue();
        assertThat(settings.markerSetDefaultHidden()).isFalse();
        assertThat(settings.markerMaxDistance()).isEqualTo(100000.0d);
        assertThat(settings.markerDetail()).isEqualTo("%label%");
        assertThat(settings.storage()).isEqualTo(STORAGE);
        assertThat(settings.messages()).isEqualTo(Fixtures.MESSAGES);
        assertThat(BlueMapSignPostsSettings.LIMIT_TIER_PREFIX).isEqualTo("bluemapsignposts.limit.");
        assertThat(BlueMapSignPostsSettings.UNLIMITED_PERMISSION)
                .isEqualTo("bluemapsignposts.limit.unlimited");
    }

    @Test
    @DisplayName("rejects nulls")
    void rejectsNulls() {
        Messages m = Fixtures.MESSAGES;
        assertThatNullPointerException()
                .isThrownBy(() -> build(null, 0, 0, "c", "b", "L", 1, "d", STORAGE, m));
        assertThatNullPointerException()
                .isThrownBy(() -> build("[map]", 0, 0, null, "b", "L", 1, "d", STORAGE, m));
        assertThatNullPointerException()
                .isThrownBy(() -> build("[map]", 0, 0, "c", null, "L", 1, "d", STORAGE, m));
        assertThatNullPointerException()
                .isThrownBy(() -> build("[map]", 0, 0, "c", "b", null, 1, "d", STORAGE, m));
        assertThatNullPointerException()
                .isThrownBy(() -> build("[map]", 0, 0, "c", "b", "L", 1, null, STORAGE, m));
        assertThatNullPointerException()
                .isThrownBy(() -> build("[map]", 0, 0, "c", "b", "L", 1, "d", null, m));
        assertThatNullPointerException()
                .isThrownBy(() -> build("[map]", 0, 0, "c", "b", "L", 1, "d", STORAGE, null));
    }

    @Test
    @DisplayName("rejects blank strings and negative numbers")
    void rejectsBlanksAndNegatives() {
        Messages m = Fixtures.MESSAGES;
        assertThatIllegalArgumentException()
                .isThrownBy(() -> build(" ", 0, 0, "c", "b", "L", 1, "d", STORAGE, m));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> build("[map]", -1, 0, "c", "b", "L", 1, "d", STORAGE, m));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> build("[map]", 0, -1, "c", "b", "L", 1, "d", STORAGE, m));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> build("[map]", 0, 0, " ", "b", "L", 1, "d", STORAGE, m));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> build("[map]", 0, 0, "c", " ", "L", 1, "d", STORAGE, m));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> build("[map]", 0, 0, "c", "b", " ", 1, "d", STORAGE, m));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("rejects a blank popup, which BlueMap would fill with the raw label as HTML")
    void rejectsBlankDetail(String detail) {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                build(
                                        "[map]",
                                        0,
                                        0,
                                        "c",
                                        "b",
                                        "L",
                                        1,
                                        detail,
                                        STORAGE,
                                        Fixtures.MESSAGES))
                .withMessageContaining("markerDetail");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0d, -5.0d, Double.NaN})
    @DisplayName("rejects a marker distance that is not positive")
    void rejectsBadDistance(double distance) {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                build(
                                        "[map]",
                                        0,
                                        0,
                                        "c",
                                        "b",
                                        "L",
                                        distance,
                                        "d",
                                        STORAGE,
                                        Fixtures.MESSAGES));
    }
}
