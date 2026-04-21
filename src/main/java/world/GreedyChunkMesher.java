package world;

import render.BlockTextureAtlas;
import render.MeshCpuData;

import java.util.ArrayList;
import java.util.List;

/**
 * Greedy voxel meshing: merges coplanar faces of the same material into large quads (far fewer triangles than naive cubing).
 * No per-vertex AO on merged quads (flat tint per quad) — major GPU/CPU win.
 */
public final class GreedyChunkMesher {
    private static final int S = Chunk.SIZE;

    private GreedyChunkMesher() {}

    public static MeshCpuData build(Chunk chunk) {
        List<Float> positions = new ArrayList<>();
        List<Float> colors = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Float> texCoords = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        int[] mask = new int[S * S];
        int[] x = new int[3];
        int[] q = new int[3];
        int[] du = new int[3];
        int[] dv = new int[3];

        for (int d = 0; d < 3; d++) {
            int u = (d + 1) % 3;
            int v = (d + 2) % 3;
            q[0] = 0;
            q[1] = 0;
            q[2] = 0;
            q[d] = 1;

            for (x[d] = -1; x[d] < S; ) {
                int n = 0;
                for (x[v] = 0; x[v] < S; x[v]++) {
                    for (x[u] = 0; x[u] < S; x[u]++) {
                        byte a = get(chunk, x[0], x[1], x[2]);
                        byte b = get(chunk, x[0] + q[0], x[1] + q[1], x[2] + q[2]);
                        int m = 0;
                        if (a != 0 && b != 0) {
                            m = 0;
                        } else if (a != 0) {
                            m = a & 0xFF;
                        } else if (b != 0) {
                            m = -(b & 0xFF);
                        }
                        mask[n++] = m;
                    }
                }
                int slice = x[d];

                for (int j = 0; j < S; j++) {
                    for (int i = 0; i < S; ) {
                        int c = mask[i + j * S];
                        if (c == 0) {
                            i++;
                            continue;
                        }
                        int w = 1;
                        while (i + w < S && mask[i + w + j * S] == c) {
                            w++;
                        }
                        int h = 1;
                        rowScan:
                        while (j + h < S) {
                            for (int k = 0; k < w; k++) {
                                if (mask[i + k + (j + h) * S] != c) {
                                    break rowScan;
                                }
                            }
                            h++;
                        }

                        for (int jj = 0; jj < h; jj++) {
                            for (int ii = 0; ii < w; ii++) {
                                mask[i + ii + (j + jj) * S] = 0;
                            }
                        }

                        du[0] = du[1] = du[2] = 0;
                        dv[0] = dv[1] = dv[2] = 0;
                        du[u] = w;
                        dv[v] = h;

                        emitQuad(positions, colors, normals, texCoords, indices, d, u, v, slice, i, j, du, dv, c);

                        i += w;
                    }
                }
                x[d]++;
            }
        }

        if (positions.isEmpty()) {
            return null;
        }
        return toCpuData(positions, colors, normals, texCoords, indices);
    }

