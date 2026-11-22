package dev.saurus.cosmos.core.coords;

import dev.saurus.cosmos.core.math.Vec3d;

/**
 * An absolute position anywhere in the universe, with sub-millimetre precision everywhere.
 *
 * <p>Representation: a 64-bit integer sector index per axis plus a double offset inside the sector.
 * Sector edge = 2^32 m (~4.3 million km, ~11 Earth-Moon distances). Inside a sector the double offset is in
 * [0, 2^32) so its ulp is at most 2^-20 m (~1 micrometre). The long index covers +/-2^63 sectors = +/-4.0e28 m,
 * about 90 times the radius of the observable universe (4.4e26 m). No arbitrary boundary is needed anywhere.
 *
 * <p>Axes are ICRS/J2000 equatorial; origin is the Sun's centre (heliocentric). Differences between two
 * positions are computed exactly in integer sector space first, then converted to double, so relative
 * vectors keep full precision even when both points are billions of light-years from the origin.
 */
public final class UniversePos {
    public static final int SECTOR_BITS = 32;
    public static final double SECTOR = (double) (1L << SECTOR_BITS);
    public static final UniversePos ORIGIN = new UniversePos(0, 0, 0, 0, 0, 0);

    public final long sx, sy, sz;
    public final double lx, ly, lz; // always normalised into [0, SECTOR)

    private UniversePos(long sx, long sy, long sz, double lx, double ly, double lz) {
        this.sx = sx; this.sy = sy; this.sz = sz; this.lx = lx; this.ly = ly; this.lz = lz;
    }

    /** Build from a sector + arbitrary (possibly large or negative) offset in metres, normalising. */
    public static UniversePos of(long sx, long sy, long sz, double ox, double oy, double oz) {
        long cx = (long) Math.floor(ox / SECTOR), cy = (long) Math.floor(oy / SECTOR), cz = (long) Math.floor(oz / SECTOR);
        double nx = ox - cx * SECTOR, ny = oy - cy * SECTOR, nz = oz - cz * SECTOR;
        // floating guard: values extremely close to SECTOR can round up
        if (nx >= SECTOR) { nx -= SECTOR; cx++; } if (ny >= SECTOR) { ny -= SECTOR; cy++; } if (nz >= SECTOR) { nz -= SECTOR; cz++; }
        return new UniversePos(Math.addExact(sx, cx), Math.addExact(sy, cy), Math.addExact(sz, cz), nx, ny, nz);
    }

    /** From heliocentric metres (fine up to ~1e15 m before precision matters; use ofParsecs for stars). */
    public static UniversePos ofMetres(Vec3d m) { return of(0, 0, 0, m.x(), m.y(), m.z()); }

    /**
     * From a position in parsecs without losing precision: the integer part of the sector count is
     * computed in parsec space first, so a star at 10 kpc is still placed with mm-scale precision
     * relative to its own catalogue value (the catalogue itself is far less precise than that).
     */
    public static UniversePos ofParsecs(double xpc, double ypc, double zpc) {
        return new Builder().addParsecs(xpc, ypc, zpc).build();
    }

    public static UniversePos ofLightYears(Vec3d ly) { return ofParsecs(ly.x() * Units.LIGHT_YEAR / Units.PARSEC,
            ly.y() * Units.LIGHT_YEAR / Units.PARSEC, ly.z() * Units.LIGHT_YEAR / Units.PARSEC); }

    public UniversePos add(Vec3d metres) { return of(sx, sy, sz, lx + metres.x(), ly + metres.y(), lz + metres.z()); }

    /** Exact vector from this to other, in metres. */
    public Vec3d to(UniversePos o) {
        return new Vec3d((o.sx - sx) * SECTOR + (o.lx - lx), (o.sy - sy) * SECTOR + (o.ly - ly), (o.sz - sz) * SECTOR + (o.lz - lz));
    }

    public double distanceTo(UniversePos o) { return to(o).length(); }

    /** Approximate heliocentric metres (loses precision far away: display/LOD use only). */
    public Vec3d approxMetres() { return new Vec3d(sx * SECTOR + lx, sy * SECTOR + ly, sz * SECTOR + lz); }

    @Override public boolean equals(Object o) {
        return o instanceof UniversePos p && p.sx == sx && p.sy == sy && p.sz == sz && p.lx == lx && p.ly == ly && p.lz == lz;
    }
    @Override public int hashCode() { return Long.hashCode(sx * 31 + sy * 17 + sz) ^ Double.hashCode(lx + ly + lz); }
    @Override public String toString() { return "UPos[" + sx + "," + sy + "," + sz + " + " + new Vec3d(lx, ly, lz) + "]"; }

    /** Accumulates large distances without precision loss (Kahan-style split into sectors + remainder). */
    public static final class Builder {
        private long sx, sy, sz; private double ox, oy, oz;
        private static final double SECTORS_PER_PC = Units.PARSEC / SECTOR;
        public Builder addParsecs(double x, double y, double z) {
            double fx = x * SECTORS_PER_PC, fy = y * SECTORS_PER_PC, fz = z * SECTORS_PER_PC;
            long ix = (long) Math.floor(fx), iy = (long) Math.floor(fy), iz = (long) Math.floor(fz);
            sx += ix; sy += iy; sz += iz;
            // remainder in metres: (x pc - ix sectors). Computed as (fx - ix)*SECTOR which is exact enough:
            // fx has ~16 significant digits, so for 1 Gpc (2.8e13 sectors) the remainder keeps ~3 digits of the sector,
            // i.e. ~1e9 m accuracy -- far below catalogue uncertainty at that distance.
            ox += (fx - ix) * SECTOR; oy += (fy - iy) * SECTOR; oz += (fz - iz) * SECTOR;
            return this;
        }
        public Builder addMetres(Vec3d m) { ox += m.x(); oy += m.y(); oz += m.z(); return this; }
        public UniversePos build() { return of(sx, sy, sz, ox, oy, oz); }
    }
}
