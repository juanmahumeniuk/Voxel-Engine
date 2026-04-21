package render;

/**
 * When loaded solid voxel count reaches this threshold, terrain uses vertex color only (no atlas sampling).
 */
public final class RenderLod {
    private RenderLod() {}

    /**
     * Sum of all loaded non-air voxels before atlas sampling is skipped.
     * 1M is exceeded with only a handful of terrain chunks (64³ ≈ 262k voxels each if full);
     * keep this high so normal play stays textured; lower to stress GPU less on huge worlds.
     */
    public static final long TEXTURE_OFF_SOLID_VOXEL_THRESHOLD = 5_000_000_000L;
}
