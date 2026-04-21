package world;

import java.util.SplittableRandom;

/**
 * Deterministic trees and ground flora from chunk coordinates + world seed.
 * Uses sparse random attempts (not full-column scans) to keep chunk generation cheap.
 */
public final class FeaturePlacer {
    public static final byte LOG = 6;
    public static final byte LEAVES = 7;
    public static final byte FLORA = 8;

    private static final int SIZE = Chunk.SIZE;
    /** Keep trunks away from chunk edges so most canopies stay inside one chunk. */
    private static final int EDGE_MARGIN = 4;
    private static final int TREE_ATTEMPTS = 72;
    private static final int FLORA_ATTEMPTS = 320;

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
            if (x < EDGE_MARGIN || x >= SIZE - EDGE_MARGIN || z < EDGE_MARGIN || z >= SIZE - EDGE_MARGIN) {
                continue;
            }
            int h = firstAirY[x + z * SIZE];
            if (h < 1 || chunk.getVoxel(x, h - 1, z) != 2) {
                continue;
            }
            if (rng.nextInt(56) != 0) {
                continue;
            }
            int trunkH = 4 + rng.nextInt(4);
            if (h + trunkH + 4 >= SIZE) {
                continue;
            }
            if (!columnClear(chunk, x, z, h, trunkH)) {
                continue;
            }
            for (int k = 0; k < trunkH; k++) {
                chunk.setVoxel(x, h + k, z, LOG);
            }
            int trunkTop = h + trunkH - 1;
            placeLeafBlob(chunk, rng, x, z, trunkTop);
        }
    }

    /** Trunk column must be air so we do not erase structures (cheap check). */
    private static boolean columnClear(Chunk chunk, int x, int z, int airY, int trunkH) {
        for (int k = 0; k < trunkH; k++) {
            if (chunk.getVoxel(x, airY + k, z) != 0) {
                return false;
            }
        }
        return true;
    }

    private static void placeLeafBlob(Chunk chunk, SplittableRandom rng, int x, int z, int trunkTop) {
        for (int dy = -1; dy <= 2; dy++) {
            int y = trunkTop + dy;
            if (y < 0 || y >= SIZE) {
                continue;
            }
            int spread = dy <= 0 ? 2 : 1;
            for (int dx = -spread; dx <= spread; dx++) {
                for (int dz = -spread; dz <= spread; dz++) {
                    if (dx == 0 && dz == 0 && y <= trunkTop) {
                        continue;
                    }
                    int nx = x + dx;
                    int nz = z + dz;
                    if (nx < 0 || nx >= SIZE || nz < 0 || nz >= SIZE) {
                        continue;
                    }
                    if (rng.nextInt(12) == 0 && dy > 0) {
                        continue;
                    }
                    if (chunk.getVoxel(nx, y, nz) == 0) {
                        chunk.setVoxel(nx, y, nz, LEAVES);
                    }
                }
            }
        }
    }

    private static void placeFlora(Chunk chunk, int[] firstAirY, SplittableRandom rng) {
        for (int attempt = 0; attempt < FLORA_ATTEMPTS; attempt++) {
            int x = rng.nextInt(SIZE);
            int z = rng.nextInt(SIZE);
            int h = firstAirY[x + z * SIZE];
            if (h < 1 || chunk.getVoxel(x, h - 1, z) != 2 || chunk.getVoxel(x, h, z) != 0) {
                continue;
            }
            if (rng.nextInt(10) != 0) {
                continue;
            }
            chunk.setVoxel(x, h, z, FLORA);
        }
    }
}
