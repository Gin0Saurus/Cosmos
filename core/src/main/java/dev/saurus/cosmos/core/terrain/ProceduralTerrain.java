package dev.saurus.cosmos.core.terrain;

import java.util.List;

/**
 * Bodies without a bundled elevation model get scientifically plausible procedural terrain, parameterised from what is
 * known about them (crater density, relief, composition, named giant features). Everything here is labelled
 * "procedural" in-game; nothing pretends to be measured.
 */
public final class ProceduralTerrain implements PlanetTerrain {
    /** A named large feature: a disc on the surface that shifts elevation (optionally flattening it) and/or sets the surface. */
    public record Region(String name, double latDeg, double lonDeg, double radiusDeg, double deltaM, boolean flatten, Surface surface) {}

    public record Profile(String bodyId, String notes,
                          double craterMaxD, double craterDensity,
                          double reliefM, double reliefWavelengthM, double ridgeM, double ridgeWavelengthM,
                          Surface base, Surface low, Surface high, Surface fresh,
                          TerrainColumn.Liquid liquid, double liquidLevelM, double liquidMinAbsLatDeg,
                          boolean lineae, boolean southStripes, boolean paterae, boolean dunes,
                          List<Region> regions) {}

    private final Profile p;
    private final double radius;
    private final Noise n;
    private final Craters craters;
    private final double[][] regionUnit;

    public ProceduralTerrain(Profile p, double radiusM, long seed) {
        this.p = p; this.radius = radiusM;
        this.n = new Noise(seed ^ p.bodyId().hashCode() * 0x9E37L);
        double maxD = Math.min(p.craterMaxD(), radiusM * 0.6);
        this.craters = p.craterDensity() > 0 ? new Craters(seed ^ 0xC0FFEEL ^ p.bodyId().hashCode(), radiusM, maxD, Math.min(375, maxD / 4)) : null;
        regionUnit = new double[p.regions().size()][];
        for (int i = 0; i < regionUnit.length; i++) {
            Region r = p.regions().get(i);
            regionUnit[i] = PlanetProjection.unit(Math.toRadians(r.latDeg()), Math.toRadians(r.lonDeg()));
        }
    }

    @Override public String bodyId() { return p.bodyId(); }
    @Override public double radiusM() { return radius; }
    @Override public boolean measured() { return false; }
    @Override public String provenance() { return "Procedural terrain (no bundled elevation model): " + p.notes(); }

    private double regionWeight(int i, double[] u) {
        double[] c = regionUnit[i];
        double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, u[0] * c[0] + u[1] * c[1] + u[2] * c[2]))));
        double r = p.regions().get(i).radiusDeg();
        return EarthTerrain.smooth((r - ang) / (r * 0.25));
    }

    @Override public double elevation(double lat, double lon) {
        double[] u = PlanetProjection.unit(lat, lon);
        double x = u[0] * radius, y = u[1] * radius, z = u[2] * radius;
        double e = n.fbm(x, y, z, p.reliefWavelengthM(), 5, 0.52) * p.reliefM();
        if (p.ridgeM() > 0) e += (n.ridged(x, y, z, p.ridgeWavelengthM(), 5) - 0.3) * p.ridgeM();
        e += n.fbm(x, y, z, 1_200, 3, 0.5) * Math.min(60, p.reliefM() * 0.1);
        if (craters != null) e += craters.height(u[0], u[1], u[2], p.craterDensity(), null);
        if (p.lineae()) {
            double l = Math.abs(n.noise(x / 40_000, y / 40_000, z / 40_000));
            if (l < 0.03) e += (0.03 - l) / 0.03 * 150;            // double ridges along the lineae
        }
        if (p.paterae()) {
            double v = n.fbm(x, y, z, 50_000, 2, 0.5);
            if (v > 0.45) e -= Math.min(1, (v - 0.45) * 8) * 600;   // volcanic depressions
        }
        for (int i = 0; i < regionUnit.length; i++) {
            Region r = p.regions().get(i);
            double w = regionWeight(i, u);
            if (w <= 0) continue;
            if (r.flatten()) e = e * (1 - w * 0.9) + r.deltaM() * w;
            else e += r.deltaM() * w;
        }
        return e;
    }

    @Override public TerrainColumn column(double lat, double lon, double e, double slope) {
        double[] u = PlanetProjection.unit(lat, lon);
        double x = u[0] * radius, y = u[1] * radius, z = u[2] * radius;
        double latDeg = Math.toDegrees(lat);
        Surface s = p.base();
        if (e < -p.reliefM() * 0.4) s = p.low();
        else if (e > p.reliefM() * 0.5 || slope > 0.5) s = p.high();
        if (craters != null && p.fresh() != null) {
            double[] fr = new double[1];
            craters.height(u[0], u[1], u[2], p.craterDensity(), fr);
            if (fr[0] > 0.6) s = p.fresh();
        }
        if (p.lineae() && Math.abs(n.noise(x / 40_000, y / 40_000, z / 40_000)) < 0.03) s = Surface.DIRTY_ICE;
        if (p.southStripes() && latDeg < -68 && Math.abs(Math.sin(Math.toRadians(lon) * 2 + latDeg * 0.15)) < 0.06) s = Surface.CLEAN_ICE;
        if (p.dunes() && Math.abs(latDeg) < 30 && n.fbm(x, y, z, 80_000, 2, 0.5) > -0.1) s = Surface.ORGANIC_DUNES;
        for (int i = 0; i < regionUnit.length; i++)
            if (p.regions().get(i).surface() != null && regionWeight(i, u) > 0.5) s = p.regions().get(i).surface();
        if (p.paterae() && n.fbm(x, y, z, 50_000, 2, 0.5) > 0.62)
            return new TerrainColumn(e, Surface.VOLCANIC_VENT, e + 2 * SurfaceScale.METRES_PER_BLOCK_V, TerrainColumn.Liquid.LAVA);
        if (p.liquid() != TerrainColumn.Liquid.NONE && Math.abs(latDeg) >= p.liquidMinAbsLatDeg() && e < p.liquidLevelM())
            return new TerrainColumn(e, p.low(), p.liquidLevelM(), p.liquid());
        if (p.liquid() != TerrainColumn.Liquid.NONE && Math.abs(latDeg) >= p.liquidMinAbsLatDeg() && e < p.liquidLevelM() + 150)
            s = Surface.ORGANIC_SHORE;
        return TerrainColumn.dry(e, s);
    }
}
