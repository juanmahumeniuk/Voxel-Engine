package render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Small procedural 8×8 tile atlas (256×256 RGBA) for block faces.
 * Tile indices are linear: {@code index = tileX + tileY * 8} with origin at bottom-left of the texture.
 */
public class BlockTextureAtlas {
    public static final int TILES_PER_ROW = 8;
    public static final int ATLAS_SIZE = 256;
    public static final int TILE_PIXELS = ATLAS_SIZE / TILES_PER_ROW;

    public static final int TILE_STONE = 0;
    public static final int TILE_DIRT = 1;
    public static final int TILE_SAND = 2;
    public static final int TILE_SNOW = 3;
    public static final int TILE_GRASS_TOP = 4;
    public static final int TILE_GRASS_SIDE = 5;
    /** Solid white — multiply with vertex color for untextured props / player. */
    public static final int TILE_NEUTRAL = 7 + 7 * TILES_PER_ROW;

    private final int textureId;

    public BlockTextureAtlas() {
        ByteBuffer data = BufferUtils.createByteBuffer(ATLAS_SIZE * ATLAS_SIZE * 4);
        for (int i = 0; i < ATLAS_SIZE * ATLAS_SIZE * 4; i++) {
            data.put((byte) 0);
        }
        data.flip();

        fillTile(data, 0, 0, 120, 120, 128, 32); // stone
        fillTile(data, 1, 0, 110, 75, 45, 18); // dirt
        fillTile(data, 2, 0, 210, 190, 120, 28); // sand
        fillTile(data, 3, 0, 235, 245, 255, 12); // snow
        fillTile(data, 4, 0, 55, 140, 55, 22); // grass top
        fillGrassSideTile(data, 5, 0);
        fillTile(data, 7, 7, 255, 255, 255, 0); // neutral / white

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, ATLAS_SIZE, ATLAS_SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private static void putPixel(ByteBuffer buf, int gx, int gy, int r, int g, int b, int a) {
        int idx = (gy * ATLAS_SIZE + gx) * 4;
        buf.put(idx, (byte) r);
        buf.put(idx + 1, (byte) g);
        buf.put(idx + 2, (byte) b);
        buf.put(idx + 3, (byte) a);
    }

    /** Tile grid coordinates: (0,0) = bottom-left tile in OpenGL image space. */
    private static void fillTile(ByteBuffer buf, int tileX, int tileY, int br, int bg, int bb, int variation) {
        for (int ly = 0; ly < TILE_PIXELS; ly++) {
            for (int lx = 0; lx < TILE_PIXELS; lx++) {
                int gx = tileX * TILE_PIXELS + lx;
                int gy = tileY * TILE_PIXELS + ly;
                int m = (lx * 13 + ly * 7) % (variation + 1);
                int r = clamp(br + m - variation / 2);
                int g = clamp(bg + m - variation / 2);
                int bcol = clamp(bb + m - variation / 2);
                boolean checker = ((lx / 4) + (ly / 4)) % 2 == 0;
                if (checker) {
                    r = clamp(r + 10);
                    g = clamp(g + 10);
                    bcol = clamp(bcol + 10);
                }
                putPixel(buf, gx, gy, r, g, bcol, 255);
            }
        }
    }

    private static void fillGrassSideTile(ByteBuffer buf, int tileX, int tileY) {
        for (int ly = 0; ly < TILE_PIXELS; ly++) {
            for (int lx = 0; lx < TILE_PIXELS; lx++) {
                int gx = tileX * TILE_PIXELS + lx;
                int gy = tileY * TILE_PIXELS + ly;
                float t = ly / (float) (TILE_PIXELS - 1);
                int br = (int) lerp(90, 50, t);
                int bg = (int) lerp(130, 90, t);
                int bb = (int) lerp(45, 35, t);
                int m = (lx * 11) % 14;
                putPixel(buf, gx, gy, clamp(br + m - 7), clamp(bg + m - 7), clamp(bb + m - 5), 255);
            }
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Appends four (u,v) pairs matching the quad vertex order used by {@link world.ChunkMesher}. */
    public static void addTileTexCoords(List<Float> tex, int tileIndex) {
        int tx = tileIndex % TILES_PER_ROW;
        int ty = tileIndex / TILES_PER_ROW;
        float margin = 0.5f / ATLAS_SIZE;
        float u0 = tx / (float) TILES_PER_ROW + margin;
        float u1 = (tx + 1) / (float) TILES_PER_ROW - margin;
        float v0 = ty / (float) TILES_PER_ROW + margin;
        float v1 = (ty + 1) / (float) TILES_PER_ROW - margin;
        tex.add(u0);
        tex.add(v0);
        tex.add(u1);
        tex.add(v0);
        tex.add(u1);
        tex.add(v1);
        tex.add(u0);
        tex.add(v1);
    }

    public void bind(int unit) {
        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void cleanup() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
        }
    }
}
