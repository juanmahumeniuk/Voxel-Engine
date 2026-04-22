package world;

import render.Mesh;
import java.util.HashMap;
import java.util.Map;

public class World {
    public static class ChunkData {
        public Chunk chunk;
        /** Written on main thread or mesh worker; render thread reads for draw. */
        public volatile Mesh mesh;
        public int cx, cz;
        /** Solid voxels in this chunk when it was added (for global LOD count). */
        public long solidVoxelCount;
    }
    
    private Map<String, ChunkData> activeChunks = new HashMap<>();
    private long totalSolidVoxels = 0;

    public void addChunk(int cx, int cz, Chunk chunk, Mesh mesh) {
        ChunkData cd = new ChunkData();
        cd.chunk = chunk;
        cd.mesh = mesh;
        cd.cx = cx;
        cd.cz = cz;
        cd.solidVoxelCount = Chunk.countSolidVoxels(chunk);
        totalSolidVoxels += cd.solidVoxelCount;
        activeChunks.put(cx + "," + cz, cd);
    }

    /** Attach GPU mesh when async build finishes; returns false if chunk was unloaded or mesh already set. */
    public synchronized boolean assignChunkMeshIfPending(int cx, int cz, Mesh mesh) {
        ChunkData cd = activeChunks.get(cx + "," + cz);
        if (cd == null || cd.mesh != null) {
            return false;
        }
        cd.mesh = mesh;
        return true;
    }
    
    public void removeChunk(int cx, int cz) {
        ChunkData cd = activeChunks.remove(cx + "," + cz);
        if (cd != null) {
            totalSolidVoxels -= cd.solidVoxelCount;
            if (totalSolidVoxels < 0) totalSolidVoxels = 0;
            // Persistence: Save chunk data when it's unloaded from memory
            WorldPersistence.saveChunk(cx, cz, cd.chunk.getData());
            if (cd.mesh != null) cd.mesh.cleanup();
        }
    }

    /** Sum of solid voxels across all loaded chunks (used for texture LOD). */
    public long getTotalSolidVoxels() {
        return totalSolidVoxels;
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

    /**
     * Set one voxel in loaded chunks and rebuild that chunk mesh immediately on the render thread.
     */
    public boolean setVoxelAndRebuild(int x, int y, int z, byte type) {
        if (y < 0 || y >= Chunk.SIZE) return false;
        int cx = Math.floorDiv(x, Chunk.SIZE);
        int cz = Math.floorDiv(z, Chunk.SIZE);
        ChunkData cd = activeChunks.get(cx + "," + cz);
        if (cd == null) return false;

        int lx = x - cx * Chunk.SIZE;
        int lz = z - cz * Chunk.SIZE;
        byte old = cd.chunk.getVoxel(lx, y, lz);
        if (old == type) return false;

        cd.chunk.setVoxel(lx, y, lz, type);
        if (old != 0) totalSolidVoxels--;
        if (type != 0) totalSolidVoxels++;
        if (totalSolidVoxels < 0) totalSolidVoxels = 0;
        cd.solidVoxelCount = Chunk.countSolidVoxels(cd.chunk);

        if (cd.mesh != null) {
            cd.mesh.cleanup();
        }
        cd.mesh = ChunkMesher.buildMesh(cd.chunk);
        return true;
    }
}
