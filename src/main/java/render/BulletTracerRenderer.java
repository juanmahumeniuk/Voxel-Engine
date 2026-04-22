package render;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BulletTracerRenderer {
    private static final float TRACE_DURATION = 0.09f;
    private static final float TRACE_SIZE = 0.26f;

    private static final class Trace {
        float sx, sy, sz;
        float ex, ey, ez;
        float age;
    }

    private final Mesh tracerMesh;
    private final List<Trace> traces = new ArrayList<>();
    private final Matrix4f transform = new Matrix4f();

    public BulletTracerRenderer() {
        tracerMesh = BoxMeshBuilder.createBox(TRACE_SIZE, TRACE_SIZE, TRACE_SIZE, 1.0f, 0.86f, 0.26f);
    }

    public void spawnTrace(float sx, float sy, float sz, float ex, float ey, float ez) {
        Trace t = new Trace();
        t.sx = sx;
        t.sy = sy;
        t.sz = sz;
        t.ex = ex;
        t.ey = ey;
        t.ez = ez;
        traces.add(t);
    }

    public void update(float dt) {
        Iterator<Trace> it = traces.iterator();
        while (it.hasNext()) {
            Trace t = it.next();
            t.age += dt;
            if (t.age >= TRACE_DURATION) it.remove();
        }
    }

    public void render(Renderer renderer) {
        for (Trace t : traces) {
            float u = t.age / TRACE_DURATION;
            float x = t.sx + (t.ex - t.sx) * u;
            float y = t.sy + (t.ey - t.sy) * u;
            float z = t.sz + (t.ez - t.sz) * u;
            transform.identity().translate(x, y, z);
            renderer.renderMesh(tracerMesh, transform);
        }
    }

    public void cleanup() {
        tracerMesh.cleanup();
    }
}
