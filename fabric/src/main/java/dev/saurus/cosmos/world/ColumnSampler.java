package dev.saurus.cosmos.world;

import dev.saurus.cosmos.core.terrain.EarthTerrain;
import dev.saurus.cosmos.core.terrain.PlanetProjection;
import dev.saurus.cosmos.core.terrain.PlanetTerrain;
import dev.saurus.cosmos.core.terrain.SurfaceScale;
import dev.saurus.cosmos.core.terrain.TerrainColumn;

/** Turns block (x, z) on a body into terrain: elevation, slope and surface. Shared by the generator and biome source. */
public final class ColumnSampler {
    public final String body;
    public final PlanetTerrain terrain;
    public final PlanetProjection proj;

    public ColumnSampler(String body) {
        this.body = body; this.terrain = Planets.terrain(body); this.proj = Planets.projection(body);
    }

    public double elevation(double bx, double bz) {
        double[] ll = proj.latLon(bx + 0.5, bz + 0.5);
        return terrain.elevation(ll[0], ll[1]);
    }

    /** Elevation of an 18x18 grid around a chunk (index [(dz+1)*18 + dx+1], dx,dz in -1..16). */
    public double[] chunkGrid(int x0, int z0) {
        double[] g = new double[18 * 18];
        for (int k = 0; k < 18; k++) for (int i = 0; i < 18; i++) g[k * 18 + i] = elevation(x0 + i - 1, z0 + k - 1);
        return g;
    }

    public TerrainColumn column(double bx, double bz, double e, double slope) {
        double[] ll = proj.latLon(bx + 0.5, bz + 0.5);
        return terrain.column(ll[0], ll[1], e, slope);
    }

    /** Surface type at a single column (used for biomes). */
    public TerrainColumn columnAt(int bx, int bz) {
        double e = elevation(bx, bz);
        double ex = elevation(bx + 3, bz) - elevation(bx - 3, bz), ez = elevation(bx, bz + 3) - elevation(bx, bz - 3);
        double slope = Math.hypot(ex, ez) / (6 * SurfaceScale.METRES_PER_BLOCK_H);
        return column(bx, bz, e, slope);
    }

    public double temperature(double bx, double bz, double e) {
        if (!body.equals("earth")) return 0;
        return EarthTerrain.temperature(proj.latLon(bx + 0.5, bz + 0.5)[0], e);
    }

    public static int surfaceY(double elevationM) { return (int) Math.floor(SurfaceScale.blockYForElevation(elevationM)); }
}
