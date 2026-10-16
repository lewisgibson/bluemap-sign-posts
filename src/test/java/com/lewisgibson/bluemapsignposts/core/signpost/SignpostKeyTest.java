package com.lewisgibson.bluemapsignposts.core.signpost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignpostKey")
class SignpostKeyTest {

    @Test
    @DisplayName("derives chunk coordinates and the marker id")
    void derivesChunkAndMarkerId() {
        SignpostKey key = new SignpostKey("world", -1, 64, 17);
        assertThat(key.chunkX()).isEqualTo(-1);
        assertThat(key.chunkZ()).isEqualTo(1);
        assertThat(key.markerId()).isEqualTo("-1,64,17");
    }

    @Test
    @DisplayName("packs chunk coordinates without collisions across the sign bit")
    void packsChunkKey() {
        assertThat(SignpostKey.chunkKey(1, -1)).isNotEqualTo(SignpostKey.chunkKey(-1, 1));
        assertThat(SignpostKey.chunkKey(0, -1)).isNotEqualTo(SignpostKey.chunkKey(-1, -1));
        assertThat(SignpostKey.chunkKey(3, 7)).isEqualTo((3L << 32) | 7L);
    }

    @Test
    @DisplayName("rejects a null or blank world")
    void rejectsBadWorld() {
        assertThatNullPointerException().isThrownBy(() -> new SignpostKey(null, 0, 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new SignpostKey(" ", 0, 0, 0));
    }
}
