package world;

import render.Mesh;

/**
 * Minecraft-like spawn: load a full ring of chunks synchronously before gameplay,
 * so collision and meshes exist under the player.
 */
public final class ChunkStreaming {
    private ChunkStreaming() {}

    /** Loads every chunk in the square [center±radius] that is not already loaded (no per-frame cap). */
    public static void preloadSquare(World world, WorldGenerator generator, int centerCx, int centerCz, int chunkRadius) {
        for (int cx = centerCx - chunkRadius; cx <= centerCx + chunkRadius; cx++) {
            for (int cz = centerCz - chunkRadius; cz <= centerCz + chunkRadius; cz++) {
                if (world.getChunk(cx, cz) != null) continue;
                Chunk chunk = new Chunk();
                byte[] saved = WorldPersistence.loadChunk(cx, cz);
                if (saved != null) {
                    chunk.setData(saved);
                } else {
                    generator.generate(chunk, cx, cz);
                }
                Mesh mesh = ChunkMesher.buildMesh(chunk);
                world.addChunk(cx, cz, chunk, mesh);
            }
        }
    }

    /** Highest solid voxel in column (world XZ); returns {@code -1} if column is air in loaded data. */
    public static int topSolidY(World world, int worldX, int worldZ) {
        for (int y = Chunk.SIZE - 1; y >= 0; y--) {
            if (world.getVoxel(worldX, y, worldZ) != 0) {
                return y;
            }
        }
        return -1;
    }
}
