package com.lewisgibson.bluemapsignposts.bukkit.message;

import com.lewisgibson.bluemapsignposts.core.message.Placeholders;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** Renders MiniMessage templates (after placeholder substitution) and sends them to a recipient. */
public final class BukkitMessenger {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    /**
     * Renders {@code template} and sends it as a chat message. A blank template is skipped so an
     * intentionally empty message never sends a stray line.
     *
     * @param audience the recipient (a Bukkit {@code Player} is an {@link Audience})
     * @param template the MiniMessage template, possibly containing {@code %name%} placeholders
     * @param placeholders the placeholder values keyed by name; substituted as plain text, so a
     *     value such as a player-written sign label can never introduce MiniMessage markup
     */
    public void send(Audience audience, String template, Map<String, String> placeholders) {
        if (template.isBlank()) {
            return;
        }
        audience.sendMessage(render(template, placeholders));
    }

    private Component render(String template, Map<String, String> placeholders) {
        Map<String, String> inert = new HashMap<>();
        placeholders.forEach((name, value) -> inert.put(name, miniMessage.escapeTags(value)));
        return miniMessage.deserialize(Placeholders.apply(template, inert));
    }
}
