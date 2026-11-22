package dev.saurus.cosmos.core.terrain;

/**
 * One sampled surface point. {@code elevationM} is relative to the body's reference surface (sea level, areoid or
 * mean sphere). {@code liquidM} is the liquid surface elevation, or NaN where there is none.
 */
public record TerrainColumn(double elevationM, Surface surface, double liquidM, Liquid liquid) {
    public enum Liquid { NONE, WATER, LAVA, METHANE }
    public static TerrainColumn dry(double e, Surface s) { return new TerrainColumn(e, s, Double.NaN, Liquid.NONE); }
}
