package dev.saurus.cosmos.core.terrain;

/**
 * Mars: NASA MGS MOLA MEGDR (measured) resampled to 1/4 degree - Olympus Mons, Tharsis, Valles Marineris, Hellas and
 * the north/south dichotomy are all real - plus procedural craters (fewer on the young northern plains and volcanoes),
 * dust, dune fields and the residual polar caps (north ~1000 km across, south ~400 km, offset toward 315 E).
 */
public final class MarsTerrain implements PlanetTerrain {
    private final ElevationGrid dem;
    private final Noise n;
    private final Craters craters;
    private final double radius;
    private final ElevationPatches patches = ElevationPatches.bundled("mars");
    private static final double[] SOUTH_CAP = PlanetProjection.unit(Math.toRadians(-87.0), Math.toRadians(-45));

    public MarsTerrain(ElevationGrid dem, double radiusM, long seed) {
        this.dem = dem; this.radius = radiusM; this.n = new Noise(seed ^ 0x4A45L);
        this.craters = new Craters(seed ^ 0x4C4A7E4L, radiusM, 24_000, 375);
    }

    @Override public String bodyId() { return "mars"; }
    @Override public double radiusM() { return radius; }
    @Override public boolean measured() { return true; }
    @Override public String provenance() {
        return "Elevation: NASA MGS MOLA MEGDR (measured, 1/4 deg; 1/16 deg around Olympus Mons, Jezero, Gale, Valles Marineris...); craters < 24 km, dunes, dust and cap edges: procedural";
    }

    double craterDensity(double e0, double latDeg) {
        if (Math.abs(latDeg) > 78) return 0.05;            // young polar layered deposits
        if (e0 > 6000) return 0.08;                          // Tharsis / Elysium volcano flanks
        if (e0 < -3500) return 0.3;                          // northern lowlands, Hellas floor
        return 0.85;                                         // ancient southern highlands
    }

    @Override public double elevation(double lat, double lon) {
        double[] u = PlanetProjection.unit(lat, lon);
        double e0 = patches.blend(lat, lon, dem.sample(lat, lon))[0];
        double c = craters.height(u[0], u[1], u[2], craterDensity(e0, Math.toDegrees(lat)), null);
        double x = u[0] * radius, y = u[1] * radius, z = u[2] * radius;
        double dust = n.fbm(x, y, z, 2_500, 4, 0.5) * 80 + n.fbm(x, y, z, 20_000, 3, 0.5) * 250;
        double dunes = Math.max(0, n.fbm(x, y, z, 60_000, 2, 0.5)) * Math.abs(Math.sin(x / 700 + n.noise(x / 3000, y / 3000, z / 3000) * 3)) * 60;
        return e0 + c + dust + dunes;
    }

    @Override public TerrainColumn column(double lat, double lon, double e, double slope) {
        double latDeg = Math.toDegrees(lat);
        double[] u = PlanetProjection.unit(lat, lon);
        double x = u[0] * radius, y = u[1] * radius, z = u[2] * radius;
        double edge = n.fbm(x, y, z, 40_000, 3, 0.5) * 2.0;
        double southAng = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, u[0] * SOUTH_CAP[0] + u[1] * SOUTH_CAP[1] + u[2] * SOUTH_CAP[2]))));
        Surface s;
        if (latDeg > 80.5 + edge || southAng < 4.0 + edge) s = Surface.MARS_POLAR_ICE;
        else if (Math.abs(latDeg) > 75 + edge) s = Surface.MARS_LAYERED;
        else if (slope > 0.45) s = e < -3000 ? Surface.MARS_LAYERED : Surface.MARS_ROCK;   // canyon walls show layering
        else if (e > 7000) s = Surface.MARS_VOLCANIC;
        else if (n.fbm(x, y, z, 30_000, 3, 0.5) > 0.22 && e < 0) s = Surface.MARS_DARK_SAND;
        else s = Surface.MARS_DUST;
        return TerrainColumn.dry(e, s);
    }
}
