package com.lewisgibson.bluemapsignposts.core.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Messages")
class MessagesTest {

    @Test
    @DisplayName("exposes every template")
    void exposesTemplates() {
        Messages messages = new Messages("a", "b", "c", "d", "e", "f", "g");
        assertThat(messages.created()).isEqualTo("a");
        assertThat(messages.updated()).isEqualTo("b");
        assertThat(messages.removed()).isEqualTo("c");
        assertThat(messages.deniedPermission()).isEqualTo("d");
        assertThat(messages.deniedPlaytime()).isEqualTo("e");
        assertThat(messages.deniedLimit()).isEqualTo("f");
        assertThat(messages.emptyLabel()).isEqualTo("g");
    }

    @Test
    @DisplayName("rejects a null template in any position")
    void rejectsNulls() {
        String[] valid = {"a", "b", "c", "d", "e", "f", "g"};
        for (int position = 0; position < valid.length; position++) {
            String[] args = valid.clone();
            args[position] = null;
            assertThatNullPointerException()
                    .as("position %d", position)
                    .isThrownBy(
                            () ->
                                    new Messages(
                                            args[0], args[1], args[2], args[3], args[4], args[5],
                                            args[6]));
        }
    }
}
