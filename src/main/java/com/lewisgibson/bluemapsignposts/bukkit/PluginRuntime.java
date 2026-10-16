package com.lewisgibson.bluemapsignposts.bukkit;

import com.lewisgibson.bluemapsignposts.core.access.SignpostPolicy;
import com.lewisgibson.bluemapsignposts.core.settings.BlueMapSignPostsSettings;
import com.lewisgibson.bluemapsignposts.core.sign.SignpostParser;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostService;

/**
 * The live runtime state, swapped atomically on reload so every event sees one consistent settings,
 * policy, parser and service quartet.
 *
 * @param settings the resolved settings
 * @param policy the create policy derived from {@code settings}
 * @param parser the sign parser derived from {@code settings}
 * @param service the service over the open store, the registry loaded from it and the map
 */
public record PluginRuntime(
        BlueMapSignPostsSettings settings,
        SignpostPolicy policy,
        SignpostParser parser,
        SignpostService service) {

    static PluginRuntime from(BlueMapSignPostsSettings settings, SignpostService service) {
        return new PluginRuntime(
                settings,
                new SignpostPolicy(settings.requiredPlaytimeTicks()),
                new SignpostParser(settings.trigger()),
                service);
    }
}
