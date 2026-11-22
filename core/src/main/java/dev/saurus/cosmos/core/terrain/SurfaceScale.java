package dev.saurus.cosmos.core.terrain;

/**
 * The one scale system used for every walkable body (see docs/SCALE.md).
 *
 * <ul>
 *   <li>Horizontal: 1 block = {@value #METRES_PER_BLOCK_H} m of planet surface. Earth's equator is ~400 k blocks around,
 *       the Moon's ~109 k, Mars' ~213 k: relative sizes are exact.</li>
 *   <li>Vertical: 1 block = {@value #METRES_PER_BLOCK_V} m of relief (2x vertical exaggeration, the same for every body),
 *       so Everest stands ~177 blocks above the sea and Olympus Mons ~430 blocks above Mars' datum.</li>
 *   <li>Local physics is still 1 block = 1 m: the player is 1.8 blocks tall and gravity is the body's real surface
 *       gravity in m/s^2 applied per block.</li>
 * </ul>
 */
public final class SurfaceScale {
    private SurfaceScale() {}
    public static final double METRES_PER_BLOCK_H = 100.0;
    public static final double METRES_PER_BLOCK_V = 50.0;
    /** Block Y of the body's reference surface (sea level / datum / mean radius). */
    public static final int DATUM_Y = 63;
    public static final int MIN_Y = -256;
    public static final int HEIGHT = 1024;
    public static final int MAX_Y = MIN_Y + HEIGHT - 1;
    /** Leaving the top of the atmosphere column launches you into space. */
    public static final int LAUNCH_Y = MAX_Y - 24;

    /** Blocks around the equator for a body of the given mean radius, rounded to a multiple of 64 (chunk aligned halves). */
    public static int circumferenceBlocks(double radiusM) {
        long c = Math.round(2 * Math.PI * radiusM / METRES_PER_BLOCK_H / 64.0) * 64L;
        return (int) Math.max(256, Math.min(c, 59_000_000L));
    }

    public static double blockYForElevation(double elevationM) { return DATUM_Y + elevationM / METRES_PER_BLOCK_V; }
    public static double elevationForBlockY(double y) { return (y - DATUM_Y) * METRES_PER_BLOCK_V; }
}