    private static void emitQuad(List<Float> p, List<Float> c, List<Float> n, List<Float> t, List<Integer> idx,
            int d, int u, int v, int s, int i, int j, int[] du, int[] dv, int maskVal) {
        byte type = (byte) (maskVal > 0 ? maskVal : -maskVal);
        float nx = 0, ny = 0, nz = 0;
        if (maskVal > 0) {
            if (d == 0) nx = 1;
            else if (d == 1) ny = 1;
            else nz = 1;
        } else {
            if (d == 0) nx = -1;
            else if (d == 1) ny = -1;
            else nz = -1;
        }

        float r = 0.5f, g = 0.5f, b = 0.5f;
        switch (type) {
            case 1 -> { r = 0.5f; g = 0.5f; b = 0.5f; }
            case 2 -> { r = 0.3f; g = 0.7f; b = 0.3f; }
            case 3 -> { r = 0.9f; g = 0.8f; b = 0.5f; }
            case 4 -> { r = 0.5f; g = 0.4f; b = 0.3f; }
            case 5 -> { r = 1.0f; g = 1.0f; b = 1.0f; }
            case 6 -> { r = 0.45f; g = 0.32f; b = 0.2f; }
            case 7 -> { r = 0.22f; g = 0.55f; b = 0.22f; }
            case 8 -> { r = 0.28f; g = 0.62f; b = 0.26f; }
        }

        int tile = tileForFace(type, nx, ny, nz);
        int tx = tile % BlockTextureAtlas.TILES_PER_ROW;
        int ty = tile / BlockTextureAtlas.TILES_PER_ROW;
        float margin = 0.5f / BlockTextureAtlas.ATLAS_SIZE;
        float u0 = tx / (float) BlockTextureAtlas.TILES_PER_ROW + margin;
        float u1 = (tx + 1) / (float) BlockTextureAtlas.TILES_PER_ROW - margin;
        float v0 = ty / (float) BlockTextureAtlas.TILES_PER_ROW + margin;
        float v1 = (ty + 1) / (float) BlockTextureAtlas.TILES_PER_ROW - margin;
        // Atlas uses CLAMP_TO_EDGE: UVs must stay inside this tile. Scaling by merged w×h
        // would sample outside the tile and clamp to black/transparent edges.
        float tu1 = u1;
        float tv1 = v1;

        int[] base = new int[3];
        base[d] = s + 1;
        base[u] = i;
        base[v] = j;

        int baseIdx = p.size() / 3;

        float x0 = base[0], y0 = base[1], z0 = base[2];
        float x1 = base[0] + du[0], y1 = base[1] + du[1], z1 = base[2] + du[2];
        float x2 = base[0] + du[0] + dv[0], y2 = base[1] + du[1] + dv[1], z2 = base[2] + du[2] + dv[2];
        float x3 = base[0] + dv[0], y3 = base[1] + dv[1], z3 = base[2] + dv[2];

        if (maskVal > 0) {
            addVert(p, c, n, t, x0, y0, z0, r, g, b, nx, ny, nz, u0, v0);
            addVert(p, c, n, t, x1, y1, z1, r, g, b, nx, ny, nz, tu1, v0);
            addVert(p, c, n, t, x2, y2, z2, r, g, b, nx, ny, nz, tu1, tv1);
            addVert(p, c, n, t, x3, y3, z3, r, g, b, nx, ny, nz, u0, tv1);
        } else {
            addVert(p, c, n, t, x0, y0, z0, r, g, b, nx, ny, nz, u0, v0);
            addVert(p, c, n, t, x3, y3, z3, r, g, b, nx, ny, nz, u0, tv1);
            addVert(p, c, n, t, x2, y2, z2, r, g, b, nx, ny, nz, tu1, tv1);
            addVert(p, c, n, t, x1, y1, z1, r, g, b, nx, ny, nz, tu1, v0);
        }

        idx.add(baseIdx);
        idx.add(baseIdx + 1);
        idx.add(baseIdx + 2);
        idx.add(baseIdx + 2);
        idx.add(baseIdx + 3);
        idx.add(baseIdx);
    }

    private static void addVert(List<Float> p, List<Float> c, List<Float> n, List<Float> t,
            float px, float py, float pz, float r, float g, float b, float nx, float ny, float nz, float tu, float tv) {
        p.add(px);
        p.add(py);
        p.add(pz);
        c.add(r);
        c.add(g);
        c.add(b);
        n.add(nx);
        n.add(ny);
        n.add(nz);
        t.add(tu);
        t.add(tv);
    }

    private static int tileForFace(byte voxel, float nx, float ny, float nz) {
        return switch (voxel) {
            case 1 -> BlockTextureAtlas.TILE_STONE;
            case 3 -> BlockTextureAtlas.TILE_SAND;
            case 4 -> BlockTextureAtlas.TILE_DIRT;
            case 5 -> BlockTextureAtlas.TILE_SNOW;
            case 2 -> {
                if (ny > 0.5f) yield BlockTextureAtlas.TILE_GRASS_TOP;
                if (ny < -0.5f) yield BlockTextureAtlas.TILE_DIRT;
                yield BlockTextureAtlas.TILE_GRASS_SIDE;
            }
            case 6 -> Math.abs(ny) > 0.5f ? BlockTextureAtlas.TILE_LOG_TOP : BlockTextureAtlas.TILE_LOG_SIDE;
            case 7 -> BlockTextureAtlas.TILE_LEAVES;
            case 8 -> BlockTextureAtlas.TILE_FLORA;
            default -> BlockTextureAtlas.TILE_STONE;
        };
    }

    private static byte get(Chunk c, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= S || y >= S || z >= S) {
            return 0;
        }
        return c.getVoxel(x, y, z);
    }

    private static MeshCpuData toCpuData(List<Float> positions, List<Float> colors, List<Float> normals,
            List<Float> texCoords, List<Integer> indices) {
        float[] pArr = new float[positions.size()];
        for (int i = 0; i < pArr.length; i++) pArr[i] = positions.get(i);
        float[] cArr = new float[colors.size()];
        for (int i = 0; i < cArr.length; i++) cArr[i] = colors.get(i);
        float[] nArr = new float[normals.size()];
        for (int i = 0; i < nArr.length; i++) nArr[i] = normals.get(i);
        float[] tArr = new float[texCoords.size()];
        for (int i = 0; i < tArr.length; i++) tArr[i] = texCoords.get(i);
        int[] idxArr = new int[indices.size()];
        for (int i = 0; i < idxArr.length; i++) idxArr[i] = indices.get(i);
        return new MeshCpuData(pArr, cArr, nArr, tArr, idxArr);
    }
}
