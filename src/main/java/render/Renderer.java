package render;

import math.Camera;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;

public class Renderer {
    private ShaderProgram shaderProgram;
    private BlockTextureAtlas blockAtlas;
    private Matrix4f projectionMatrix, viewMatrix, modelMatrix;
    private final Matrix4f viewProjectionMatrix = new Matrix4f();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private boolean frustumValid;
    private Vector3f lightDir = new Vector3f(-0.5f, -1.0f, -0.3f).normalize();

    public void init() throws Exception {
        blockAtlas = new BlockTextureAtlas();

        shaderProgram = new ShaderProgram();
        shaderProgram.createVertexShader("""
            #version 330 core
            layout (location=0) in vec3 position;
            layout (location=1) in vec3 inColor;
            layout (location=2) in vec3 inNormal;
            layout (location=3) in vec2 inTexCoord;
            out vec3 outColor;
            out vec3 fragNormal;
            out vec2 texCoord;
            uniform mat4 projectionMatrix, viewMatrix, modelMatrix;
            void main() {
                gl_Position = projectionMatrix * viewMatrix * modelMatrix * vec4(position, 1.0);
                outColor = inColor;
                fragNormal = normalize((modelMatrix * vec4(inNormal, 0.0)).xyz);
                texCoord = inTexCoord;
            }
        """);
        shaderProgram.createFragmentShader("""
            #version 330 core
            in vec3 outColor;
            in vec3 fragNormal;
            in vec2 texCoord;
            out vec4 fragColor;
            uniform vec3 lightDirection;
            uniform sampler2D blockAtlas;
            uniform int solidAlbedoMode;
            void main() {
                vec3 texRgb = texture(blockAtlas, texCoord).rgb;
                float useSolid = float(solidAlbedoMode);
                vec3 albedo = mix(texRgb * outColor, outColor, useSolid);
                float diff = max(dot(fragNormal, -lightDirection), 0.0);
                vec3 diffuse = albedo * (diff * 0.7 + 0.3);
                fragColor = vec4(diffuse, 1.0);
            }
        """);
        shaderProgram.link();
        shaderProgram.createUniform("projectionMatrix");
        shaderProgram.createUniform("viewMatrix");
        shaderProgram.createUniform("modelMatrix");
        shaderProgram.createUniform("lightDirection");
        shaderProgram.createUniform("blockAtlas");
        shaderProgram.createUniform("solidAlbedoMode");

        projectionMatrix = new Matrix4f();
        viewMatrix = new Matrix4f();
        modelMatrix = new Matrix4f();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
    }

    /**
     * @param solidAlbedoMode 1 = skip block atlas (flat vertex color), 0 = textured (LOD under threshold).
     */
    public void beginRender(Camera camera, int windowWidth, int windowHeight, int solidAlbedoMode) {
        if (windowWidth <= 0 || windowHeight <= 0) {
            frustumValid = false;
            return;
        }
        frustumValid = true;
        shaderProgram.bind();
        blockAtlas.bind(0);
        shaderProgram.setUniform("blockAtlas", 0);
        shaderProgram.setUniform("solidAlbedoMode", solidAlbedoMode);

        float fov = (float) Math.toRadians(60.0f);
        projectionMatrix.setPerspective(fov, (float) windowWidth / windowHeight, 0.1f, 1000.f);
        viewMatrix.identity();
        viewMatrix.rotateX((float)Math.toRadians(camera.getRotation().x));
        viewMatrix.rotateY((float)Math.toRadians(camera.getRotation().y));
        viewMatrix.rotateZ((float)Math.toRadians(camera.getRotation().z));
        viewMatrix.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);

        viewProjectionMatrix.set(projectionMatrix).mul(viewMatrix);
        frustum.set(viewProjectionMatrix);

        shaderProgram.setUniform("projectionMatrix", projectionMatrix);
        shaderProgram.setUniform("viewMatrix", viewMatrix);
        shaderProgram.setUniform("lightDirection", lightDir);
    }

    /**
     * World-space AABB vs current view frustum (updated in {@link #beginRender}).
     * If the frustum was not computed this frame, returns {@code true} (draw anyway).
     */
    public boolean isAabbInViewFrustum(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        if (!frustumValid) return true;
        return frustum.testAab(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public void renderMesh(Mesh mesh, float x, float y, float z) {
        if (mesh == null) return;
        modelMatrix.identity().translate(x, y, z);
        renderMesh(mesh, modelMatrix);
    }

    public void renderMesh(Mesh mesh, Matrix4f transform) {
        if (mesh == null) return;
        shaderProgram.setUniform("modelMatrix", transform);
        glBindVertexArray(mesh.getVaoId());
        glDrawElements(GL_TRIANGLES, mesh.getVertexCount(), GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void endRender() {
        blockAtlas.unbind();
        shaderProgram.unbind();
    }

    public void cleanup() {
        if (blockAtlas != null) blockAtlas.cleanup();
        if (shaderProgram != null) shaderProgram.cleanup();
    }
}
