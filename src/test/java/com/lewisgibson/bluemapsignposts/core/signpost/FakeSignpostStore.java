package com.lewisgibson.bluemapsignposts.core.signpost;

import com.lewisgibson.bluemapsignposts.core.access.LimitOverride;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** An in-memory store that records every write, for the service tests. */
final class FakeSignpostStore implements SignpostStore {

    final Map<SignpostKey, Signpost> signposts = new LinkedHashMap<>();
    final Map<UUID, LimitOverride> overrides = new LinkedHashMap<>();
    final List<String> writes = new ArrayList<>();

    @Override
    public void open() {
        writes.add("open");
    }

    @Override
    public String description() {
        return "fake";
    }

    @Override
    public List<Signpost> loadSignposts() {
        return new ArrayList<>(signposts.values());
    }

    @Override
    public Map<UUID, LimitOverride> loadLimitOverrides() {
        return new LinkedHashMap<>(overrides);
    }

    @Override
    public void putSignpost(Signpost signpost) {
        signposts.put(signpost.key(), signpost);
        writes.add("put " + signpost.key().markerId());
    }

    @Override
    public void removeSignpost(SignpostKey key) {
        signposts.remove(key);
        writes.add("remove " + key.markerId());
    }

    @Override
    public void putLimitOverride(LimitOverride override) {
        overrides.put(override.player(), override);
        writes.add("put-limit " + override.playerName());
    }

    @Override
    public void removeLimitOverride(UUID player) {
        overrides.remove(player);
        writes.add("remove-limit");
    }

    @Override
    public void close() {
        writes.add("close");
    }
}
