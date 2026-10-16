package com.lewisgibson.bluemapsignposts.core.signpost;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StorageException")
class StorageExceptionTest {

    @Test
    @DisplayName("carries its message and cause")
    void carriesMessageAndCause() {
        RuntimeException cause = new RuntimeException("disk");
        StorageException ex = new StorageException("could not open", cause);
        assertThat(ex).hasMessage("could not open").hasCause(cause);
    }
}
