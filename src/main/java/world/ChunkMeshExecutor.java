package world;

import render.Mesh;
import render.MeshCpuData;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Builds {@link ChunkMesher} geometry on worker threads; GPU upload happens on the main thread
 * (OpenGL context is thread-local — same model as Minecraft Java mesh workers + main-thread upload).
 */
public final class ChunkMeshExecutor implements AutoCloseable {

    /** Avoid uploading many VAOs in one frame (main-thread hitch). */
    private static final int MAX_MESH_UPLOADS_PER_DRAIN = 10;

    public record CompletedMesh(int cx, int cz, MeshCpuData data) {}

    private final ExecutorService pool;
    private final ConcurrentLinkedQueue<CompletedMesh> completed = new ConcurrentLinkedQueue<>();

    public ChunkMeshExecutor() {
        int threads = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "chunk-mesh");
            t.setDaemon(true);
            return t;
        });
    }

    public void submit(int cx, int cz, Chunk chunk) {
        pool.submit(() -> {
            try {
                MeshCpuData data = ChunkMesher.buildMeshCpuData(chunk);
                completed.offer(new CompletedMesh(cx, cz, data));
            } catch (Exception e) {
                e.printStackTrace();
                completed.offer(new CompletedMesh(cx, cz, null));
            }
        });
    }

    public void drainToWorld(World world) {
        int uploads = 0;
        int polled = 0;
        while (uploads < MAX_MESH_UPLOADS_PER_DRAIN && polled < 128) {
            polled++;
            CompletedMesh job = completed.poll();
            if (job == null) {
                break;
            }
            if (job.data == null) {
                continue;
            }
            Mesh mesh = new Mesh(job.data);
            uploads++;
            if (!world.assignChunkMeshIfPending(job.cx, job.cz, mesh)) {
                mesh.cleanup();
            }
        }
    }

    @Override
    public void close() {
        pool.shutdown();
        try {
            pool.awaitTermination(4, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
        while (completed.poll() != null) {
            // MeshCpuData has no GL resources to free
        }
    }
}
