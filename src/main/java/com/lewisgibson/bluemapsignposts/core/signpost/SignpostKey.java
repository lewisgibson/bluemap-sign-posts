package com.lewisgibson.bluemapsignposts.core.signpost;

import java.util.Objects;

/**
 * The block position that identifies a signpost: a world name and integer block coordinates. One
 * block holds at most one signpost, so this is also the marker's identity on the map.
 *
 * @param world the world's name
 * @param x the block X
 * @param y the block Y
 * @param z the block Z
 */
public record SignpostKey(String world, int x, int y, int z) {

    public SignpostKey {
        Objects.requireNonNull(world, "world");
        if (world.isBlank()) {
            throw new IllegalArgumentException("world must not be blank");
        }
    }

    /**
     * @return the chunk X this block sits in
     */
    public int chunkX() {
        return x >> 4;
    }

    /**
     * @return the chunk Z this block sits in
     */
    public int chunkZ() {
        return z >> 4;
    }

    /**
     * @return the id used for the marker inside the map's marker set, unique per world because one
     *     block holds one signpost: {@code "x,y,z"}
     */
    public String markerId() {
        return x + "," + y + "," + z;
    }

    /**
     * Packs chunk coordinates into one {@code long}, the form the registry indexes chunks by.
     *
     * @param chunkX the chunk X
     * @param chunkZ the chunk Z
     * @return the packed key
     */
    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }
}
