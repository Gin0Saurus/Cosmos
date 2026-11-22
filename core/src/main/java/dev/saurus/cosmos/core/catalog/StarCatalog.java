package dev.saurus.cosmos.core.catalog;

import dev.saurus.cosmos.core.coords.UniversePos;
import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.util.Csv;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real stars (HYG v4.1: Hipparcos, Yale Bright Star, Gliese), structure-of-arrays for cache-friendly rendering.
 * Positions are heliocentric ICRS parsecs at epoch J2000 with linear space motion (HYG vx,vy,vz in pc/yr), so the sky
 * changes correctly with simulated time. Stars added at runtime (exoplanet hosts not in Hipparcos) use the same arrays.
 */
public final class StarCatalog {
    private int n;
    private double[] x, y, z;          // pc
    private float[] vx, vy, vz;        // pc / yr
    private float[] absMag, appMag, bv, lum;
    private String[] name, bayer, con, ids, spect, hygId;
    private byte[] source;             // 0 = HYG, 1 = exoplanet catalogue host, 2 = black hole companion etc.
    private final Map<String, Integer> byHyg = new HashMap<>();
    private Grid grid;

    public static final double GRID_CELL_PC = 10.0;

    public int size() { return n; }

    public static StarCatalog load(InputStream in) throws IOException {
        StarCatalog c = new StarCatalog();
        c.alloc(120_000);
        Csv.read(in, r -> c.add(r.s("hyg"), r.s("name"), r.s("bayer"), r.s("con"), r.s("ids"),
                r.d("x_pc", 0), r.d("y_pc", 0), r.d("z_pc", 0), (float) r.d("mag", 99), (float) r.d("absmag", 99),
                (float) r.d("bv", Double.NaN), r.s("spect"), (float) r.d("lum_sun", 0),
                (float) r.d("vx", 0), (float) r.d("vy", 0), (float) r.d("vz", 0), (byte) 0));
        c.buildIndex();
        return c;
    }

    public static StarCatalog loadDefault() throws IOException {
        try (InputStream in = StarCatalog.class.getResourceAsStream("/cosmos/data/stars.csv")) {
            if (in == null) throw new IOException("stars.csv missing");
            return load(in);
        }
    }

    private void alloc(int cap) {
        x = new double[cap]; y = new double[cap]; z = new double[cap];
        vx = new float[cap]; vy = new float[cap]; vz = new float[cap];
        absMag = new float[cap]; appMag = new float[cap]; bv = new float[cap]; lum = new float[cap];
        name = new String[cap]; bayer = new String[cap]; con = new String[cap]; ids = new String[cap];
        spect = new String[cap]; hygId = new String[cap]; source = new byte[cap];
    }

    private void grow() {
        int cap = x.length * 3 / 2 + 16;
        x = Arrays.copyOf(x, cap); y = Arrays.copyOf(y, cap); z = Arrays.copyOf(z, cap);
        vx = Arrays.copyOf(vx, cap); vy = Arrays.copyOf(vy, cap); vz = Arrays.copyOf(vz, cap);
        absMag = Arrays.copyOf(absMag, cap); appMag = Arrays.copyOf(appMag, cap); bv = Arrays.copyOf(bv, cap); lum = Arrays.copyOf(lum, cap);
        name = Arrays.copyOf(name, cap); bayer = Arrays.copyOf(bayer, cap); con = Arrays.copyOf(con, cap); ids = Arrays.copyOf(ids, cap);
        spect = Arrays.copyOf(spect, cap); hygId = Arrays.copyOf(hygId, cap); source = Arrays.copyOf(source, cap);
    }

    public int add(String hyg, String nm, String by, String cn, String id, double px, double py, double pz, float mag, float absm,
                   float colour, String sp, float l, float svx, float svy, float svz, byte src) {
        if (n == x.length) grow();
        int i = n++;
        hygId[i] = hyg; name[i] = nm; bayer[i] = by; con[i] = cn; ids[i] = id; x[i] = px; y[i] = py; z[i] = pz;
        appMag[i] = mag; absMag[i] = absm; bv[i] = colour; spect[i] = sp; lum[i] = l; vx[i] = svx; vy[i] = svy; vz[i] = svz; source[i] = src;
        if (hyg != null && !hyg.isEmpty()) byHyg.put(hyg, i);
        if (grid != null) grid.insert(i);
        return i;
    }

    public void buildIndex() { grid = new Grid(); for (int i = 0; i < n; i++) grid.insert(i); }

