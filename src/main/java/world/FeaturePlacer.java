package world;

import java.util.SplittableRandom;

/**
 * Deterministic trees and ground flora from chunk coordinates + world seed.
 * Uses sparse random attempts (not full-column scans) to keep chunk generation cheap.
 * <p>
 * Scale: one Minecraft-style block edge = 64 engine voxels (see {@link #VOXELS_PER_MC_BLOCK}).
 */
public final class FeaturePlacer {
    private static final byte STONE = 1;
    private static final byte GRASS = 2;
    private static final byte SAND = 3;
    private static final byte DIRT = 4;
    private static final byte SNOW = 5;
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
    private static final int CASTLE_GRID_SPACING = Chunk.SIZE * 72;
    private static final int CASTLE_MIN_HALF = 70;
    private static final int CASTLE_MAX_HALF = 120;
    private static final int CASTLE_SPAWN_CHANCE = 100; // 1/100 grid cells
    private static final int SPAWN_CASTLE_HALF = 180;
    private static final int SPAWN_CASTLE_CENTER_X = 0;
    private static final int SPAWN_CASTLE_CENTER_Z = 0;
    /** Grass clumps: short stems (MC tall-grass is tiny vs block; at 64:1 use a few voxels only). */
    private static final int FLORA_STEM_MIN = 2;
    private static final int FLORA_STEM_MAX = 7;
    private static final int TRUNK_WIDTH = 2;
    private static final int TRUNK_DEPTH = 2;

    private FeaturePlacer() {}

    public static void decorate(Chunk chunk, int chunkX, int chunkZ, long worldSeed, int[] firstAirY, byte[] biomes) {
        long mix = worldSeed
                ^ (long) chunkX * 0xC13FA9A902A6328FL
                ^ (long) chunkZ * 0x6A09E667F3BCC909L;
        SplittableRandom rng = new SplittableRandom(mix);
        placeMegaCastles(chunk, chunkX, chunkZ, worldSeed, firstAirY, biomes);
        placeTrees(chunk, firstAirY, biomes, rng);
        placeFlora(chunk, firstAirY, biomes, rng);
    }

