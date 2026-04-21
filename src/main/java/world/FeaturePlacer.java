package world;

import java.util.SplittableRandom;

/**
 * Deterministic trees and ground flora from chunk coordinates + world seed.
 * Uses sparse random attempts (not full-column scans) to keep chunk generation cheap.
 * <p>
 * Scale: one Minecraft-style block edge = 64 engine voxels (see {@link #VOXELS_PER_MC_BLOCK}).
 */
public final class FeaturePlacer {
    public static final byte LOG = 6;
    public static final byte LEAVES = 7;
    public static final byte FLORA = 8;

    /** Voxels along one edge of a single “Minecraft block” at this resolution. */
    public static final int VOXELS_PER_MC_BLOCK = 64;

    private static final int SIZE = Chunk.SIZE;
    /** Leaf canopy radius in voxels (MC quarter-block). */
    private static final int LEAF_RADIUS = VOXELS_PER_MC_BLOCK / 8;
    /** Keep canopies inside the chunk horizontal bounds. */
    private static final int EDGE_MARGIN = LEAF_RADIUS + 4;
    private static final int TREE_ATTEMPTS = 56;
    private static final int FLORA_PATCH_ATTEMPTS = 96;
    /** Grass clumps: short stems (MC tall-grass is tiny vs block; at 64:1 use a few voxels only). */
    private static final int FLORA_STEM_MIN = 2;
    private static final int FLORA_STEM_MAX = 7;
    private static final int TRUNK_WIDTH = 2;
    private static final int TRUNK_DEPTH = 2;

    private FeaturePlacer() {}

    public static void decorate(Chunk chunk, int chunkX, int chunkZ, long worldSeed, int[] firstAirY) {
        long mix = worldSeed
                ^ (long) chunkX * 0xC13FA9A902A6328FL
                ^ (long) chunkZ * 0x6A09E667F3BCC909L;
        SplittableRandom rng = new SplittableRandom(mix);
        placeTrees(chunk, firstAirY, rng);
        placeFlora(chunk, firstAirY, rng);
    }

    private static void placeTrees(Chunk chunk, int[] firstAirY, SplittableRandom rng) {
        for (int attempt = 0; attempt < TREE_ATTEMPTS; attempt++) {
            int x = rng.nextInt(SIZE);
            int z = rng.nextInt(SIZE);
            if (x < EDGE_MARGIN || z < EDGE_MARGIN
                    || x > SIZE - EDGE_MARGIN - TRUNK_WIDTH || z > SIZE - EDGE_MARGIN - TRUNK_DEPTH) {
                continue;
            }
            int h = firstAirY[x + z * SIZE];
            if (h < 1 || chunk.getVoxel(x, h - 1, z) != 2) {
                continue;
            }
            if (rng.nextInt(64) != 0) {
                continue;
            }
            // Trunk: ~⅛–¼ MC block tall (8–16 voxels), 2×2 so it reads as wood not a hair.
            int trunkH = VOXELS_PER_MC_BLOCK / 8 + rng.nextInt(VOXELS_PER_MC_BLOCK / 8);
            int headroomLeaves = 10;
            if (h + trunkH + headroomLeaves >= SIZE) {
                continue;
            }
            if (!trunkFootClear(chunk, x, z, h, trunkH)) {
                continue;
            }
            for (int k = 0; k < trunkH; k++) {
                for (int ox = 0; ox < TRUNK_WIDTH; ox++) {
                    for (int oz = 0; oz < TRUNK_DEPTH; oz++) {
                        chunk.setVoxel(x + ox, h + k, z + oz, LOG);
                    }
                }
            }
            int trunkTop = h + trunkH - 1;
            placeLeafBlob(chunk, rng, x, z, trunkTop, TRUNK_WIDTH, TRUNK_DEPTH);
        }
    }

    /** Single-column air check (flora stems). */
    private static boolean columnClear(Chunk chunk, int x, int z, int airY, int height) {
        for (int k = 0; k < height; k++) {
            if (chunk.getVoxel(x, airY + k, z) != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean trunkFootClear(Chunk chunk, int footX, int footZ, int airY, int trunkH) {
        for (int k = 0; k < trunkH; k++) {
            for (int ox = 0; ox < TRUNK_WIDTH; ox++) {
                for (int oz = 0; oz < TRUNK_DEPTH; oz++) {
                    if (chunk.getVoxel(footX + ox, airY + k, footZ + oz) != 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void placeLeafBlob(Chunk chunk, SplittableRandom rng, int footX, int footZ, int trunkTop,
            int trunkW, int trunkD) {
        final int R = LEAF_RADIUS;
        for (int dy = -2; dy <= 5; dy++) {
            int y = trunkTop + dy;
            if (y < 0 || y >= SIZE) {
                continue;
            }
            int spread = R;
            if (dy > 2) {
                spread = Math.max(2, R - 3);
            } else if (dy > 0) {
                spread = Math.max(3, R - 1);
            }
            for (int dx = -spread; dx <= spread; dx++) {
                for (int dz = -spread; dz <= spread; dz++) {
                    int nx = footX + dx;
                    int nz = footZ + dz;
                    if (nx >= footX && nx < footX + trunkW && nz >= footZ && nz < footZ + trunkD && y <= trunkTop) {
                        continue;
                    }
                    if (nx < 0 || nx >= SIZE || nz < 0 || nz >= SIZE) {
                        continue;
                    }
                    if (rng.nextInt(40) == 0 && dy > 1) {
                        continue;
                    }
                    if (chunk.getVoxel(nx, y, nz) == 0) {
                        chunk.setVoxel(nx, y, nz, LEAVES);
                    }
                }
            }
        }
    }

    /**
     * Low, wide grass clumps (3×3 patch of short stems). Avoids 1×1×32 “needles” that killed FPS and looked wrong.
     */
    private static void placeFlora(Chunk chunk, int[] firstAirY, SplittableRandom rng) {
        for (int attempt = 0; attempt < FLORA_PATCH_ATTEMPTS; attempt++) {
            if (rng.nextInt(48) != 0) {
                continue;
            }
            int cx = 1 + rng.nextInt(SIZE - 2);
            int cz = 1 + rng.nextInt(SIZE - 2);
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    if (rng.nextInt(3) == 0) {
                        continue;
                    }
                    int px = cx + ox;
                    int pz = cz + oz;
                    int h = firstAirY[px + pz * SIZE];
                    if (h < 1 || chunk.getVoxel(px, h - 1, pz) != 2 || chunk.getVoxel(px, h, pz) != 0) {
                        continue;
                    }
                    int stem = FLORA_STEM_MIN + rng.nextInt(FLORA_STEM_MAX - FLORA_STEM_MIN + 1);
                    int room = SIZE - h;
                    stem = Math.min(stem, room);
                    if (stem < FLORA_STEM_MIN) {
                        continue;
                    }
                    if (!columnClear(chunk, px, pz, h, stem)) {
                        continue;
                    }
                    for (int k = 0; k < stem; k++) {
                        chunk.setVoxel(px, h + k, pz, FLORA);
                    }
                }
            }
        }
    }
}
