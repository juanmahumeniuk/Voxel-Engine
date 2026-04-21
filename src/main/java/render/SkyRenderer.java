package render;

import math.Camera;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Procedural sky: gradient, soft clouds, sun disc, and subtle volumetric-style god rays (shader-only).
 * Drawn first with depth unchanged so terrain occludes.
 */
public class SkyRenderer {
    /** Same as {@link Renderer} light vector; sun lies opposite (toward sky). */
    private static final Vector3f LIGHT_TOWARD_SURFACE = new Vector3f(-0.5f, -1.0f, -0.3f).normalize();
    private static final Vector3f SUN_DIR_WORLD = new Vector3f(LIGHT_TOWARD_SURFACE).negate().normalize();

    private ShaderProgram shader;
    private int vaoId;
    private int vboId;
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f viewRot = new Matrix4f();
    private final Matrix3f worldFromView = new Matrix3f();

    public void init() throws Exception {
        float[] cube = {
                -1, 1, -1, -1, -1, -1, 1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1,
                -1, -1, 1, -1, -1, -1, -1, 1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1,
                1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, 1, 1, 1, -1, 1, -1, -1,
                -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, 1, 1, 1, -1, 1, -1, -1, 1,
                -1, 1, -1, 1, 1, -1, 1, 1, 1, 1, 1, 1, -1, 1, 1, -1, 1, -1,
                -1, -1, -1, -1, -1, 1, 1, -1, 1, 1, -1, 1, 1, -1, -1, -1, -1, -1,
        };

        shader = new ShaderProgram();
        shader.createVertexShader("""
                #version 330 core
                layout (location = 0) in vec3 aPos;
                uniform mat4 uProjection;
                uniform mat4 uViewRot;
                out vec3 vRayVS;
                void main() {
                    vec4 clip = uProjection * uViewRot * vec4(aPos, 1.0);
                    gl_Position = clip.xyww;
                    vRayVS = normalize(aPos);
                }
                """);
        shader.createFragmentShader("""
                #version 330 core
                in vec3 vRayVS;
                out vec4 fragColor;
                uniform mat3 uWorldFromView;
                uniform vec3 uSunDir;
                uniform float uTime;

                float hash21(vec2 p) {
                    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
                }
                float valueNoise2d(vec2 p) {
                    vec2 i = floor(p);
                    vec2 f = fract(p);
                    float a = hash21(i);
                    float b = hash21(i + vec2(1, 0));
                    float c = hash21(i + vec2(0, 1));
                    float d = hash21(i + vec2(1, 1));
                    vec2 u = f * f * (3.0 - 2.0 * f);
                    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
                }
                float skyFbm(vec2 p) {
                    float v = 0.0, a = 0.55;
                    mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
                    for (int i = 0; i < 3; i++) {
                        v += a * valueNoise2d(p);
                        p = m * p;
                        a *= 0.5;
                    }
                    return v;
                }

                void main() {
                    vec3 dirW = normalize(uWorldFromView * vRayVS);
                    vec3 sun = normalize(uSunDir);
                    float y = dirW.y;
                    float sunH = sun.y;

                    float zenith = smoothstep(-0.15, 0.85, y);
                    vec3 topCol = vec3(0.12, 0.28, 0.62);
                    vec3 horCol = vec3(0.45, 0.72, 0.95);
                    vec3 sky = mix(horCol, topCol, zenith);

                    float night = smoothstep(0.12, -0.25, sunH);
                    sky = mix(sky, sky * vec3(0.08, 0.1, 0.22), night * 0.85);

                    vec2 cloudUv = vec2(atan(dirW.z, dirW.x), asin(clamp(y, -1.0, 1.0))) * vec2(1.2, 2.5);
                    cloudUv += vec2(uTime * 0.012, uTime * 0.003);
                    float dens = skyFbm(cloudUv);
                    dens = smoothstep(0.45, 0.82, dens) * smoothstep(-0.05, 0.35, y);
                    vec3 cloudCol = vec3(1.0, 1.0, 1.0);
                    sky = mix(sky, cloudCol, dens * 0.55);

                    float mu = max(dot(dirW, sun), 0.0);
                    float disk = pow(mu, 3200.0) * 4.2;
                    float corona = pow(mu, 120.0) * 0.35;
                    vec3 sunCol = vec3(1.0, 0.96, 0.78);
                    vec3 bloom = sunCol * (disk + corona);

                    vec3 up = vec3(0.0, 1.0, 0.0);
                    vec3 perpU = normalize(cross(sun, abs(sun.y) < 0.92 ? up : vec3(1.0, 0.0, 0.0)));
                    vec3 perpV = cross(sun, perpU);
                    float ang = atan(dot(dirW, perpV), dot(dirW, perpU));
                    float shaft = pow(max(0.0, sin(ang * 14.0 + uTime * 0.25)), 5.0);
                    shaft += pow(max(0.0, sin(ang * 9.0 - uTime * 0.18)), 4.0) * 0.6;
                    float nearSun = smoothstep(0.88, 0.9995, mu);
                    float below = smoothstep(-0.08, 0.12, sunH);
                    float godRays = shaft * nearSun * (0.06 + 0.05 * below) * (1.0 - night * 0.7);
                    vec3 rays = sunCol * godRays;

                    vec3 col = sky + bloom + rays;
                    fragColor = vec4(col, 1.0);
                }
                """);
        shader.link();
        shader.createUniform("uProjection");
        shader.createUniform("uViewRot");
        shader.createUniform("uWorldFromView");
        shader.createUniform("uSunDir");
        shader.createUniform("uTime");

        FloatBuffer buf = MemoryUtil.memAllocFloat(cube.length);
        buf.put(cube).flip();
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glBindVertexArray(0);
        MemoryUtil.memFree(buf);
    }

    public void render(Camera camera, int windowWidth, int windowHeight, float timeSeconds) {
        if (windowWidth <= 0 || windowHeight <= 0) return;

        float fov = (float) Math.toRadians(60.0f);
        projection.identity().setPerspective(fov, (float) windowWidth / windowHeight, 0.1f, 1000.f);

        viewRot.identity();
        viewRot.rotateX((float) Math.toRadians(camera.getRotation().x));
        viewRot.rotateY((float) Math.toRadians(camera.getRotation().y));
        viewRot.rotateZ((float) Math.toRadians(camera.getRotation().z));

        worldFromView.set(viewRot).transpose();

        boolean cull = glIsEnabled(GL_CULL_FACE);
        if (cull) glDisable(GL_CULL_FACE);
        glDepthMask(false);
        glDepthFunc(GL_LEQUAL);

        shader.bind();
        shader.setUniform("uProjection", projection);
        shader.setUniform("uViewRot", viewRot);
        shader.setUniform("uWorldFromView", worldFromView);
        shader.setUniform("uSunDir", SUN_DIR_WORLD);
        shader.setUniform("uTime", timeSeconds);

        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, 36);
        glBindVertexArray(0);
        shader.unbind();

        glDepthMask(true);
        glDepthFunc(GL_LESS);
        if (cull) glEnable(GL_CULL_FACE);
    }

    public void cleanup() {
        if (shader != null) shader.cleanup();
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
