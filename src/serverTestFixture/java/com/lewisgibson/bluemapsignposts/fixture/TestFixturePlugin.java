package com.lewisgibson.bluemapsignposts.fixture;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A second plugin, loaded only in the server tests, that lets the harness fire real sign, banner
 * and break events at a block for a fake player whose playtime and permissions are whatever the
 * command says, and read BlueMap's own marker set back. The plugin under test sees ordinary events
 * from an ordinary-looking player and the RCON reply reports what the player was told and what the
 * map shows.
 *
 * <ul>
 *   <li>{@code /bmsptest sign <x> <y> <z> <playtimeTicks> <flags> <line1>|<line2>|<line3>|<line4>}
 *   <li>{@code /bmsptest banner <x> <y> <z> <playtimeTicks> <flags> <name...>}
 *   <li>{@code /bmsptest break <x> <y> <z>}
 *   <li>{@code /bmsptest explode <x> <y> <z>} - a real block-breaking explosion at the block
 *   <li>{@code /bmsptest marker <x> <y> <z>} - the marker's label in BlueMap's set, or {@code -}
 * </ul>
 *
 * Nothing here touches the plugin under test: it is observed purely through the server's own event
 * pipeline and BlueMap's public API.
 */
public final class TestFixturePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getCommand("bmsptest").setExecutor(this::run);
        // Test-only visibility: the plugin under test re-checks its signposts on chunk load, so
        // the harness log says when the spawn chunk came and went between RCON commands.
        getServer()
                .getPluginManager()
                .registerEvents(
                        new org.bukkit.event.Listener() {
                            @org.bukkit.event.EventHandler
                            public void onLoad(org.bukkit.event.world.ChunkLoadEvent event) {
                                if (event.getChunk().getX() == 0 && event.getChunk().getZ() == 0) {
                                    getLogger().info("chunk 0,0 loaded");
                                }
                            }

                            @org.bukkit.event.EventHandler
                            public void onUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
                                if (event.getChunk().getX() == 0 && event.getChunk().getZ() == 0) {
                                    getLogger().info("chunk 0,0 unloaded");
                                }
                            }
                        },
                        this);
    }

    private boolean run(CommandSender sender, Command command, String label, String[] arguments) {
        String[] args = arguments;
        World world = getServer().getWorlds().get(0);
        // An optional first argument picks the world: @nether, @end, or @<world name>.
        if (args.length > 0 && args[0].startsWith("@")) {
            world = world(args[0].substring(1));
            args = List.of(args).subList(1, args.length).toArray(String[]::new);
            if (world == null) {
                sender.sendMessage("no such world " + arguments[0]);
                return true;
            }
        }
        if (args.length == 2 && args[0].equals("worldcreate")) {
            World created = new WorldCreator(args[1]).type(WorldType.FLAT).createWorld();
            sender.sendMessage("created=" + (created != null));
            return true;
        }
        if (args.length == 2 && args[0].equals("worldunload")) {
            sender.sendMessage("unloaded=" + getServer().unloadWorld(args[1], true));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("usage: /bmsptest [@world] <sign|banner|break|marker> <x> <y> <z>");
            return true;
        }
        Block block =
                world.getBlockAt(
                        Integer.parseInt(args[1]),
                        Integer.parseInt(args[2]),
                        Integer.parseInt(args[3]));
        switch (args[0]) {
            case "sign" -> sign(sender, block, args);
            case "banner" -> banner(sender, block, args);
            case "break" -> breakBlock(sender, block);
            case "explode" -> explode(sender, block);
            case "marker" -> sender.sendMessage(markerLabel(world, block));
            case "read" -> sender.sendMessage(read(block));
            default -> sender.sendMessage("unknown action " + args[0]);
        }
        return true;
    }

    private void sign(CommandSender sender, Block block, String[] args) {
        FakePlayer fake = new FakePlayer(Long.parseLong(args[4]), args[5]);
        Player player = fake.proxy();
        // A standing sign needs a block under it, or the flat world's physics knocks it off on
        // the next tick and the plugin (correctly) takes the marker down with it.
        block.getRelative(0, -1, 0).setType(Material.STONE);
        block.setType(Material.OAK_SIGN);
        String[] text = String.join(" ", List.of(args).subList(6, args.length)).split("\\|", -1);
        List<Component> lines = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            lines.add(Component.text(index < text.length ? text[index] : ""));
        }
        getServer()
                .getPluginManager()
                .callEvent(new SignChangeEvent(block, player, lines, Side.FRONT));
        // What the server does after the event: the text lands in the block state. Without this
        // the plugin's chunk-load check would find a blank sign and (rightly) drop the marker the
        // next time the chunk loads - which it does between RCON commands with nobody online.
        Sign sign = (Sign) block.getState();
        for (int index = 0; index < 4; index++) {
            sign.getSide(Side.FRONT).line(index, lines.get(index));
        }
        sign.update(true, false);
        reply(sender, fake, block);
    }

    private void banner(CommandSender sender, Block block, String[] args) {
        FakePlayer fake = new FakePlayer(Long.parseLong(args[4]), args[5]);
        Player player = fake.proxy();
        block.getRelative(0, -1, 0).setType(Material.STONE);
        block.setType(Material.WHITE_BANNER);
        ItemStack item = new ItemStack(Material.WHITE_BANNER);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text(String.join(" ", List.of(args).subList(6, args.length))));
        item.setItemMeta(meta);
        Block against = block.getRelative(0, -1, 0);
        getServer()
                .getPluginManager()
                .callEvent(
                        new BlockPlaceEvent(
                                block,
                                block.getState(),
                                against,
                                item,
                                player,
                                true,
                                EquipmentSlot.HAND));
        // As above: a placed named banner carries the name in its block entity.
        Banner banner = (Banner) block.getState();
        banner.customName(meta.customName());
        banner.update(true, false);
        reply(sender, fake, block);
    }

    private void breakBlock(CommandSender sender, Block block) {
        FakePlayer fake = new FakePlayer(0L, "-");
        getServer().getPluginManager().callEvent(new BlockBreakEvent(block, fake.proxy()));
        block.setType(Material.AIR);
        reply(sender, fake, block);
    }

    private void explode(CommandSender sender, Block block) {
        boolean exploded =
                block.getWorld()
                        .createExplosion(block.getLocation().add(0.5, 0.5, 0.5), 3.0f, false, true);
        sender.sendMessage("exploded=" + exploded + " now=" + block.getType().name());
    }

    private World world(String name) {
        for (World candidate : getServer().getWorlds()) {
            boolean nether =
                    name.equals("nether") && candidate.getEnvironment() == World.Environment.NETHER;
            boolean end =
                    name.equals("end") && candidate.getEnvironment() == World.Environment.THE_END;
            if (nether || end || candidate.getName().equals(name)) {
                return candidate;
            }
        }
        return null;
    }

    private void reply(CommandSender sender, FakePlayer fake, Block block) {
        sender.sendMessage(
                "messages=" + fake.messages() + " marker=" + markerLabel(block.getWorld(), block));
    }

    /** What the block is and says, from its saved state, for the harness to compare with. */
    private static String read(Block block) {
        if (block.getState() instanceof Sign sign) {
            List<String> lines = new ArrayList<>();
            for (Component line : sign.getSide(Side.FRONT).lines()) {
                lines.add(PlainTextComponentSerializer.plainText().serialize(line));
            }
            return "sign "
                    + String.join("|", lines)
                    + " chunkLoaded="
                    + block.getChunk().isLoaded();
        }
        if (block.getState() instanceof Banner banner) {
            Component name = banner.customName();
            return "banner "
                    + (name == null
                            ? "-"
                            : PlainTextComponentSerializer.plainText().serialize(name));
        }
        return block.getType().name();
    }

    private static String markerLabel(World world, Block block) {
        Optional<BlueMapAPI> api = BlueMapAPI.getInstance();
        if (api.isEmpty()) {
            return "(bluemap not enabled)";
        }
        Optional<BlueMapWorld> mapWorld = api.get().getWorld(world);
        if (mapWorld.isEmpty()) {
            return "(world not mapped)";
        }
        for (BlueMapMap map : mapWorld.get().getMaps()) {
            MarkerSet set = map.getMarkerSets().get("signposts");
            if (set == null) {
                continue;
            }
            Marker marker = set.get(block.getX() + "," + block.getY() + "," + block.getZ());
            if (marker != null) {
                return marker.getLabel();
            }
        }
        return "-";
    }
}
