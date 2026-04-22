package world;

import math.PerlinNoise;

public class WorldGenerator {
    public static final byte BIOME_TEMPERATE = 0;
    public static final byte BIOME_DESERT = 1;
    public static final byte BIOME_TUNDRA = 2;
    public static final byte BIOME_ALPINE = 3;

    private static final int TARGET_MAX_MOUNTAIN_BLOCKS = 600;
    private static final double SPAWN_MOUNTAIN_RADIUS = 780.0;
    private static final int MOUNTAIN_GRID = 2200;

    private final long seed;
    private final PerlinNoise elevationNoise;
    private final PerlinNoise moistureNoise;
    private final PerlinNoise temperatureNoise;

    public WorldGenerator(long seed) {
        this.seed = seed;
        elevationNoise = new PerlinNoise(seed);
        moistureNoise = new PerlinNoise(seed + 100);
        temperatureNoise = new PerlinNoise(seed + 200);
    }

    public void generate(Chunk chunk, int chunkX, int chunkZ) {
        int n = Chunk.SIZE;
        int[] firstAirY = new int[n * n];
        byte[] biomes = new byte[n * n];

        for (int z = 0; z < Chunk.SIZE; z++) {
            for (int x = 0; x < Chunk.SIZE; x++) {
                int globalX = chunkX * Chunk.SIZE + x;
                int globalZ = chunkZ * Chunk.SIZE + z;

                double nxElev = globalX * 0.0014;
                double nzElev = globalZ * 0.0014;
                double nxBio = globalX * 0.00055;
                double nzBio = globalZ * 0.00055;

                double continental = fbm(elevationNoise, nxElev, nzElev, 3);
                double erosion = fbm(moistureNoise, nxElev * 1.3 + 37.0, nzElev * 1.3 - 17.0, 3);
                double ridge = ridged(elevationNoise, nxElev * 1.9 + 130.0, nzElev * 1.9 + 71.0, 3);
                double detail = fbm(elevationNoise, nxElev * 5.5 + 19.0, nzElev * 5.5 - 43.0, 2);
                double mountainSignal = mountainSignal(globalX, globalZ);

                double peaksMask = Math.pow(clamp01((continental - 0.30) / 0.70), 1.8);
                double mountains = Math.pow(ridge, 2.2) * peaksMask * (0.30 + 0.70 * (1.0 - erosion));
                double rugged = Math.pow(ridge, 1.5) * (0.5 + 0.5 * detail);
                double normalizedHeight = clamp01(0.04
                        + continental * 0.26
                        + mountains * 0.32
                        + rugged * 0.14
                        + detail * 0.08
                        + mountainSignal * 0.25);
                if (mountainSignal > 0.50) {
                    normalizedHeight = clamp01(normalizedHeight
                            + (mountainSignal - 0.50) * (0.45 + 0.45 * ridge));
                }

                int virtualHeightBlocks = 30 + (int) Math.round(Math.pow(normalizedHeight, 1.05)
                        * (TARGET_MAX_MOUNTAIN_BLOCKS - 30));
                int height = 1 + (int) Math.round((virtualHeightBlocks / (double) TARGET_MAX_MOUNTAIN_BLOCKS) * (Chunk.SIZE - 2));
                if (height < 1) height = 1;
                if (height >= Chunk.SIZE) height = Chunk.SIZE - 1;

                double t = temperatureNoise.noise(nxBio, nzBio) * 0.5 + 0.5;
                double m = moistureNoise.noise(nxBio, nzBio) * 0.5 + 0.5;
                byte biome;

                if (mountainSignal > 0.45 || (normalizedHeight > 0.70 && mountains > 0.18)) {
                    biome = BIOME_ALPINE;
                } else if (t < 0.34) {
                    biome = BIOME_TUNDRA;
                } else if (t > 0.62 && m < 0.43) {
                    biome = BIOME_DESERT;
                } else {
                    biome = BIOME_TEMPERATE;
                }

                byte surfaceBlock;
                byte subsurfaceBlock;

                if (biome == BIOME_TUNDRA || biome == BIOME_ALPINE) {
                    surfaceBlock = 5; // Snow
                    subsurfaceBlock = 4; // Dirt
                } else if (biome == BIOME_DESERT) {
                    surfaceBlock = 3; // Sand
                    subsurfaceBlock = 3; // Sand
                } else {
                    surfaceBlock = 2; // Grass
                    subsurfaceBlock = 4; // Dirt
                }

                for (int y = 0; y < height; y++) {
                    if (y == height - 1) {
                        chunk.setVoxel(x, y, z, surfaceBlock);
                    } else if (y > height - 5) {
                        chunk.setVoxel(x, y, z, subsurfaceBlock);
                    } else {
                        chunk.setVoxel(x, y, z, (byte) 1); // Stone
                    }
                }
                firstAirY[x + z * n] = height;
                biomes[x + z * n] = biome;
            }
        }

        FeaturePlacer.decorate(chunk, chunkX, chunkZ, seed, firstAirY, biomes);
    }

