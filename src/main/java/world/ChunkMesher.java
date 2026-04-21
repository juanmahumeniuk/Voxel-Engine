package world;

import render.Mesh;
import render.MeshCpuData;

public class ChunkMesher {

    /** CPU-only; safe from worker threads (no OpenGL). Uses greedy meshing for far fewer triangles. */
    public static MeshCpuData buildMeshCpuData(Chunk chunk) {
        return GreedyChunkMesher.build(chunk);
    }

    /** OpenGL thread only. */
    public static Mesh buildMesh(Chunk chunk) {
        MeshCpuData data = buildMeshCpuData(chunk);
        return data == null ? null : new Mesh(data);
    }
}
