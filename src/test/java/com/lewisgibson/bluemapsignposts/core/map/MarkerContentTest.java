package com.lewisgibson.bluemapsignposts.core.map;

import static com.lewisgibson.bluemapsignposts.Fixtures.ALICE;
import static com.lewisgibson.bluemapsignposts.Fixtures.signpost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MarkerContent")
class MarkerContentTest {

    @Test
    @DisplayName("renders every placeholder, escaping what players wrote")
    void rendersPlaceholders() {
        Signpost signpost = signpost("world", 1, 64, -3, "<b>Farm</b> & \"shop\" 'x'", ALICE);
        String html = MarkerContent.render("%label%|%owner%|%world%|%x%|%y%|%z%", signpost);
        assertThat(html)
                .isEqualTo(
                        "&lt;b&gt;Farm&lt;/b&gt; &amp; &quot;shop&quot; &#39;x&#39;|Alice|world|1|64|-3");
    }

    @Test
    @DisplayName("escape leaves ordinary text alone")
    void escapePlain() {
        assertThat(MarkerContent.escape("Iron farm 2")).isEqualTo("Iron farm 2");
    }

    @Test
    @DisplayName("rejects nulls")
    void rejectsNulls() {
        Signpost signpost = signpost("world", 0, 0, 0, "Farm", ALICE);
        assertThatNullPointerException().isThrownBy(() -> MarkerContent.render(null, signpost));
        assertThatNullPointerException().isThrownBy(() -> MarkerContent.render("%label%", null));
        assertThatNullPointerException().isThrownBy(() -> MarkerContent.escape(null));
    }
}
