package com.lewisgibson.bluemapsignposts.core.signpost;

import static com.lewisgibson.bluemapsignposts.Fixtures.ALICE;
import static com.lewisgibson.bluemapsignposts.Fixtures.BOB;
import static com.lewisgibson.bluemapsignposts.Fixtures.override;
import static com.lewisgibson.bluemapsignposts.Fixtures.signpost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignpostService")
class SignpostServiceTest {

    private final SignpostRegistry registry = new SignpostRegistry();
    private final FakeSignpostStore store = new FakeSignpostStore();
    private final FakeMarkerSink sink = new FakeMarkerSink();
    private final List<Runnable> queued = new ArrayList<>();
    private final SignpostService service = new SignpostService(registry, store, queued::add, sink);

    private void runWrites() {
        for (Runnable write : queued) {
            write.run();
        }
        queued.clear();
    }

    @Test
    @DisplayName("place updates the registry and the map at once, and the store on the writer")
    void place() {
        Signpost farm = signpost("world", 1, 64, 1, "Farm", ALICE);
        assertThat(service.place(farm)).isEmpty();
        assertThat(registry.get(farm.key())).contains(farm);
        assertThat(sink.shown).containsKey(farm.key());
        assertThat(store.signposts).isEmpty();
        runWrites();
        assertThat(store.signposts).containsKey(farm.key());

        Signpost renamed = signpost("world", 1, 64, 1, "Iron farm", ALICE);
        assertThat(service.place(renamed)).contains(farm);
        assertThat(sink.shown.get(farm.key()).label()).isEqualTo("Iron farm");
    }

    @Test
    @DisplayName("remove takes it off the map and out of the store, and is a no-op when absent")
    void remove() {
        Signpost farm = signpost("world", 1, 64, 1, "Farm", ALICE);
        service.place(farm);
        runWrites();
        assertThat(service.remove(farm.key())).contains(farm);
        assertThat(sink.shown).isEmpty();
        runWrites();
        assertThat(store.signposts).isEmpty();
        assertThat(store.writes).containsExactly("put 1,64,1", "remove 1,64,1");

        assertThat(service.remove(farm.key())).isEmpty();
        assertThat(queued).isEmpty();
    }

    @Test
    @DisplayName("limit overrides are set and cleared through the registry and the store")
    void limitOverrides() {
        assertThat(service.setLimitOverride(override(ALICE, 3))).isEmpty();
        assertThat(service.setLimitOverride(override(ALICE, 5))).contains(override(ALICE, 3));
        runWrites();
        assertThat(store.overrides.get(ALICE)).isEqualTo(override(ALICE, 5));
        assertThat(service.clearLimitOverride(ALICE)).contains(override(ALICE, 5));
        assertThat(service.clearLimitOverride(ALICE)).isEmpty();
        runWrites();
        assertThat(store.overrides).isEmpty();
        assertThat(store.writes)
                .containsExactly("put-limit Alice", "put-limit Alice", "remove-limit");
    }

    @Test
    @DisplayName("publishAll shows every signpost again and says how many")
    void publishAll() {
        service.place(signpost("world", 1, 64, 1, "A", ALICE));
        service.place(signpost("world", 2, 64, 2, "B", BOB));
        sink.shown.clear();
        assertThat(service.publishAll()).isEqualTo(2);
        assertThat(sink.shown).hasSize(2);
        assertThat(service.registry()).isSameAs(registry);
    }

    @Test
    @DisplayName("retractAll empties the map and leaves the registry and the store alone")
    void retractAll() {
        service.place(signpost("world", 1, 64, 1, "A", ALICE));
        service.place(signpost("world", 2, 64, 2, "B", BOB));
        runWrites();
        service.retractAll();
        assertThat(sink.shown).isEmpty();
        assertThat(registry.size()).isEqualTo(2);
        assertThat(store.signposts).hasSize(2);
        assertThat(queued).isEmpty();
    }

    @Test
    @DisplayName("publishWorld shows one world's signposts and no other's")
    void publishWorld() {
        service.place(signpost("world", 1, 64, 1, "A", ALICE));
        service.place(signpost("extra", 2, 64, 2, "B", BOB));
        service.retractAll();
        assertThat(service.publishWorld("extra")).isEqualTo(1);
        assertThat(sink.shown).hasSize(1);
        assertThat(sink.shown.values()).extracting(Signpost::label).containsExactly("B");
        assertThat(service.publishWorld("nowhere")).isZero();
    }

    @Test
    @DisplayName("rejects nulls")
    void rejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SignpostService(null, store, queued::add, sink));
        assertThatNullPointerException()
                .isThrownBy(() -> new SignpostService(registry, null, queued::add, sink));
        assertThatNullPointerException()
                .isThrownBy(() -> new SignpostService(registry, store, null, sink));
        assertThatNullPointerException()
                .isThrownBy(() -> new SignpostService(registry, store, queued::add, null));
    }
}
