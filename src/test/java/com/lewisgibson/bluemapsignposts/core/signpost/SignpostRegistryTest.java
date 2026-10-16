package com.lewisgibson.bluemapsignposts.core.signpost;

import static com.lewisgibson.bluemapsignposts.Fixtures.ALICE;
import static com.lewisgibson.bluemapsignposts.Fixtures.BOB;
import static com.lewisgibson.bluemapsignposts.Fixtures.override;
import static com.lewisgibson.bluemapsignposts.Fixtures.signpost;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignpostRegistry")
class SignpostRegistryTest {

    private final SignpostRegistry registry = new SignpostRegistry();

    @Test
    @DisplayName("starts empty")
    void startsEmpty() {
        assertThat(registry.size()).isZero();
        assertThat(registry.all()).isEmpty();
        assertThat(registry.isEmptyIn("world")).isTrue();
        assertThat(registry.get(new SignpostKey("world", 0, 0, 0))).isEmpty();
        assertThat(registry.remove(new SignpostKey("world", 0, 0, 0))).isEmpty();
        assertThat(registry.inChunk("world", 0, 0)).isEmpty();
    }

    @Test
    @DisplayName("put stores, indexes and reports the previous occupant")
    void putStoresAndReplaces() {
        Signpost first = signpost("world", 5, 64, 5, "First", ALICE);
        Signpost second = signpost("world", 5, 64, 5, "Second", BOB);

        assertThat(registry.put(first)).isEmpty();
        assertThat(registry.put(second)).contains(first);
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.get(first.key())).contains(second);
        assertThat(registry.inChunk("world", 0, 0)).containsExactly(second);
        assertThat(registry.isEmptyIn("world")).isFalse();
    }

    @Test
    @DisplayName("remove returns the signpost and drops empty chunk and world indexes")
    void removeCleansIndexes() {
        Signpost a = signpost("world", 1, 64, 1, "A", ALICE);
        Signpost b = signpost("world", 2, 64, 2, "B", ALICE);
        Signpost c = signpost("world", 40, 64, 40, "C", ALICE);
        registry.put(a);
        registry.put(b);
        registry.put(c);

        assertThat(registry.remove(a.key())).contains(a);
        assertThat(registry.inChunk("world", 0, 0)).containsExactly(b);
        assertThat(registry.remove(b.key())).contains(b);
        assertThat(registry.inChunk("world", 0, 0)).isEmpty();
        assertThat(registry.isEmptyIn("world")).isFalse();
        assertThat(registry.remove(c.key())).contains(c);
        assertThat(registry.isEmptyIn("world")).isTrue();
        assertThat(registry.remove(c.key())).isEmpty();
    }

    @Test
    @DisplayName("counts and lists per owner")
    void perOwner() {
        registry.put(signpost("world", 1, 64, 1, "A", ALICE));
        registry.put(signpost("world", 2, 64, 2, "B", ALICE));
        registry.put(signpost("world_nether", 3, 64, 3, "C", BOB));

        assertThat(registry.countOwnedBy(ALICE)).isEqualTo(2);
        assertThat(registry.countOwnedBy(BOB)).isEqualTo(1);
        assertThat(registry.ownedBy(BOB)).extracting(Signpost::label).containsExactly("C");
        assertThat(registry.ownedBy(ALICE)).hasSize(2);
    }

    @Test
    @DisplayName("inChunk distinguishes a missing world from a missing chunk")
    void inChunkLookups() {
        registry.put(signpost("world", 1, 64, 1, "A", ALICE));
        assertThat(registry.inChunk("world_nether", 0, 0)).isEmpty();
        assertThat(registry.inChunk("world", 1, 0)).isEmpty();
        assertThat(registry.inChunk("world", 0, 0)).hasSize(1);
    }

    @Test
    @DisplayName("replaceAll swaps the whole contents, later duplicates winning")
    void replaceAll() {
        registry.put(signpost("world", 1, 64, 1, "Old", ALICE));
        registry.putLimitOverride(override(ALICE, 3));
        registry.replaceAll(
                List.of(
                        signpost("world", 9, 64, 9, "New", BOB),
                        signpost("world", 9, 64, 9, "Newer", BOB)),
                List.of(override(BOB, 7)));
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.get(new SignpostKey("world", 9, 64, 9)).map(Signpost::label))
                .contains("Newer");
        assertThat(registry.get(new SignpostKey("world", 1, 64, 1))).isEmpty();
        assertThat(registry.limitOverride(ALICE)).isEmpty();
        assertThat(registry.limitOverride(BOB)).contains(override(BOB, 7));
    }

    @Test
    @DisplayName("lists per world")
    void perWorld() {
        registry.put(signpost("world", 1, 64, 1, "A", ALICE));
        registry.put(signpost("world_nether", 3, 64, 3, "C", BOB));
        assertThat(registry.inWorld("world")).extracting(Signpost::label).containsExactly("A");
        assertThat(registry.inWorld("world_the_end")).isEmpty();
    }

    @Test
    @DisplayName("keeps limit overrides: put reports the previous, remove reports the removed")
    void limitOverrides() {
        assertThat(registry.limitOverride(ALICE)).isEmpty();
        assertThat(registry.putLimitOverride(override(ALICE, 3))).isEmpty();
        assertThat(registry.putLimitOverride(override(ALICE, 5))).contains(override(ALICE, 3));
        assertThat(registry.limitOverride(ALICE)).contains(override(ALICE, 5));
        assertThat(registry.limitOverrides()).containsExactly(override(ALICE, 5));
        assertThat(registry.removeLimitOverride(ALICE)).contains(override(ALICE, 5));
        assertThat(registry.removeLimitOverride(ALICE)).isEmpty();
        assertThat(registry.limitOverrides()).isEmpty();
    }

    @Test
    @DisplayName("loads itself from a store")
    void loadsFromStore() {
        FakeSignpostStore store = new FakeSignpostStore();
        Signpost farm = signpost("world", 1, 64, 1, "A", ALICE);
        store.signposts.put(farm.key(), farm);
        store.overrides.put(BOB, override(BOB, 2));
        SignpostRegistry loaded = SignpostRegistry.load(store);
        assertThat(loaded.size()).isEqualTo(1);
        assertThat(loaded.limitOverride(BOB)).contains(override(BOB, 2));
    }
}
