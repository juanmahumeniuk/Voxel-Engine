package render;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

public class WeaponOverlay {
    private static final int HUD_W = 420;
    private static final int HUD_H = 100;
    private static final int RETICLE_TEX = 256;

    private final ShaderProgram shader;
    private final int vaoId;
    private final int vboId;
    private final int hudTextureId;
    private final int reticleTextureId;
    private final Matrix4f ortho = new Matrix4f();
    private int lastAmmo = -1;
    private int lastReserve = -1;
    private String lastWeapon = "";
    private float lastAdsProgress = -1f;
    private int lastReloadBucket = -1;

    public WeaponOverlay() throws Exception {
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
                uniform sampler2D overlayTexture;
                void main() {
                    fragColor = texture(overlayTexture, uv);
                }
                """);
        shader.link();
        shader.createUniform("orthoMatrix");
        shader.createUniform("overlayTexture");

        float[] quad = {
                0, 0, 0, 1,
                1, 0, 1, 1,
                1, 1, 1, 0,
                0, 1, 0, 0
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

        hudTextureId = glGenTextures();
        initTexture(hudTextureId, HUD_W, HUD_H);
        reticleTextureId = glGenTextures();
        initTexture(reticleTextureId, RETICLE_TEX, RETICLE_TEX);
        uploadHudTexture("DEAGLE", 7, 56, 0f);
        uploadReticleTexture(0f);
    }

    public void render(String weaponName, int ammo, int reserve, float adsProgress, float reloadProgress,
                       int windowWidth, int windowHeight) {
        if (windowWidth <= 0 || windowHeight <= 0) return;
        int reloadBucket = (int) Math.floor(reloadProgress * 30.0f);
        if (ammo != lastAmmo || reserve != lastReserve || !weaponName.equals(lastWeapon) || reloadBucket != lastReloadBucket) {
            uploadHudTexture(weaponName, ammo, reserve, reloadProgress);
            lastAmmo = ammo;
            lastReserve = reserve;
            lastWeapon = weaponName;
            lastReloadBucket = reloadBucket;
        }
        if (Math.abs(adsProgress - lastAdsProgress) > 0.02f) {
            uploadReticleTexture(adsProgress);
            lastAdsProgress = adsProgress;
        }

        int[] vp = new int[4];
        glGetIntegerv(GL_VIEWPORT, vp);

        boolean depth = glIsEnabled(GL_DEPTH_TEST);
        boolean cull = glIsEnabled(GL_CULL_FACE);
        if (depth) glDisable(GL_DEPTH_TEST);
        if (cull) glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        shader.bind();
        shader.setUniform("overlayTexture", 0);
        glBindVertexArray(vaoId);

        // Weapon + ammo panel
        int panelW = HUD_W;
        int panelH = HUD_H;
        int panelX = windowWidth - panelW - 14;
        int panelY = windowHeight - panelH - 14;
        glViewport(panelX, panelY, panelW, panelH);
        ortho.setOrtho(0, 1, 1, 0, -1, 1);
        shader.setUniform("orthoMatrix", ortho);
        glBindTexture(GL_TEXTURE_2D, hudTextureId);
        glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

        // Crosshair / scope reticle
        int reticleSize = Math.round(74 + adsProgress * 136);
        int reticleX = (windowWidth - reticleSize) / 2;
        int reticleY = (windowHeight - reticleSize) / 2;
        glViewport(reticleX, reticleY, reticleSize, reticleSize);
        glBindTexture(GL_TEXTURE_2D, reticleTextureId);
        glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        shader.unbind();
        glDisable(GL_BLEND);
        if (depth) glEnable(GL_DEPTH_TEST);
        if (cull) glEnable(GL_CULL_FACE);
        glViewport(vp[0], vp[1], vp[2], vp[3]);
    }

    private void initTexture(int textureId, int w, int h) {
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void uploadHudTexture(String weaponName, int ammo, int reserve, float reloadProgress) {
        BufferedImage img = new BufferedImage(HUD_W, HUD_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(7, 10, 14, 185));
            g.fillRoundRect(0, 0, HUD_W, HUD_H, 16, 16);
            g.setColor(new Color(230, 230, 235));
            g.setFont(new Font("Arial", Font.BOLD, 24));
            g.drawString(weaponName, 16, 36);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 34));
            g.drawString(String.format("%02d/%03d", ammo, reserve), 16, 78);
            g.setFont(new Font("Arial", Font.PLAIN, 14));
            g.setColor(new Color(170, 175, 185));
            g.drawString("LMB disparar  RMB mirilla  SHIFT correr  R recargar  1-4 / rueda", 16, 94);
            if (reloadProgress > 0.001f) {
                int barX = HUD_W - 170;
                int barY = 68;
                int barW = 150;
                int barH = 14;
                g.setColor(new Color(30, 34, 42, 220));
                g.fillRoundRect(barX, barY, barW, barH, 6, 6);
                g.setColor(new Color(255, 194, 82, 230));
                g.fillRoundRect(barX + 1, barY + 1, Math.max(1, (int) ((barW - 2) * reloadProgress)), barH - 2, 6, 6);
            }
        } finally {
            g.dispose();
        }
        uploadImageToTexture(img, hudTextureId);
    }

    private void uploadReticleTexture(float adsProgress) {
        BufferedImage img = new BufferedImage(RETICLE_TEX, RETICLE_TEX, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int c = RETICLE_TEX / 2;
            if (adsProgress > 0.01f) {
                int alpha = 120 + (int) (adsProgress * 100);
                g.setColor(new Color(255, 255, 255, alpha));
                g.setStroke(new BasicStroke(3f));
                g.drawOval(24, 24, RETICLE_TEX - 48, RETICLE_TEX - 48);
                g.drawLine(c, 18, c, RETICLE_TEX - 18);
                g.drawLine(18, c, RETICLE_TEX - 18, c);
                g.setColor(new Color(255, 80, 80, 220));
                g.fillOval(c - 4, c - 4, 8, 8);
            }
            int gap = Math.max(2, 14 - (int) (adsProgress * 11));
            int len = Math.max(6, 13 - (int) (adsProgress * 5));
            // Dark outline makes crosshair visible on snow/bright sky.
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(c - 2, c - gap - len - 1, 4, len + 2);
            g.fillRect(c - 2, c + gap - 1, 4, len + 2);
            g.fillRect(c - gap - len - 1, c - 2, len + 2, 4);
            g.fillRect(c + gap - 1, c - 2, len + 2, 4);
            g.setColor(new Color(255, 255, 255, 220));
            g.fillRect(c - 1, c - gap - len, 2, len + 1);
            g.fillRect(c - 1, c + gap, 2, len + 1);
            g.fillRect(c - gap - len, c - 1, len + 1, 2);
            g.fillRect(c + gap, c - 1, len + 1, 2);
            g.setColor(new Color(255, 80, 80, 230));
            g.fillOval(c - 2, c - 2, 4, 4);
        } finally {
            g.dispose();
        }
        uploadImageToTexture(img, reticleTextureId);
    }

    private void uploadImageToTexture(BufferedImage img, int textureId) {
        int w = img.getWidth();
        int h = img.getHeight();
        ByteBuffer rgba = BufferUtils.createByteBuffer(w * h * 4);
        for (int y = 0; y < h; y++) {
            int glRow = h - 1 - y;
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 0xff;
                int r = (argb >> 16) & 0xff;
                int gc = (argb >> 8) & 0xff;
                int b = argb & 0xff;
                int i = (glRow * w + x) * 4;
                rgba.put(i, (byte) r);
                rgba.put(i + 1, (byte) gc);
                rgba.put(i + 2, (byte) b);
                rgba.put(i + 3, (byte) a);
            }
        }
        rgba.clear();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void cleanup() {
        shader.cleanup();
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
        glDeleteTextures(hudTextureId);
        glDeleteTextures(reticleTextureId);
    }
}
