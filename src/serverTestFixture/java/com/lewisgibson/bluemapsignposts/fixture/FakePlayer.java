package com.lewisgibson.bluemapsignposts.fixture;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

/**
 * A {@link Player} that exists only as far as the plugin under test looks: an id, a name, a
 * playtime statistic, a set of permission nodes, operator status and somewhere to receive chat. Any
 * other call throws, naming the method, so a new dependency on the player surfaces as a test
 * failure with a clear message rather than a silent default.
 *
 * <p>Flags, comma-separated: {@code noperm} (no create node), {@code bypass}, {@code op}, {@code
 * unlimited}, {@code tier:<n>}; {@code -} for none.
 */
final class FakePlayer implements InvocationHandler {

    /** The offline-mode id Paper would give a player called TestPlayer. */
    static final String NAME = "TestPlayer";

    static final UUID ID =
            UUID.nameUUIDFromBytes(("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8));

    private final long playtimeTicks;
    private final Set<String> granted = new HashSet<>();
    private final boolean operator;
    private final List<String> messages = new ArrayList<>();

    FakePlayer(long playtimeTicks, String flags) {
        this.playtimeTicks = playtimeTicks;
        boolean op = false;
        granted.add("bluemapsignposts.create");
        for (String flag : flags.split(",")) {
            switch (flag) {
                case "noperm" -> granted.remove("bluemapsignposts.create");
                case "bypass" -> granted.add("bluemapsignposts.bypass");
                case "unlimited" -> granted.add("bluemapsignposts.limit.unlimited");
                case "op" -> op = true;
                default -> {
                    if (flag.startsWith("tier:")) {
                        granted.add("bluemapsignposts.limit." + flag.substring(5));
                    }
                }
            }
        }
        this.operator = op;
    }

    Player proxy() {
        return (Player)
                Proxy.newProxyInstance(
                        Player.class.getClassLoader(), new Class<?>[] {Player.class}, this);
    }

    /** Every chat line the plugin sent, as plain text, joined by " / ". */
    String messages() {
        return messages.isEmpty() ? "-" : String.join(" / ", messages);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "getUniqueId":
                return ID;
            case "getName":
                return NAME;
            case "getStatistic":
                if (args.length == 1 && args[0] == Statistic.PLAY_ONE_MINUTE) {
                    return (int) playtimeTicks;
                }
                break;
            case "hasPermission":
                if (args.length == 1 && args[0] instanceof String node) {
                    return granted.contains(node);
                }
                break;
            case "isOp":
                return operator;
            case "getEffectivePermissions":
                {
                    Set<PermissionAttachmentInfo> infos = new HashSet<>();
                    for (String node : granted) {
                        infos.add(new PermissionAttachmentInfo((Player) proxy, node, null, true));
                    }
                    return infos;
                }
            case "sendMessage":
                if (args.length == 1 && args[0] instanceof Component component) {
                    messages.add(PlainTextComponentSerializer.plainText().serialize(component));
                    return null;
                }
                break;
            case "hashCode":
                return ID.hashCode();
            case "equals":
                return proxy == args[0];
            case "toString":
                return "FakePlayer[" + NAME + "]";
            default:
                break;
        }
        throw new UnsupportedOperationException(
                "FakePlayer does not implement "
                        + method
                        + " - the plugin under test now needs it");
    }
}
