package dev.saurus.cosmos.core.terrain;

/**
 * Earth: NOAA ETOPO 2022 elevation (measured, land + sea floor + ice-sheet surface) resampled to 1/6 degree, plus
 * procedural detail below that resolution. Surface types come from a simple physical climate model (insolation and
 * altitude set temperature; Hadley/Ferrel circulation and distance from the ocean set moisture), so deserts, rainforest,
 * taiga, tundra and ice sheets land where the real ones are without shipping a land-cover map.
 */
public final class EarthTerrain implements PlanetTerrain {
    private final ElevationGrid dem;
    private final Noise n;
    private final double radius;
    private final float[] moisture;        // 1-degree grid, built once from the DEM
    private static final int MW = 360, MH = 180;

    public EarthTerrain(ElevationGrid dem, double radiusM, long seed) {
        this.dem = dem; this.radius = radiusM; this.n = new Noise(seed ^ 0xEA27L);
        this.moisture = buildMoisture();
    }

    @Override public String bodyId() { return "earth"; }
    @Override public double radiusM() { return radius; }
    @Override public boolean measured() { return true; }
    @Override public String provenance() {
        return "Elevation: NOAA NCEI ETOPO 2022 (measured, 1/6 deg); detail < 18 km and surface types: procedural (climate model)";
    }

    @Override public double elevation(double lat, double lon) {
        double e0 = dem.sample(lat, lon);
        double[] u = PlanetProjection.unit(lat, lon);
        double x = u[0] * radius, y = u[1] * radius, z = u[2] * radius;
        double coast = smooth(Math.abs(e0) / 150.0);
        double hills = n.fbm(x, y, z, 3_000, 5, 0.5) * 140 * coast;
        double e = e0 + hills;
        if (e0 > 0) {
            double rough = clamp((e0 - 300) / 2500, 0, 1);
            e += rough * (n.ridged(x, y, z, 9_000, 5) - 0.35) * 2_200;
            e = Math.max(e, e0 * 0.5);              // coastlines stay where ETOPO puts them
        } else {
            e += n.fbm(x, y, z, 12_000, 3, 0.5) * 120 * coast;
            e = Math.min(e, e0 * 0.5);
        }
        return e;
    }

