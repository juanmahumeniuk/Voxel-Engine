package world;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.*;

public class WorldPersistence {
    private static final String SAVE_FILE = "world_save/region_0_0.vxl";
    private static final int SECTOR_SIZE = 4096;
    private static final int REGION_SIZE = 64; // Supports 64x64 chunks (4096 entries)
    private static RandomAccessFile file;
    private static int[] offsetTable = new int[REGION_SIZE * REGION_SIZE];

    static {
        try {
            File dir = new File("world_save/");
            if (!dir.exists()) dir.mkdirs();
            
            file = new RandomAccessFile(SAVE_FILE, "rw");
            if (file.length() < SECTOR_SIZE * 4) { // Fill header if new file
                file.setLength(SECTOR_SIZE * 4);
                file.seek(0);
                for (int i = 0; i < offsetTable.length; i++) file.writeInt(0);
            }
            
            file.seek(0);
            for (int i = 0; i < offsetTable.length; i++) {
                offsetTable[i] = file.readInt();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized byte[] loadChunk(int cx, int cz) {
        int index = getIndex(cx, cz);
        if (index < 0 || index >= offsetTable.length) return null;
        
        int sectorOffset = offsetTable[index];
        if (sectorOffset == 0) return null;

        try {
            file.seek((long) sectorOffset * SECTOR_SIZE);
            int length = file.readInt();
            byte version = file.readByte(); // Version 1 = Zlib
            
            byte[] compressed = new byte[length - 1];
            file.readFully(compressed);
            
            return decompress(compressed);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static synchronized void saveChunk(int cx, int cz, byte[] data) {
        int index = getIndex(cx, cz);
        if (index < 0 || index >= offsetTable.length) return;

        try {
            byte[] compressed = compress(data);
            int sectorsNeeded = (compressed.length + 5 + SECTOR_SIZE - 1) / SECTOR_SIZE;
            
            // For simplicity, always append to end of file and update pointer 
            // (A true Anvil implementation would reuse sectors, but here the complexity is limited)
            int sectorOffset = (int) (file.length() / SECTOR_SIZE);
            
            file.seek((long) sectorOffset * SECTOR_SIZE);
            file.writeInt(compressed.length + 1);
            file.writeByte(1); // Zlib
            file.write(compressed);
            
            // Pad to sector boundary
            long currentPos = file.getFilePointer();
            long padding = (long) sectorsNeeded * SECTOR_SIZE - (compressed.length + 5);
            if (padding > 0) file.write(new byte[(int)padding]);

            // Update header
            offsetTable[index] = sectorOffset;
            file.seek((long) index * 4);
            file.writeInt(sectorOffset);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int getIndex(int cx, int cz) {
        // Map global chunk coords to 0..63 region local coords
        int rx = Math.floorMod(cx, REGION_SIZE);
        int rz = Math.floorMod(cz, REGION_SIZE);
        return rx + rz * REGION_SIZE;
    }

    private static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        DeflaterOutputStream dos = new DeflaterOutputStream(bos);
        dos.write(data);
        dos.close();
        return bos.toByteArray();
    }

    private static byte[] decompress(byte[] compressed) throws Exception {
        ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
        InflaterInputStream iis = new InflaterInputStream(bis);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = iis.read(buffer)) > 0) bos.write(buffer, 0, len);
        return bos.toByteArray();
    }

    public static void close() {
        try { if (file != null) file.close(); } catch (IOException e) {}
    }
}
