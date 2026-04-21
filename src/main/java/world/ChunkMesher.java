package world;

import render.Mesh;
import java.util.ArrayList;
import java.util.List;

public class ChunkMesher {

    public static Mesh buildMesh(Chunk chunk) {
        List<Float> positions = new ArrayList<>();
        List<Float> colors = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        int CHUNK_SIZE = Chunk.SIZE;
        int index = 0;
        
        for (int y = 0; y < CHUNK_SIZE; y++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    byte voxel = chunk.getVoxel(x, y, z);
                    if (voxel == 0) continue;

                    float r = 0.5f, g = 0.5f, b = 0.5f;
                    switch (voxel) {
                        case 1: r = 0.5f; g = 0.5f; b = 0.5f; break; // Stone
                        case 2: r = 0.3f; g = 0.7f; b = 0.3f; break; // Grass
                        case 3: r = 0.9f; g = 0.8f; b = 0.5f; break; // Sand
                        case 4: r = 0.5f; g = 0.4f; b = 0.3f; break; // Dirt
                        case 5: r = 1.0f; g = 1.0f; b = 1.0f; break; // Snow
                    }

                    // Z+
                    if (z == CHUNK_SIZE - 1 || chunk.getVoxel(x, y, z + 1) == 0) {
                        float[] ao = { ao(chunk,x,y,z+1,-1,-1,0), ao(chunk,x,y,z+1,1,-1,0), ao(chunk,x,y,z+1,1,1,0), ao(chunk,x,y,z+1,-1,1,0) };
                        index = addFace(positions, colors, normals, indices, index, x, y, z, new float[]{0,0,1, 1,0,1, 1,1,1, 0,1,1}, r, g, b, 0, 0, 1, ao);
                    }
                    // Z-
                    if (z == 0 || chunk.getVoxel(x, y, z - 1) == 0) {
                        float[] ao = { ao(chunk,x,y,z-1,1,-1,0), ao(chunk,x,y,z-1,-1,-1,0), ao(chunk,x,y,z-1,-1,1,0), ao(chunk,x,y,z-1,1,1,0) };
                        index = addFace(positions, colors, normals, indices, index, x, y, z, new float[]{1,0,0, 0,0,0, 0,1,0, 1,1,0}, r, g, b, 0, 0, -1, ao);
                    }
                    // Y+
                    if (y == CHUNK_SIZE - 1 || chunk.getVoxel(x, y + 1, z) == 0) {
                        float[] ao = { ao(chunk,x,y+1,z,-1,0,1), ao(chunk,x,y+1,z,1,0,1), ao(chunk,x,y+1,z,1,0,-1), ao(chunk,x,y+1,z,-1,0,-1) };
                        index = addFace(positions, colors, normals, indices, index, x, y, z, new float[]{0,1,1, 1,1,1, 1,1,0, 0,1,0}, r, g, b, 0, 1, 0, ao);
                    }
                    // Y-
                    if (y == 0 || chunk.getVoxel(x, y - 1, z) == 0) {
                        float[] ao = { ao(chunk,x,y-1,z,-1,0,-1), ao(chunk,x,y-1,z,1,0,-1), ao(chunk,x,y-1,z,1,0,1), ao(chunk,x,y-1,z,-1,0,1) };
                        index = addFace(positions, colors, normals, indices, index, x, y, z, new float[]{0,0,0, 1,0,0, 1,0,1, 0,0,1}, r, g, b, 0, -1, 0, ao);
                    }
                    // X+
                    if (x == CHUNK_SIZE - 1 || chunk.getVoxel(x + 1, y, z) == 0) {
                        float[] ao = { ao(chunk,x+1,y,z,0,-1,1), ao(chunk,x+1,y,z,0,-1,-1), ao(chunk,x+1,y,z,0,1,-1), ao(chunk,x+1,y,z,0,1,1) };
                        index = addFace(positions, colors, normals, indices, index, x, y, z, new float[]{1,0,1, 1,0,0, 1,1,0, 1,1,1}, r, g, b, 1, 0, 0, ao);
                    }
                    // X-
                    if (x == 0 || chunk.getVoxel(x - 1, y, z) == 0) {
                        float[] ao = { ao(chunk,x-1,y,z,0,-1,-1), ao(chunk,x-1,y,z,0,-1,1), ao(chunk,x-1,y,z,0,1,1), ao(chunk,x-1,y,z,0,1,-1) };
                        index = addFace(positions, colors, normals, indices, index, x, y, z, new float[]{0,0,0, 0,0,1, 0,1,1, 0,1,0}, r, g, b, -1, 0, 0, ao);
                    }
                }
            }
        }

        if (positions.isEmpty()) return null;
        float[] pArr = new float[positions.size()]; for(int i=0;i<pArr.length;i++) pArr[i]=positions.get(i);
        float[] cArr = new float[colors.size()]; for(int i=0;i<cArr.length;i++) cArr[i]=colors.get(i);
        float[] nArr = new float[normals.size()]; for(int i=0;i<nArr.length;i++) nArr[i]=normals.get(i);
        int[] idxArr = new int[indices.size()]; for(int i=0;i<idxArr.length;i++) idxArr[i]=indices.get(i);
        return new Mesh(pArr, cArr, nArr, idxArr);
    }

    private static int addFace(List<Float> p, List<Float> c, List<Float> n, List<Integer> idx, int i, int x, int y, int z, float[] off, float r, float g, float b, float nx, float ny, float nz, float[] ao) {
        for (int j = 0; j < 4; j++) {
            p.add(x + off[j * 3]); p.add(y + off[j * 3 + 1]); p.add(z + off[j * 3 + 2]);
            c.add(r * ao[j]); c.add(g * ao[j]); c.add(b * ao[j]);
            n.add(nx); n.add(ny); n.add(nz);
        }
        idx.add(i); idx.add(i + 1); idx.add(i + 2); idx.add(i + 2); idx.add(i + 3); idx.add(i);
        return i + 4;
    }

    private static float ao(Chunk c, int x, int y, int z, int dx, int dy, int dz) {
        // Simple Ambient Occlusion: checks 3 neighbors around a vertex
        // For a vertex, we check the two side blocks and the corner block.
        // We use the 7-argument version that matched the calls in the loop
        int s1, s2, corner;
        if (dx == 0) { // Side face X
            s1 = isSolid(c, x, y+dy, z) ? 1 : 0;
            s2 = isSolid(c, x, y, z+dz) ? 1 : 0;
            corner = isSolid(c, x, y+dy, z+dz) ? 1 : 0;
        } else if (dy == 0) { // Side face Y
            s1 = isSolid(c, x+dx, y, z) ? 1 : 0;
            s2 = isSolid(c, x, y, z+dz) ? 1 : 0;
            corner = isSolid(c, x+dx, y, z+dz) ? 1 : 0;
        } else { // Side face Z
            s1 = isSolid(c, x+dx, y, z) ? 1 : 0;
            s2 = isSolid(c, x, y+dy, z) ? 1 : 0;
            corner = isSolid(c, x+dx, y+dy, z) ? 1 : 0;
        }
        
        if (s1 == 1 && s2 == 1) return 0.5f;
        return 1.0f - (s1 + s2 + corner) * 0.15f;
    }

    private static boolean isSolid(Chunk c, int x, int y, int z) {
        if (x<0||x>=Chunk.SIZE||y<0||y>=Chunk.SIZE||z<0||z>=Chunk.SIZE) return false;
        return c.getVoxel(x, y, z) != 0;
    }
}
