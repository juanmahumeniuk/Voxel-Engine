package world;

import render.Mesh;
import java.util.HashMap;
import java.util.Map;

public class World {
    public static class ChunkData {
        public Chunk chunk;
        public Mesh mesh;
        public int cx, cz;
    }
    
    private Map<String, ChunkData> activeChunks = new HashMap<>();

    public void addChunk(int cx, int cz, Chunk chunk, Mesh mesh) {
        ChunkData cd = new ChunkData();
        cd.chunk = chunk;
        cd.mesh = mesh;
        cd.cx = cx;
        cd.cz = cz;
        activeChunks.put(cx + "," + cz, cd);
    }
    
    public void removeChunk(int cx, int cz) {
        ChunkData cd = activeChunks.remove(cx + "," + cz);
        if (cd != null) {
            // Persistence: Save chunk data when it's unloaded from memory
            WorldPersistence.saveChunk(cx, cz, cd.chunk.getData());
            if (cd.mesh != null) cd.mesh.cleanup();
        }
    }
    
    public Iterable<ChunkData> getActiveChunks() {
        return activeChunks.values();
    }
    
    public ChunkData getChunk(int cx, int cz) {
        return activeChunks.get(cx + "," + cz);
    }

    public byte getVoxel(int x, int y, int z) {
        if (y < 0 || y >= Chunk.SIZE) return 0;
        
        int cx = Math.floorDiv(x, Chunk.SIZE);
        int cz = Math.floorDiv(z, Chunk.SIZE);
        ChunkData cd = activeChunks.get(cx + "," + cz);
        
        if (cd == null) return 0; // Better for exploring: air wall of unloaded area

        int lx = x - cx * Chunk.SIZE;
        int lz = z - cz * Chunk.SIZE;
        return cd.chunk.getVoxel(lx, y, lz);
    }
}