    private static void placeTrees(Chunk chunk, int[] firstAirY, byte[] biomes, SplittableRandom rng) {
        for (int attempt = 0; attempt < TREE_ATTEMPTS; attempt++) {
            int x = rng.nextInt(SIZE);
            int z = rng.nextInt(SIZE);
            if (x < EDGE_MARGIN || z < EDGE_MARGIN
                    || x > SIZE - EDGE_MARGIN - TRUNK_WIDTH || z > SIZE - EDGE_MARGIN - TRUNK_DEPTH) {
                continue;
            }
            int h = firstAirY[x + z * SIZE];
            byte biome = biomes[x + z * SIZE];
            if (h < 1 || chunk.getVoxel(x, h - 1, z) != GRASS) {
                continue;
            }
            int rarity = biome == WorldGenerator.BIOME_ALPINE ? 180 : 64;
            if (rng.nextInt(rarity) != 0) {
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

    private static void placeMegaCastles(Chunk chunk, int chunkX, int chunkZ, long worldSeed, int[] firstAirY, byte[] biomes) {
        int chunkMinX = chunkX * SIZE;
        int chunkMinZ = chunkZ * SIZE;
        int chunkMaxX = chunkMinX + SIZE - 1;
        int chunkMaxZ = chunkMinZ + SIZE - 1;

        // Always place one massive fortress around spawn so it is guaranteed to be discoverable.
        if (intersects(chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ, SPAWN_CASTLE_CENTER_X, SPAWN_CASTLE_CENTER_Z, SPAWN_CASTLE_HALF)) {
            carveCastleInChunk(chunk, chunkMinX, chunkMinZ, firstAirY, biomes,
                    SPAWN_CASTLE_CENTER_X, SPAWN_CASTLE_CENTER_Z, SPAWN_CASTLE_HALF, worldSeed, 0, 0);
        }

        int minCellX = Math.floorDiv(chunkMinX - CASTLE_MAX_HALF, CASTLE_GRID_SPACING);
        int maxCellX = Math.floorDiv(chunkMaxX + CASTLE_MAX_HALF, CASTLE_GRID_SPACING);
        int minCellZ = Math.floorDiv(chunkMinZ - CASTLE_MAX_HALF, CASTLE_GRID_SPACING);
        int maxCellZ = Math.floorDiv(chunkMaxZ + CASTLE_MAX_HALF, CASTLE_GRID_SPACING);

        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                long h = hash(worldSeed, cellX, cellZ, 0x9E3779B97F4A7C15L);
                if (Math.floorMod(h, CASTLE_SPAWN_CHANCE) != 0) {
                    continue;
                }
                int jitterX = (int) (Math.floorMod(hash(worldSeed, cellX, cellZ, 0xA24BAED4963EE407L), CASTLE_GRID_SPACING / 2L)
                        - CASTLE_GRID_SPACING / 4L);
                int jitterZ = (int) (Math.floorMod(hash(worldSeed, cellX, cellZ, 0x3C79AC492BA7B653L), CASTLE_GRID_SPACING / 2L)
                        - CASTLE_GRID_SPACING / 4L);
                int centerX = cellX * CASTLE_GRID_SPACING + CASTLE_GRID_SPACING / 2 + jitterX;
                int centerZ = cellZ * CASTLE_GRID_SPACING + CASTLE_GRID_SPACING / 2 + jitterZ;
                int half = CASTLE_MIN_HALF + (int) Math.floorMod(hash(worldSeed, cellX, cellZ, 0x1C69B3F74AC4AE35L),
                        (CASTLE_MAX_HALF - CASTLE_MIN_HALF + 1L));

                if (!intersects(chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ, centerX, centerZ, half)) {
                    continue;
                }
                carveCastleInChunk(chunk, chunkMinX, chunkMinZ, firstAirY, biomes, centerX, centerZ, half, worldSeed, cellX, cellZ);
            }
        }
    }

    private static void carveCastleInChunk(Chunk chunk, int chunkMinX, int chunkMinZ, int[] firstAirY, byte[] biomes,
                                           int centerX, int centerZ, int half, long worldSeed, int cellX, int cellZ) {
        int floorY = 16 + (int) Math.floorMod(hash(worldSeed, cellX, cellZ, 0x8B8B8B8B8B8B8B8BL), 4L);
        int wallHeight = 22 + (int) Math.floorMod(hash(worldSeed, cellX, cellZ, 0xDB4F0B9175AE2165L), 6L);
        int wallThickness = Math.max(4, half / 14);
        int towerRadius = Math.max(10, half / 8);
        int towerHeight = wallHeight + 8;
        int gateHalf = Math.max(8, half / 11);
        int gateHeight = Math.min(towerHeight - 4, 14);
        int wallTopY = Math.min(SIZE - 2, floorY + wallHeight);
        int towerTopY = Math.min(SIZE - 2, floorY + towerHeight);
        int roofY = Math.min(SIZE - 2, wallTopY + 1);

        for (int lx = 0; lx < SIZE; lx++) {
            int gx = chunkMinX + lx;
            int dx = gx - centerX;
            if (Math.abs(dx) > half) continue;
            for (int lz = 0; lz < SIZE; lz++) {
                int gz = chunkMinZ + lz;
                int dz = gz - centerZ;
                if (Math.abs(dz) > half) continue;

                byte biome = biomes[lx + lz * SIZE];
                byte block = (biome == WorldGenerator.BIOME_DESERT) ? SAND : STONE;
                byte floor = (biome == WorldGenerator.BIOME_TEMPERATE) ? DIRT : block;

                boolean inWallBand = Math.abs(dx) >= half - wallThickness || Math.abs(dz) >= half - wallThickness;
                boolean inGateCut = (dz <= -half + wallThickness && Math.abs(dx) <= gateHalf);
                boolean insideCastle = Math.abs(dx) < half - wallThickness && Math.abs(dz) < half - wallThickness;

                // Build a continuous foundation and flat floor.
                for (int y = 0; y < floorY; y++) {
                    chunk.setVoxel(lx, y, lz, block);
                }
                chunk.setVoxel(lx, floorY, lz, floor);

                // Keep interior clear and level.
                if (insideCastle) {
                    for (int y = floorY + 1; y < roofY; y++) {
                        chunk.setVoxel(lx, y, lz, (byte) 0);
                    }
                }

                // Outer walls.
                if (inWallBand && !inGateCut) {
                    for (int y = floorY + 1; y <= wallTopY; y++) {
                        chunk.setVoxel(lx, y, lz, block);
                    }
                }

                // Explicit front gate opening.
                if (inGateCut) {
                    for (int y = floorY + 1; y <= floorY + gateHeight; y++) {
                        chunk.setVoxel(lx, y, lz, (byte) 0);
                    }
                }

                // Continuous roof over the full enclosed area.
                if (insideCastle || inWallBand) {
                    chunk.setVoxel(lx, roofY, lz, block);
                }

                boolean nearCorner = (Math.abs(Math.abs(dx) - half) <= towerRadius)
                        && (Math.abs(Math.abs(dz) - half) <= towerRadius);
                if (nearCorner) {
                    int radial = Math.abs(Math.abs(dx) - half) + Math.abs(Math.abs(dz) - half);
                    if (radial <= towerRadius) {
                        for (int y = floorY + 1; y <= towerTopY; y++) {
                            chunk.setVoxel(lx, y, lz, block);
                        }
                        int towerRoofY = Math.min(SIZE - 1, towerTopY + 1);
                        if (radial <= towerRadius / 2) {
                            chunk.setVoxel(lx, towerRoofY, lz, block);
                        }
                    }
                }
            }
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
    private static void placeFlora(Chunk chunk, int[] firstAirY, byte[] biomes, SplittableRandom rng) {
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
                    byte biome = biomes[px + pz * SIZE];
                    if (biome == WorldGenerator.BIOME_DESERT || biome == WorldGenerator.BIOME_ALPINE) {
                        continue;
                    }
                    if (h < 1 || chunk.getVoxel(px, h - 1, pz) != GRASS || chunk.getVoxel(px, h, pz) != 0) {
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

    private static long hash(long seed, int x, int z, long salt) {
        long h = seed ^ salt;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdl;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53l;
        h ^= (h >>> 33);
        return h;
    }

    private static boolean intersects(int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ,
                                      int centerX, int centerZ, int half) {
        return centerX + half >= chunkMinX
                && centerX - half <= chunkMaxX
                && centerZ + half >= chunkMinZ
                && centerZ - half <= chunkMaxZ;
    }

}
