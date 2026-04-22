package render;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Draws an FPS label in the top-left using a small dynamic RGBA texture.
 */
public class FpsOverlay {
    private static final int TEX_W = 320;
    private static final int TEX_H = 48;

    private final ShaderProgram shader;
    private final int vaoId;
    private final int vboId;
    private final int textureId;
    private final Matrix4f ortho = new Matrix4f();
    private int lastUploadedFps = -1;

    public FpsOverlay() throws Exception {
        shader = new ShaderProgram();
        shader.createVertexShader("""
                #version 330 core
                layout (location = 0) in vec2 position;
                layout (location = 1) in vec2 texCoord;
                uniform mat4 orthoMatrix;
                out vec2 uv;
                void main() {
                    gl_Position = orthoMatrix * vec4(position, 0.0, 1.0);
                    uv = texCoord;
                }
                """);
        shader.createFragmentShader("""
                #version 330 core
                in vec2 uv;
                out vec4 fragColor;
                uniform sampler2D labelTexture;
                void main() {
                    fragColor = texture(labelTexture, uv);
                }
                """);
        shader.link();
        shader.createUniform("orthoMatrix");
        shader.createUniform("labelTexture");

        float[] quad = {
                0, 0, 0, 1,
                TEX_W, 0, 1, 1,
                TEX_W, TEX_H, 1, 0,
                0, TEX_H, 0, 0,
        };
        ByteBuffer buf = BufferUtils.createByteBuffer(quad.length * Float.BYTES);
        buf.asFloatBuffer().put(quad).flip();

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glBindVertexArray(0);

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        ByteBuffer empty = BufferUtils.createByteBuffer(TEX_W * TEX_H * 4);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, TEX_W, TEX_H, 0, GL_RGBA, GL_UNSIGNED_BYTE, empty);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void render(int fps, int windowWidth, int windowHeight) {
        if (windowWidth <= 0 || windowHeight <= 0) return;

        if (fps != lastUploadedFps) {
            lastUploadedFps = fps;
            uploadLabelTexture(fps);
        }

        int[] vp = new int[4];
        glGetIntegerv(GL_VIEWPORT, vp);

        int margin = 8;
        glViewport(margin, windowHeight - TEX_H - margin, TEX_W, TEX_H);

        ortho.setOrtho(0, TEX_W, TEX_H, 0, -1f, 1f);

        boolean depth = glIsEnabled(GL_DEPTH_TEST);
        boolean cull = glIsEnabled(GL_CULL_FACE);
        if (depth) glDisable(GL_DEPTH_TEST);
        if (cull) glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        shader.bind();
        shader.setUniform("orthoMatrix", ortho);
        shader.setUniform("labelTexture", 0);
        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);

        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
        glBindVertexArray(0);

        glBindTexture(GL_TEXTURE_2D, 0);
        shader.unbind();

        glDisable(GL_BLEND);
        if (depth) glEnable(GL_DEPTH_TEST);
        if (cull) glEnable(GL_CULL_FACE);

        glViewport(vp[0], vp[1], vp[2], vp[3]);
    }

    private void uploadLabelTexture(int fps) {
        BufferedImage img = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRoundRect(4, 4, TEX_W - 8, TEX_H - 8, 8, 8);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
            g.drawString("FPS: " + fps, 14, 32);
        } finally {
            g.dispose();
        }

        ByteBuffer rgba = BufferUtils.createByteBuffer(TEX_W * TEX_H * 4);
        for (int y = 0; y < TEX_H; y++) {
            int glRow = TEX_H - 1 - y;
            for (int x = 0; x < TEX_W; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 0xff;
                int r = (argb >> 16) & 0xff;
                int gc = (argb >> 8) & 0xff;
                int b = argb & 0xff;
                int i = (glRow * TEX_W + x) * 4;
                rgba.put(i, (byte) r);
                rgba.put(i + 1, (byte) gc);
                rgba.put(i + 2, (byte) b);
                rgba.put(i + 3, (byte) a);
            }
        }
        rgba.clear();

        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, TEX_W, TEX_H, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void cleanup() {
        shader.cleanup();
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
        glDeleteTextures(textureId);
    }
}