    private static double fbm(PerlinNoise noise, double x, double z, int octaves) {
        double sum = 0.0;
        double amp = 1.0;
        double freq = 1.0;
        double ampSum = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += noise.noise(x * freq, z * freq) * amp;
            ampSum += amp;
            amp *= 0.5;
            freq *= 2.0;
        }
        return clamp01((sum / ampSum) * 0.5 + 0.5);
    }

    private static double ridged(PerlinNoise noise, double x, double z, int octaves) {
        double sum = 0.0;
        double amp = 1.0;
        double freq = 1.0;
        double ampSum = 0.0;
        for (int i = 0; i < octaves; i++) {
            double n = Math.abs(noise.noise(x * freq, z * freq));
            double r = 1.0 - n;
            sum += r * amp;
            ampSum += amp;
            amp *= 0.55;
            freq *= 2.0;
        }
        return clamp01(sum / ampSum);
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private double mountainSignal(int globalX, int globalZ) {
        double gx = globalX;
        double gz = globalZ;
        double forcedSpawnPeak = radialPeak(gx, gz, 0.0, 0.0, SPAWN_MOUNTAIN_RADIUS) * 1.25;
        double ringPeakA = radialPeak(gx, gz, 900.0, -300.0, 620.0);
        double ringPeakB = radialPeak(gx, gz, -950.0, 450.0, 680.0);
        double guaranteedPeaks = Math.max(forcedSpawnPeak, Math.max(ringPeakA, ringPeakB));

        int cellX = Math.floorDiv(globalX, MOUNTAIN_GRID);
        int cellZ = Math.floorDiv(globalZ, MOUNTAIN_GRID);
        double best = 0.0;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int cx = cellX + ox;
                int cz = cellZ + oz;
                long h = hash(seed, cx, cz, 0x7F4A7C159E3779B9L);
                int jitterX = (int) (Math.floorMod(hash(seed, cx, cz, 0xC2B2AE3D27D4EB4FL), MOUNTAIN_GRID / 2L) - MOUNTAIN_GRID / 4L);
                int jitterZ = (int) (Math.floorMod(hash(seed, cx, cz, 0x165667B19E3779F9L), MOUNTAIN_GRID / 2L) - MOUNTAIN_GRID / 4L);
                double centerX = cx * (double) MOUNTAIN_GRID + MOUNTAIN_GRID / 2.0 + jitterX;
                double centerZ = cz * (double) MOUNTAIN_GRID + MOUNTAIN_GRID / 2.0 + jitterZ;
                double radius = 900.0 + Math.floorMod(h, 700L);
                double strength = 0.55 + (Math.floorMod(h >>> 8, 45L) / 100.0);
                double peak = radialPeak(gx, gz, centerX, centerZ, radius) * strength;
                if (peak > best) best = peak;
            }
        }
        return clamp01(Math.max(best, guaranteedPeaks));
    }

    private static double radialPeak(double x, double z, double cx, double cz, double radius) {
        double dx = x - cx;
        double dz = z - cz;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d >= radius) return 0.0;
        double t = 1.0 - d / radius;
        return t * t * (3.0 - 2.0 * t);
    }

    private static long hash(long worldSeed, int x, int z, long salt) {
        long h = worldSeed ^ salt;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdl;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53l;
        h ^= (h >>> 33);
        return h;
    }
}
