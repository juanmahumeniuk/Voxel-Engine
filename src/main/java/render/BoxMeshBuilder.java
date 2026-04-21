package render;

import java.util.ArrayList;
import java.util.List;

public class BoxMeshBuilder {
    public static Mesh createBox(float width, float height, float depth, float r, float g, float b) {
        List<Float> positions = new ArrayList<>();
        List<Float> colors = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        float hw = width / 2f;
        float h = height; 
        float hd = depth / 2f;
        
        // Face definitions with correct outward normals
        addFace(positions, colors, normals, indices, new float[]{-hw, -h/2, hd,  hw, -h/2, hd,  hw, h/2, hd,  -hw, h/2, hd}, r, g, b, 0, 0, 1);
        addFace(positions, colors, normals, indices, new float[]{hw, -h/2, -hd,  -hw, -h/2, -hd,  -hw, h/2, -hd,  hw, h/2, -hd}, r, g, b, 0, 0, -1);
        addFace(positions, colors, normals, indices, new float[]{-hw, h/2, hd,  hw, h/2, hd,  hw, h/2, -hd,  -hw, h/2, -hd}, r, g, b, 0, 1, 0);
        addFace(positions, colors, normals, indices, new float[]{-hw, -h/2, -hd,  hw, -h/2, -hd,  hw, -h/2, hd,  -hw, -h/2, hd}, r, g, b, 0, -1, 0);
        addFace(positions, colors, normals, indices, new float[]{hw, -h/2, hd,  hw, -h/2, -hd,  hw, h/2, -hd,  hw, h/2, hd}, r, g, b, 1, 0, 0);
        addFace(positions, colors, normals, indices, new float[]{-hw, -h/2, -hd,  -hw, -h/2, hd,  -hw, h/2, hd,  -hw, h/2, -hd}, r, g, b, -1, 0, 0);

        float[] posArr = new float[positions.size()]; for(int i=0;i<positions.size();i++) posArr[i]=positions.get(i);
        float[] colorArr = new float[colors.size()]; for(int i=0;i<colors.size();i++) colorArr[i]=colors.get(i);
        float[] normArr = new float[normals.size()]; for(int i=0;i<normals.size();i++) normArr[i]=normals.get(i);
        int[] idxArr = new int[indices.size()]; for(int i=0;i<indices.size();i++) idxArr[i]=indices.get(i);
        
        return new Mesh(posArr, colorArr, normArr, idxArr);
    }

    private static void addFace(List<Float> positions, List<Float> colors, List<Float> normals, List<Integer> indices, 
            float[] vOffsets, float r, float g, float b, float nx, float ny, float nz) {
        int index = positions.size() / 3;
        for (int i = 0; i < 4; i++) {
            positions.add(vOffsets[i * 3]);
            positions.add(vOffsets[i * 3 + 1]);
            positions.add(vOffsets[i * 3 + 2]);
            colors.add(r); colors.add(g); colors.add(b);
            normals.add(nx); normals.add(ny); normals.add(nz);
        }
        indices.add(index); indices.add(index + 1); indices.add(index + 2);
        indices.add(index + 2); indices.add(index + 3); indices.add(index);
    }
}
