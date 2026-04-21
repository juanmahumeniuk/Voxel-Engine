package render;

import math.Camera;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

public class Renderer {
    private ShaderProgram shaderProgram;
    private Matrix4f projectionMatrix, viewMatrix, modelMatrix;
    private Vector3f lightDir = new Vector3f(-0.5f, -1.0f, -0.3f).normalize();

    public void init() throws Exception {
        shaderProgram = new ShaderProgram();
        shaderProgram.createVertexShader("""
            #version 330 core
            layout (location=0) in vec3 position;
            layout (location=1) in vec3 inColor;
            layout (location=2) in vec3 inNormal;
            out vec3 outColor;
            out vec3 fragNormal;
            uniform mat4 projectionMatrix, viewMatrix, modelMatrix;
            void main() {
                gl_Position = projectionMatrix * viewMatrix * modelMatrix * vec4(position, 1.0);
                outColor = inColor;
                fragNormal = normalize((modelMatrix * vec4(inNormal, 0.0)).xyz);
            }
        """);
        shaderProgram.createFragmentShader("""
            #version 330 core
            in vec3 outColor;
            in vec3 fragNormal;
            out vec4 fragColor;
            uniform vec3 lightDirection;
            void main() {
                float diff = max(dot(fragNormal, -lightDirection), 0.0);
                vec3 diffuse = outColor * (diff * 0.7 + 0.3); // Mix ambient and diffuse
                fragColor = vec4(diffuse, 1.0);
            }
        """);
        shaderProgram.link();
        shaderProgram.createUniform("projectionMatrix");
        shaderProgram.createUniform("viewMatrix");
        shaderProgram.createUniform("modelMatrix");
        shaderProgram.createUniform("lightDirection");

        projectionMatrix = new Matrix4f();
        viewMatrix = new Matrix4f();
        modelMatrix = new Matrix4f();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
    }

    public void beginRender(Camera camera, int windowWidth, int windowHeight) {
        if (windowWidth <= 0 || windowHeight <= 0) return;
        shaderProgram.bind();
        float fov = (float) Math.toRadians(60.0f);
        projectionMatrix.setPerspective(fov, (float) windowWidth / windowHeight, 0.1f, 1000.f);
        viewMatrix.identity();
        viewMatrix.rotateX((float)Math.toRadians(camera.getRotation().x));
        viewMatrix.rotateY((float)Math.toRadians(camera.getRotation().y));
        viewMatrix.rotateZ((float)Math.toRadians(camera.getRotation().z));
        viewMatrix.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);

        shaderProgram.setUniform("projectionMatrix", projectionMatrix);
        shaderProgram.setUniform("viewMatrix", viewMatrix);
        shaderProgram.setUniform("lightDirection", lightDir);
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

    public void endRender() { shaderProgram.unbind(); }
    public void cleanup() { if (shaderProgram != null) shaderProgram.cleanup(); }
}
