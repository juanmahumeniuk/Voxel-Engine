package world;

public class Chunk {
    public static final int SIZE = 64; // 64x64x64 voxels internally
    private byte[] voxels;

    public Chunk() {
        voxels = new byte[SIZE * SIZE * SIZE];
    }

    public byte getVoxel(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) return 0;
        return voxels[getIndex(x, y, z)];
    }

    public void setVoxel(int x, int y, int z, byte type) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) return;
        voxels[getIndex(x, y, z)] = type;
    }

    private int getIndex(int x, int y, int z) {
        // Z is the highest stride, then Y, then X to optimize sequential reads internally
        return x + (y * SIZE) + (z * SIZE * SIZE);
    }

    public byte[] getData() {
        return voxels;
    }

    public void setData(byte[] data) {
        if (data.length == voxels.length) {
            System.arraycopy(data, 0, voxels, 0, data.length);
        }
    }
}