    /** Fraction-of-ocean-around-you moisture index on a 1 degree grid (0 dry .. 1 wet). */
    private float[] buildMoisture() {
        float[] m = new float[MW * MH];
        double[] ringsDeg = { 4, 9, 16 };
        for (int j = 0; j < MH; j++) {
            double lat = Math.toRadians(89.5 - j);
            for (int i = 0; i < MW; i++) {
                double lon = Math.toRadians(i + 0.5);
                int ocean = 0, total = 0;
                for (double r : ringsDeg) for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    double la = lat + Math.toRadians(r) * Math.sin(a);
                    double lo = lon + Math.toRadians(r) * Math.cos(a) / Math.max(0.2, Math.cos(lat));
                    if (la > Math.PI / 2 || la < -Math.PI / 2) continue;
                    // prevailing winds: upwind ocean counts double (westerlies 30-60, trade winds otherwise)
                    double latDeg = Math.abs(Math.toDegrees(lat));
                    boolean upwind = latDeg > 30 && latDeg < 60 ? Math.cos(a) < -0.3 : Math.cos(a) > 0.3;
                    int wgt = upwind ? 2 : 1;
                    if (dem.sample(la, lo) < 0) ocean += wgt;
                    total += wgt;
                }
                double latAbs = Math.abs(Math.toDegrees(lat));
                double band = latitudeRain(latAbs);
                double frac = total == 0 ? 0 : ocean / (double) total;
                // inside the ITCZ, convection recycles rain far inland (Amazon, Congo), so the ocean matters less there
                double local = 0.2 + 0.6 * smooth((14 - latAbs) / 8);
                m[j * MW + i] = (float) (band * (local + (1 - local) * frac));
            }
        }
        return m;
    }

    /** Zonal precipitation from the general circulation: ITCZ, subtropical highs, mid-latitude storm tracks, polar desert. */
    static double latitudeRain(double latDeg) {
        double[][] t = { {0, 1.0}, {8, 0.95}, {15, 0.6}, {22, 0.2}, {28, 0.15}, {35, 0.45}, {45, 0.75}, {55, 0.75}, {65, 0.5}, {75, 0.25}, {90, 0.1} };
        for (int k = 1; k < t.length; k++) if (latDeg <= t[k][0]) {
            double f = (latDeg - t[k - 1][0]) / (t[k][0] - t[k - 1][0]);
            return t[k - 1][1] + f * (t[k][1] - t[k - 1][1]);
        }
        return 0.1;
    }

    double moisture(double lat, double lon) {
        double fx = (Math.toDegrees(Math.IEEEremainder(lon, 2 * Math.PI)) + 360) % 360 - 0.5, fy = 89.5 - Math.toDegrees(lat);
        int i = (int) Math.floor(fx), j = (int) Math.floor(fy);
        double tx = fx - i, ty = fy - j;
        double a = mAt(i, j), b = mAt(i + 1, j), c = mAt(i, j + 1), d = mAt(i + 1, j + 1);
        return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
    }
    private double mAt(int i, int j) { return moisture[Math.max(0, Math.min(MH - 1, j)) * MW + Math.floorMod(i, MW)]; }

    /** Mean annual near-surface temperature (C): insolation by latitude, 6.5 C/km lapse rate. */
    public static double temperature(double lat, double elevationM) {
        // 5 C/km: between the free-air 6.5 C/km and the weaker lapse over sun-heated high plateaus (Tibet, Andes)
        return -25 + 52 * Math.cos(lat) - 5.0 * Math.max(0, elevationM) / 1000;
    }

    @Override public TerrainColumn column(double lat, double lon, double e, double slope) {
        double T = temperature(lat, e);
        double[] u = PlanetProjection.unit(lat, lon);
        double px = u[0] * radius, py = u[1] * radius, pz = u[2] * radius;
        if (e < 0) {
            double ice = T + n.fbm(px, py, pz, 500_000, 3, 0.5) * 6;     // ragged sea-ice edge, ~70 N / 66 S
            Surface s = ice < -6.5 ? Surface.FROZEN_OCEAN : e < -2000 ? Surface.DEEP_OCEAN : Surface.OCEAN;
            return new TerrainColumn(e, s, 0, TerrainColumn.Liquid.WATER);
        }
        double M = moisture(lat, lon) + n.fbm(px, py, pz, 400_000, 3, 0.5) * 0.18;
        Surface s;
        if (T < -8) s = Math.abs(Math.toDegrees(lat)) > 55 ? Surface.ICE_SHEET : T < -11 ? Surface.SNOWY_PEAKS : Surface.TUNDRA;  // ice sheets / glaciers / alpine steppe
        else if (T < 0) s = M > 0.45 ? Surface.SNOW : Surface.TUNDRA;
        else if (T < 6) s = M > 0.32 ? Surface.TAIGA : Surface.TUNDRA;
        else if (M < 0.2) s = Surface.DESERT;
        else if (M < 0.33) s = T > 18 ? Surface.SAVANNA : Surface.GRASSLAND;
        else if (T > 21 && M > 0.62) s = Surface.JUNGLE;
        else if (M < 0.5) s = n.fbm(px, py, pz, 60_000, 3, 0.5) > 0.05 ? Surface.FOREST : Surface.GRASSLAND;
        else s = Surface.FOREST;
        // steep ground is bare rock, or snow and ice where it's cold enough; high but flat plateaus keep their climate class
        if (s != Surface.ICE_SHEET && s != Surface.SNOWY_PEAKS && (slope > 0.6 || (e > 3000 && slope > 0.22))) s = T < -5 ? Surface.SNOWY_PEAKS : Surface.MOUNTAIN;
        if (e < 60 && slope < 0.15 && T > 4 && s != Surface.DESERT && nearCoast(lat, lon)) s = Surface.BEACH;
        return new TerrainColumn(e, s, Double.NaN, TerrainColumn.Liquid.NONE);
    }

    private boolean nearCoast(double lat, double lon) {
        double d = Math.toRadians(0.12);
        return dem.sample(lat + d, lon) < 0 || dem.sample(lat - d, lon) < 0 || dem.sample(lat, lon + d) < 0 || dem.sample(lat, lon - d) < 0;
    }

    static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
    static double smooth(double t) { t = clamp(t, 0, 1); return t * t * (3 - 2 * t); }
}
