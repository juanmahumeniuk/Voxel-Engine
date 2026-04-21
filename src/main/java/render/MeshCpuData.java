package render;

/**
 * Vertex data built on any thread; upload to GPU with {@link Mesh#Mesh(MeshCpuData)} on the OpenGL thread only.
 */
public record MeshCpuData(float[] positions, float[] colors, float[] normals, float[] texCoords, int[] indices) {}