    // --------------------------------------------------------------- accessors
    public Integer indexOfHyg(String hyg) { return byHyg.get(hyg); }
    public String name(int i) { return name[i]; }
    public String bayerRaw(int i) { return bayer[i]; }
    public String constellation(int i) { return con[i]; }
    public String ids(int i) { return ids[i]; }
    public String spectralType(int i) { return spect[i]; }
    public String hygId(int i) { return hygId[i]; }
    public float absMag(int i) { return absMag[i]; }
    public float appMagFromSun(int i) { return appMag[i]; }
    public float luminosity(int i) { return lum[i]; }
    public boolean fromHyg(int i) { return source[i] == 0; }
    public float bv(int i) { return bv[i]; }

    public String displayName(int i) {
        if (name[i] != null && !name[i].isEmpty()) return name[i];
        String[] b = Designations.expandBayer(bayer[i], con[i]);
        if (b.length > 0) return b[b.length > 1 ? 1 : 0];
        String id = ids[i];
        if (id != null && !id.isEmpty()) return id.split("\\|")[0];
        return "HYG " + hygId[i];
    }

    /** Position in parsecs at simulation time (seconds since J2000), including proper motion. */
    public Vec3d positionPc(int i, double t) {
        double yr = t / Units.JULIAN_YEAR_S;
        return new Vec3d(x[i] + vx[i] * yr, y[i] + vy[i] * yr, z[i] + vz[i] * yr);
    }
    public UniversePos position(int i, double t) { Vec3d p = positionPc(i, t); return UniversePos.ofParsecs(p.x(), p.y(), p.z()); }

    public double teff(int i) {
        if (!Float.isNaN(bv[i])) return StarColor.teffFromBV(bv[i]);
        return 5772; // unknown colour -> solar (flagged as estimate in info panel)
    }
    public float[] colour(int i) { return StarColor.rgbFromTemperature(teff(i)); }
    /** Radius estimate (R_sun) from catalogue luminosity and colour temperature (Stefan-Boltzmann). */
    public double radiusEstimateRsun(int i) { return lum[i] > 0 ? StarColor.radiusFromLT(lum[i], teff(i)) : 1.0; }

    /** Apparent V magnitude of star i seen from an arbitrary observer position (pc). */
    public double apparentMagFrom(int i, Vec3d observerPc, double t) {
        double d = Math.max(positionPc(i, t).distance(observerPc), 1e-9);
        return absMag[i] + 5 * (Math.log10(d) - 1);
    }

    /** Indices of stars within radius (pc) of p, sorted by distance. */
    public List<Integer> within(Vec3d pPc, double radiusPc, double t) {
        List<Integer> out = new ArrayList<>();
        grid.query(pPc, radiusPc + 2, i -> { if (positionPc(i, t).distance(pPc) <= radiusPc) out.add(i); });
        out.sort((a, b) -> Double.compare(positionPc(a, t).distance(pPc), positionPc(b, t).distance(pPc)));
        return out;
    }

    public int nearest(Vec3d pPc, double t) {
        for (double r = 2; r < 1e5; r *= 2) { List<Integer> l = within(pPc, r, t); if (!l.isEmpty()) return l.get(0); }
        return -1;
    }

    /** Uniform spatial hash grid over J2000 positions (proper motion < 1 pc per 10 kyr for almost all stars). */
    private final class Grid {
        final Map<Long, int[]> cells = new HashMap<>();
        final Map<Long, Integer> counts = new HashMap<>();
        long key(long ix, long iy, long iz) { return (ix & 0x1FFFFF) | ((iy & 0x1FFFFF) << 21) | ((iz & 0x1FFFFF) << 42); }
        long cell(double v) { return (long) Math.floor(v / GRID_CELL_PC); }
        void insert(int i) {
            long k = key(cell(x[i]), cell(y[i]), cell(z[i]));
            int[] a = cells.get(k); int c = counts.getOrDefault(k, 0);
            if (a == null) a = new int[4]; else if (c == a.length) a = Arrays.copyOf(a, c * 2);
            a[c] = i; cells.put(k, a); counts.put(k, c + 1);
        }
        void query(Vec3d p, double r, java.util.function.IntConsumer sink) {
            long x0 = cell(p.x() - r), x1 = cell(p.x() + r), y0 = cell(p.y() - r), y1 = cell(p.y() + r), z0 = cell(p.z() - r), z1 = cell(p.z() + r);
            if ((x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > cells.size()) { // big query: scan occupied cells
                for (Map.Entry<Long, int[]> e : cells.entrySet()) { int c = counts.get(e.getKey()); for (int j = 0; j < c; j++) sink.accept(e.getValue()[j]); }
                return;
            }
            for (long a = x0; a <= x1; a++) for (long b = y0; b <= y1; b++) for (long cz = z0; cz <= z1; cz++) {
                long k = key(a, b, cz); int[] arr = cells.get(k); if (arr == null) continue;
                int c = counts.get(k); for (int j = 0; j < c; j++) sink.accept(arr[j]);
            }
        }
    }
}
