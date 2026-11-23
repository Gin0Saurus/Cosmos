package dev.saurus.cosmos.core.terrain;

/**
 * The Moon: NASA LRO LOLA LDEM (measured) resampled to 1/4 degree, plus procedural impact craters from 24 km down to
 * 375 m, and regolith roughness. Maria are delimited by the IAU feature centres and diameters (approximate outlines);
 * they are smoother and carry fewer craters because they are ~1 Gyr younger than the highlands.
 */
public final class MoonTerrain implements PlanetTerrain {
    private final ElevationGrid dem;
    private final Noise n;
    private final Craters craters;
    private final double radius;
    // name, lat, lon (deg E), diameter km: IAU Gazetteer of Planetary Nomenclature (rounded)
    static final double[][] MARIA = {
        {32.8, -15.6, 1146}, {28.0, 17.5, 674}, {8.5, 31.4, 873}, {17.0, 59.1, 556}, {-7.8, 53.7, 909}, {-15.2, 35.5, 333},
        {-21.3, -16.6, 715}, {18.4, -57.4, 2568}, {-24.4, -38.6, 389}, {-10.0, -23.0, 376}, {13.3, 3.6, 242}, {7.5, -30.9, 513},
        {27.3, 147.9, 276}, {-19.4, -92.8, 294}, {1.3, 87.5, 373}, {13.3, 86.1, 358}, {-38.9, 93.0, 603},
        {56.0, -40.0, 420}, {57.0, -10.0, 420}, {56.0, 20.0, 420}, {55.0, 40.0, 380}, {38.0, -32.0, 600}, {-3.0, -40.0, 900}
    };
    private final double[][] mariaUnit;
    private final ElevationPatches patches = ElevationPatches.bundled("moon");

    public MoonTerrain(ElevationGrid dem, double radiusM, long seed) {
        this.dem = dem; this.radius = radiusM; this.n = new Noise(seed ^ 0x300L);
        this.craters = new Craters(seed ^ 0xC4A7E4L, radiusM, 24_000, 375);
        mariaUnit = new double[MARIA.length][];
        for (int i = 0; i < MARIA.length; i++) {
            double[] u = PlanetProjection.unit(Math.toRadians(MARIA[i][0]), Math.toRadians(MARIA[i][1]));
            mariaUnit[i] = new double[] { u[0], u[1], u[2], MARIA[i][2] * 1000 / 2 / radiusM };
        }
    }

    @Override public String bodyId() { return "moon"; }
    @Override public double radiusM() { return radius; }
    @Override public boolean measured() { return true; }
    @Override public String provenance() {
        return "Elevation: NASA LRO LOLA LDEM (measured, 1/4 deg; 1/16 deg around Apollo sites, Tycho, Copernicus, Shackleton...); craters < 24 km and regolith detail: procedural; mare outlines: IAU (approx.)";
    }

    /** 0 = highlands, 1 = deep inside a mare (soft edge). */
    double mare(double[] u) {
        double best = 0;
        double wob = n.fbm(u[0] * 9, u[1] * 9, u[2] * 9, 1, 3, 0.5) * 0.25;
        for (double[] m : mariaUnit) {
            double ang = Math.acos(Math.max(-1, Math.min(1, u[0] * m[0] + u[1] * m[1] + u[2] * m[2])));
            double r = ang / m[3] + wob;
            best = Math.max(best, EarthTerrain.smooth((1.0 - r) / 0.15));
        }
        return best;
    }

    @Override public double elevation(double lat, double lon) {
        double[] u = PlanetProjection.unit(lat, lon);
        double e0 = patches.blend(lat, lon, dem.sample(lat, lon))[0];
        double mare = mare(u);
        double density = 1.0 - 0.7 * mare;
        double c = craters.height(u[0], u[1], u[2], density, null);
        double x = u[0] * radius, y = u[1] * radius, z = u[2] * radius;
        double rough = n.fbm(x, y, z, 900, 4, 0.55) * (45 - 25 * mare) + n.fbm(x, y, z, 15_000, 3, 0.5) * (300 - 200 * mare);
        return e0 + c + rough;
    }

    @Override public TerrainColumn column(double lat, double lon, double e, double slope) {
        double[] u = PlanetProjection.unit(lat, lon);
        double[] fresh = new double[1];
        craters.height(u[0], u[1], u[2], 1.0, fresh);
        double mare = mare(u);
        Surface s;
        if (Math.abs(Math.toDegrees(lat)) > 87.5 && e < dem.sample(lat, lon) - 400) s = Surface.LUNAR_POLAR_ICE; // permanently shadowed floors
        else if (fresh[0] > 0.55) s = Surface.LUNAR_EJECTA;
        else s = mare > 0.5 ? Surface.LUNAR_MARE : Surface.LUNAR_HIGHLAND;
        return TerrainColumn.dry(e, s);
    }
}
