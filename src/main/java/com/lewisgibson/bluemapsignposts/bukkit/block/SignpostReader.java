package com.lewisgibson.bluemapsignposts.bukkit.block;

import com.lewisgibson.bluemapsignposts.core.sign.ParseResult;
import com.lewisgibson.bluemapsignposts.core.sign.SignpostParser;
import com.lewisgibson.bluemapsignposts.core.signpost.Signpost;
import com.lewisgibson.bluemapsignposts.core.signpost.SignpostSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

/**
 * Reads what a block currently says, as the domain sees it: a sign's two sides flattened to plain
 * text and parsed, or a banner's custom name.
 */
public final class SignpostReader {

    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    private SignpostReader() {}

    /**
     * What a block says right now, from its saved state.
     *
     * @param block the block to read
     * @param parser the current sign parser
     * @return the reading, or empty if the block is neither a sign nor a named banner
     */
    public static Optional<Reading> read(Block block, SignpostParser parser) {
        BlockState state = block.getState();
        if (state instanceof Sign sign) {
            ParseResult result =
                    parser.parseSides(
                            lines(sign.getSide(Side.FRONT)), lines(sign.getSide(Side.BACK)));
            return Optional.of(new Reading(SignpostSource.SIGN, result));
        }
        if (state instanceof Banner banner) {
            return bannerReading(banner.customName());
        }
        return Optional.empty();
    }

    /**
     * What a sign will say once an in-flight edit lands: the edited side takes the new lines, the
     * other side keeps what is saved.
     *
     * @param sign the sign's current state
     * @param editedSide the side being written
     * @param editedLines the new lines for that side
     * @param parser the current sign parser
     * @return the parse of the sign as it will be
     */
    public static ParseResult readAfterEdit(
            Sign sign, Side editedSide, List<Component> editedLines, SignpostParser parser) {
        List<String> edited = plain(editedLines);
        Side otherSide = editedSide == Side.FRONT ? Side.BACK : Side.FRONT;
        List<String> other = lines(sign.getSide(otherSide));
        return editedSide == Side.FRONT
                ? parser.parseSides(edited, other)
                : parser.parseSides(other, edited);
    }

    /**
     * Reads a banner's custom name as a signpost label.
     *
     * @param customName the banner's custom name, or {@code null} when it has none
     * @return a banner reading, or empty if there is no name
     */
    public static Optional<Reading> bannerReading(Component customName) {
        if (customName == null) {
            return Optional.empty();
        }
        String label = Signpost.clip(PLAIN.serialize(customName).trim());
        if (label.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Reading(SignpostSource.BANNER, ParseResult.signpost(label)));
    }

    private static List<String> lines(SignSide side) {
        return plain(side.lines());
    }

    private static List<String> plain(List<Component> components) {
        List<String> lines = new ArrayList<>(components.size());
        for (Component component : components) {
            lines.add(PLAIN.serialize(component));
        }
        return lines;
    }

    /**
     * What a block says, and from which kind of block.
     *
     * @param source sign or banner
     * @param result the parse (always a signpost for a banner)
     */
    public record Reading(SignpostSource source, ParseResult result) {}
}
