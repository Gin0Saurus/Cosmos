package dev.saurus.cosmos.core.terrain;

import java.util.stream.IntStream;

/**
 * The far end of the level-of-detail chain: a whole body rendered from the same terrain model the chunks are built
 * from, as an equirectangular albedo map for the space renderer. What you see from orbit is what you land on.
 * Albedos are representative linear reflectances (e.g. ocean 0.06, desert 0.35, lunar mare 0.07, highlands 0.17).
 */
public final class SurfaceMap {
    private SurfaceMap() {}

    public static float[] albedo(Surface s) {
        return switch (s) {
            case OCEAN -> new float[] { 0.020f, 0.045f, 0.090f };
            case DEEP_OCEAN -> new float[] { 0.012f, 0.028f, 0.065f };
            case FROZEN_OCEAN -> new float[] { 0.55f, 0.60f, 0.66f };
            case BEACH -> new float[] { 0.40f, 0.36f, 0.27f };
            case ICE_SHEET -> new float[] { 0.80f, 0.82f, 0.86f };
            case SNOW, SNOWY_PEAKS -> new float[] { 0.66f, 0.68f, 0.72f };
            case TUNDRA -> new float[] { 0.14f, 0.13f, 0.09f };
            case TAIGA -> new float[] { 0.045f, 0.07f, 0.045f };
            case FOREST -> new float[] { 0.055f, 0.09f, 0.045f };
            case GRASSLAND -> new float[] { 0.11f, 0.15f, 0.065f };
            case SAVANNA -> new float[] { 0.24f, 0.21f, 0.12f };
            case DESERT -> new float[] { 0.45f, 0.34f, 0.21f };
            case JUNGLE -> new float[] { 0.035f, 0.07f, 0.03f };
            case MOUNTAIN -> new float[] { 0.17f, 0.155f, 0.14f };
            case LUNAR_HIGHLAND -> new float[] { 0.17f, 0.16f, 0.15f };
            case LUNAR_MARE -> new float[] { 0.070f, 0.068f, 0.064f };
            case LUNAR_EJECTA -> new float[] { 0.26f, 0.25f, 0.24f };
            case LUNAR_POLAR_ICE -> new float[] { 0.20f, 0.20f, 0.21f };
            case MARS_DUST -> new float[] { 0.30f, 0.16f, 0.08f };
            case MARS_DARK_SAND -> new float[] { 0.12f, 0.08f, 0.06f };
            case MARS_ROCK -> new float[] { 0.22f, 0.13f, 0.08f };
            case MARS_VOLCANIC -> new float[] { 0.26f, 0.14f, 0.075f };
            case MARS_LAYERED -> new float[] { 0.34f, 0.25f, 0.18f };
            case MARS_POLAR_ICE -> new float[] { 0.75f, 0.72f, 0.68f };
            case GREY_REGOLITH -> new float[] { 0.14f, 0.135f, 0.13f };
            case DARK_REGOLITH -> new float[] { 0.07f, 0.065f, 0.06f };
            case BASALT_PLAINS -> new float[] { 0.12f, 0.11f, 0.10f };
            case SULFUR_PLAINS -> new float[] { 0.58f, 0.52f, 0.22f };
            case VOLCANIC_VENT -> new float[] { 0.05f, 0.04f, 0.03f };
            case CLEAN_ICE -> new float[] { 0.80f, 0.80f, 0.82f };
            case DIRTY_ICE -> new float[] { 0.50f, 0.47f, 0.43f };
            case DARK_ICE -> new float[] { 0.20f, 0.19f, 0.18f };
            case NITROGEN_ICE -> new float[] { 0.85f, 0.80f, 0.75f };
            case THOLIN -> new float[] { 0.24f, 0.13f, 0.07f };
            case ORGANIC_DUNES -> new float[] { 0.10f, 0.07f, 0.04f };
            case ORGANIC_SHORE -> new float[] { 0.12f, 0.10f, 0.08f };
        };
    }

    /**
     * Renders a body to w x h ABGR pixels (sRGB-encoded linear albedo, alpha = 255). Pixel (x, y) is longitude
     * pi - (x + 0.5) / w * 2pi and latitude pi/2 - (y + 0.5) / h * pi, the same layout as the Milky Way sky map.
     */
    public static int[] render(PlanetTerrain t, int w, int h) {
        int[] px = new int[w * h];
        double dx = 2 * Math.PI / w;
        IntStream.range(0, h).parallel().forEach(y -> {
            double lat = Math.PI / 2 - (y + 0.5) / h * Math.PI;
            for (int x = 0; x < w; x++) {
                double lon = Math.PI - (x + 0.5) / w * 2 * Math.PI;
                double e = t.elevation(lat, lon);
                double ee = t.elevation(lat, lon + dx);
                double slope = Math.abs(ee - e) / Math.max(1, dx * Math.cos(lat) * t.radiusM());
                TerrainColumn c = t.column(lat, lon, e, slope);
                float[] a = albedo(c.surface());
                float r = a[0], g = a[1], b = a[2];
                if (c.liquid() == TerrainColumn.Liquid.METHANE) { r = 0.015f; g = 0.013f; b = 0.010f; }
                else if (c.liquid() == TerrainColumn.Liquid.LAVA) { r = 0.30f; g = 0.08f; b = 0.02f; }
                if (c.liquid() == TerrainColumn.Liquid.WATER && c.liquidM() > e) {      // deeper water is darker
                    float f = (float) Math.max(0.55, 1 - (c.liquidM() - e) / 9000.0);
                    r *= f; g *= f; b *= f;
                }
                px[y * w + x] = 0xFF000000 | (enc(b) << 16) | (enc(g) << 8) | enc(r);
            }
        });
        return px;
    }

    private static int enc(float v) { return (int) Math.min(255, Math.round(255 * Math.pow(Math.max(v, 0), 1 / 2.2))); }
}
