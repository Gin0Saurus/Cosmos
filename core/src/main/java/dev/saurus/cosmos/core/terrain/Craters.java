package dev.saurus.cosmos.core.terrain;

/**
 * Procedural impact craters below the resolution of the real elevation model, placed on the sphere with a jittered
 * 3D grid per size band. Profiles follow measured lunar morphometry (Pike 1977): simple bowls with depth ~0.2 D and a
 * raised rim ~0.04 D for D below ~15 km, ejecta blanket decaying as (r/R)^-3 out to ~2.5 R.
 * Size-frequency follows a cumulative N(&gt;D) ~ D^-2 power law ("saturation" on old surfaces); {@code density}
 * scales it down for younger surfaces (lunar maria, Martian plains).
 */
public final class Craters {
    private final long seed;
    private final double radiusM;
    /** Band diameters in metres, from largest to smallest. */
    private final double[] bands;

    public Craters(long seed, double radiusM, double maxDiameterM, double minDiameterM) {
        this.seed = seed; this.radiusM = radiusM;
        int n = (int) Math.max(1, Math.round(Math.log(maxDiameterM / minDiameterM) / Math.log(2)) + 1);
        bands = new double[n];
        for (int i = 0; i < n; i++) bands[i] = maxDiameterM / Math.pow(2, i);
    }

    /** Height offset in metres at unit-sphere direction (ux, uy, uz). density in [0, 1]. Also returns freshness via out[0]. */
    public double height(double ux, double uy, double uz, double density, double[] freshOut) {
        double px = ux * radiusM, py = uy * radiusM, pz = uz * radiusM;
        double total = 0, fresh = 0;
        for (int b = 0; b < bands.length; b++) {
            double D = bands[b];
            double cell = D * 2.2;                 // ~1 crater per (2.2 D)^2 area at saturation
            long cx = (long) Math.floor(px / cell), cy = (long) Math.floor(py / cell), cz = (long) Math.floor(pz / cell);
            for (long i = cx - 1; i <= cx + 1; i++) for (long j = cy - 1; j <= cy + 1; j++) for (long k = cz - 1; k <= cz + 1; k++) {
                double h0 = Noise.hash01(seed + b * 7919L, i, j, k);
                if (h0 > density) continue;
                double dd = D * (0.7 + 0.6 * Noise.hash01(seed + 1 + b * 7919L, i, j, k));
                double ccx = (i + Noise.hash01(seed + 2, i, j, k)) * cell;
                double ccy = (j + Noise.hash01(seed + 3, i, j, k)) * cell;
                double ccz = (k + Noise.hash01(seed + 4, i, j, k)) * cell;
                // project the cell centre onto the sphere; only cells near the surface carry a crater
                double cl = Math.sqrt(ccx * ccx + ccy * ccy + ccz * ccz);
                if (Math.abs(cl - radiusM) > cell * 0.5) continue;
                double s = radiusM / cl; ccx *= s; ccy *= s; ccz *= s;
                double dx = px - ccx, dy = py - ccy, dz = pz - ccz;
                double r = Math.sqrt(dx * dx + dy * dy + dz * dz), R = dd / 2;
                if (r > R * 2.5) continue;
                double depth = 0.2 * dd, rim = 0.04 * dd, x = r / R;
                double age = Noise.hash01(seed + 5, i, j, k);      // 0 = fresh, 1 = degraded
                double degrade = 1 - 0.6 * age;
                double h;
                if (x < 1) h = -depth + (depth + rim) * x * x;            // parabolic bowl rising to the rim
                else h = rim * Math.pow(x, -3) * (x < 2.5 ? (2.5 - x) / 1.5 : 0); // ejecta blanket
                total += h * degrade;
                if (x < 2.0) fresh = Math.max(fresh, (1 - age) * (1 - x / 2.0));
            }
        }
        if (freshOut != null) freshOut[0] = fresh;
        return total;
    }
}
