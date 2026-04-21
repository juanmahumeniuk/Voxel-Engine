package world;

import math.PerlinNoise;

public class WorldGenerator {
    private PerlinNoise elevationNoise;
    private PerlinNoise moistureNoise;
    private PerlinNoise temperatureNoise;

    public WorldGenerator(long seed) {
        elevationNoise = new PerlinNoise(seed);
        moistureNoise = new PerlinNoise(seed + 100);
        temperatureNoise = new PerlinNoise(seed + 200);
    }

    public void generate(Chunk chunk, int chunkX, int chunkZ) {
        for (int z = 0; z < Chunk.SIZE; z++) {
            for (int x = 0; x < Chunk.SIZE; x++) {
                int globalX = chunkX * Chunk.SIZE + x;
                int globalZ = chunkZ * Chunk.SIZE + z;

                // Increased Scale: Lower frequency = Harder mountains and larger plains
                double nxElev = globalX * 0.002;
                double nzElev = globalZ * 0.002;

                // FBM for elevation
                double e = 1.00 * elevationNoise.noise(nxElev, nzElev)
                         + 0.50 * elevationNoise.noise(nxElev * 2, nzElev * 2)
                         + 0.25 * elevationNoise.noise(nxElev * 4, nzElev * 4);
                
                e = (e + 1.0) / 2.0; // Normalized 0..1
                
                // Steeper exponent (3.0) creates vast low plains and sudden high peaks
                int height = 5 + (int) (Math.pow(e, 3.0) * 55); 
                if (height < 1) height = 1;
                if (height >= Chunk.SIZE) height = Chunk.SIZE - 1;

                // Biomes scale (0.0005 makes them massive)
                double nxBio = globalX * 0.0005;
                double nzBio = globalZ * 0.0005;

                double t = temperatureNoise.noise(nxBio, nzBio) * 0.5 + 0.5;
                double m = moistureNoise.noise(nxBio, nzBio) * 0.5 + 0.5;

                byte surfaceBlock;
                byte subsurfaceBlock;

                if (t < 0.35) { // Cold
                    surfaceBlock = 5; // Snow
                    subsurfaceBlock = 4; // Dirt
                } else if (t > 0.6 && m < 0.45) { // Hot and Dry
                    surfaceBlock = 3; // Sand
                    subsurfaceBlock = 3; // Sand
                } else { // Temperate
                    surfaceBlock = 2; // Grass
                    subsurfaceBlock = 4; // Dirt
                }

                for (int y = 0; y < height; y++) {
                    if (y == height - 1) {
                        chunk.setVoxel(x, y, z, surfaceBlock);
                    } else if (y > height - 6) {
                        chunk.setVoxel(x, y, z, subsurfaceBlock);
                    } else {
                        chunk.setVoxel(x, y, z, (byte) 1); // Stone
                    }
                }
            }
        }
    }
}
