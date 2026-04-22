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
 * Main menu overlay shown before entering the world.
 */
public class MainMenu {
    public static final int ACTION_NEW_WORLD = 0;
    public static final int ACTION_CONTINUE_WORLD = 1;
    public static final int ACTION_OPTIONS = 2;

    private static final int TEX_W = 960;
    private static final int TEX_H = 540;

    private final ShaderProgram shader;
    private final int vaoId;
    private final int vboId;
    private final int textureId;
    private final Matrix4f ortho = new Matrix4f();
    private final String[] options = {"Iniciar mundo nuevo", "Continuar mundo anterior", "Opciones"};
    private int selected = 0;
    private boolean canContinue = false;
    private boolean vSyncEnabled = false;
    private String statusMessage = "Usa flechas para navegar y ENTER para elegir.";

    public MainMenu() throws Exception {
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
                uniform sampler2D menuTexture;
                void main() {
                    fragColor = texture(menuTexture, uv);
                }
                """);
        shader.link();
        shader.createUniform("orthoMatrix");
        shader.createUniform("menuTexture");

        // VAO and VBO
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        float[] vertices = {
            // positions        // texture coords
            -1.0f,  1.0f, 0.0f, 1.0f, // top left
            -1.0f, -1.0f, 0.0f, 0.0f, // bottom left
             1.0f, -1.0f, 1.0f, 0.0f, // bottom right
             1.0f,  1.0f, 1.0f, 1.0f  // top right
        };
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        // Texture
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        updateTexture();
    }

    private void updateTexture() {
        BufferedImage image = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setFont(new Font("Arial", Font.BOLD, 30));
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, TEX_W, TEX_H);

        g2d.setColor(new Color(22, 26, 36));
        g2d.fillRoundRect(30, 30, TEX_W - 60, TEX_H - 60, 20, 20);

        g2d.setColor(Color.WHITE);
        g2d.drawString("Voxel Engine", 56, 90);
        g2d.setFont(new Font("Arial", Font.PLAIN, 18));
        g2d.setColor(new Color(180, 190, 210));
        g2d.drawString("Menu de inicio", 58, 120);

        g2d.setFont(new Font("Arial", Font.BOLD, 22));
        int baseY = 200;
        for (int i = 0; i < options.length; i++) {
            String label = options[i];
            if (i == ACTION_OPTIONS) {
                label = "Opciones - VSync: " + (vSyncEnabled ? "ON" : "OFF");
            }
            if (i == ACTION_CONTINUE_WORLD && !canContinue) {
                label += " (sin guardado)";
            }
            if (i == selected) {
                g2d.setColor(new Color(255, 212, 90));
                g2d.fillRoundRect(70, baseY - 30 + i * 64, TEX_W - 140, 44, 10, 10);
                g2d.setColor(Color.BLACK);
            } else if (i == ACTION_CONTINUE_WORLD && !canContinue) {
                g2d.setColor(new Color(128, 128, 128));
            } else {
                g2d.setColor(Color.WHITE);
            }
            g2d.drawString(label, 88, baseY + i * 64);
        }

        g2d.setFont(new Font("Arial", Font.PLAIN, 16));
        g2d.setColor(new Color(180, 190, 210));
        g2d.drawString(statusMessage, 56, TEX_H - 74);
        g2d.drawString("ESC para salir", 56, TEX_H - 46);

        g2d.dispose();

        int[] pixels = new int[TEX_W * TEX_H];
        image.getRGB(0, 0, TEX_W, TEX_H, pixels, 0, TEX_W);

        ByteBuffer buffer = BufferUtils.createByteBuffer(TEX_W * TEX_H * 4);
        for (int y = 0; y < TEX_H; y++) {
            int glRow = TEX_H - 1 - y;
            for (int x = 0; x < TEX_W; x++) {
                int pixel = pixels[y * TEX_W + x];
                int i = (glRow * TEX_W + x) * 4;
                buffer.put(i, (byte) ((pixel >> 16) & 0xFF));     // Red
                buffer.put(i + 1, (byte) ((pixel >> 8) & 0xFF));  // Green
                buffer.put(i + 2, (byte) (pixel & 0xFF));         // Blue
                buffer.put(i + 3, (byte) ((pixel >> 24) & 0xFF)); // Alpha
            }
        }
        buffer.clear();

        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, TEX_W, TEX_H, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
    }

    public void render(int windowWidth, int windowHeight) {
        if (windowWidth <= 0 || windowHeight <= 0) return;

        int[] vp = new int[4];
        glGetIntegerv(GL_VIEWPORT, vp);

        int maxWidth = Math.max(1, windowWidth - 80);
        int targetWidth = Math.max(640, (int) (windowWidth * 0.60f));
        targetWidth = Math.min(targetWidth, maxWidth);
        int targetHeight = Math.max(1, Math.round(targetWidth * (TEX_H / (float) TEX_W)));

        int maxHeight = Math.max(1, windowHeight - 80);
        if (targetHeight > maxHeight) {
            targetHeight = maxHeight;
            targetWidth = Math.max(1, Math.round(targetHeight * (TEX_W / (float) TEX_H)));
        }

        int viewportX = (windowWidth - targetWidth) / 2;
        int viewportY = (windowHeight - targetHeight) / 2;
        glViewport(viewportX, viewportY, targetWidth, targetHeight);

        ortho.setOrtho2D(-1, 1, -1, 1);
        shader.bind();
        shader.setUniform("orthoMatrix", ortho);
        shader.setUniform("menuTexture", 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);

        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
        shader.unbind();

        glViewport(vp[0], vp[1], vp[2], vp[3]);
    }

    public void selectNext() {
        selected = (selected + 1) % options.length;
        updateTexture();
    }

    public void selectPrev() {
        selected = (selected - 1 + options.length) % options.length;
        updateTexture();
    }

    public int getSelectedAction() {
        return selected;
    }

    public void setCanContinue(boolean canContinue) {
        this.canContinue = canContinue;
        updateTexture();
    }

    public void setVSyncEnabled(boolean vSyncEnabled) {
        this.vSyncEnabled = vSyncEnabled;
        updateTexture();
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
        updateTexture();
    }

    public void cleanup() {
        glDeleteTextures(textureId);
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
        shader.cleanup();
    }
}