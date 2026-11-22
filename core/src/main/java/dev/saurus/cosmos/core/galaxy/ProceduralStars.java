package dev.saurus.cosmos.core.galaxy;

import dev.saurus.cosmos.core.catalog.StarColor;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.util.Hash;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic procedural stars for the parts of the Milky Way that no catalogue covers.
 *
 * <p>Space is cut into cubic cells (galactocentric axes, edge {@link #CELL_PC}). The content of a cell is a pure
 * function of (universe seed, cell index): the same cell always yields the same stars, on any machine, forever.
 * Star count ~ Poisson(local density x model density x cell volume). Masses follow the Kroupa (2001) IMF above the
 * hydrogen-burning limit; ages are uniform over 10 Gyr; stars past their main-sequence lifetime become white dwarfs,
 * neutron stars or black holes. Luminosity/radius use standard main-sequence scaling relations. All PROCEDURAL.
 *
 * <p>Consistency with real data: no procedural star is placed within {@link #CATALOGUE_RADIUS_PC} of the Sun
 * (where HYG/Gliese are authoritative), nor anywhere it would appear brighter than V = {@link #CATALOGUE_MAG_LIMIT}
 * from Earth (such stars are real and already catalogued).
 */
public final class ProceduralStars {
    public static final double CELL_PC = 25.0;
    public static final double CATALOGUE_RADIUS_PC = 25.0;
    public static final double CATALOGUE_MAG_LIMIT = 8.0;

    public enum Type { MAIN_SEQUENCE, GIANT, WHITE_DWARF, NEUTRON_STAR, BLACK_HOLE }

    public record PStar(long seed, Vec3d icrsPc, double massSun, double lumSun, double radiusSun, double teffK, Type type, double ageGyr) {
        public float[] colour() { return StarColor.rgbFromTemperature(teffK); }
        public double absMag() { return 4.83 - 2.5 * Math.log10(Math.max(lumSun, 1e-12)); }
        public String designation() {
            return String.format("CSX %s", Long.toUnsignedString(seed & 0xFFFFFFFFFFL, 36).toUpperCase());
        }
    }

    private final long universeSeed;
    public ProceduralStars(long universeSeed) { this.universeSeed = universeSeed; }

    public static long cellIndex(double v) { return (long) Math.floor(v / CELL_PC); }

    /** Stars in the cell containing galactocentric position (pc). */
    public List<PStar> cell(long ix, long iy, long iz) {
        long seed = Hash.of(universeSeed, ix, iy, iz);
        Hash.Rng rng = new Hash.Rng(seed);
        double cx = (ix + 0.5) * CELL_PC, cy = (iy + 0.5) * CELL_PC, cz = (iz + 0.5) * CELL_PC;
        double dens = MilkyWayModel.density(cx, cy, cz);
        double mean = MilkyWayModel.LOCAL_STARS_PER_PC3 * dens * CELL_PC * CELL_PC * CELL_PC;
        int count = rng.poisson(mean);
        List<PStar> out = new ArrayList<>(Math.min(count, 1 << 16));
        for (int k = 0; k < count; k++) {
            long sseed = Hash.of(seed, k, 0x5EED, 7);
            Hash.Rng r = new Hash.Rng(sseed);
            Vec3d gc = new Vec3d(ix * CELL_PC + r.nextDouble() * CELL_PC, iy * CELL_PC + r.nextDouble() * CELL_PC, iz * CELL_PC + r.nextDouble() * CELL_PC);
            Vec3d icrs = MilkyWayModel.fromGalactocentric(gc);
            double dSun = icrs.length();
            if (dSun < CATALOGUE_RADIUS_PC) continue;
            PStar s = star(sseed, r, icrs);
            double app = s.absMag() + 5 * (Math.log10(dSun) - 1);
            if (app < CATALOGUE_MAG_LIMIT && s.type() != Type.BLACK_HOLE && s.type() != Type.NEUTRON_STAR) continue;
            out.add(s);
        }
        return out;
    }

    static PStar star(long seed, Hash.Rng r, Vec3d icrs) {
        double m = kroupaMass(r.nextDouble());
        double age = r.range(0.01, 10.0);
        double tms = 10.0 * Math.pow(m, -2.5); // Gyr
        if (age < tms) {
            double L = msLuminosity(m), R = m < 1 ? Math.pow(m, 0.8) : Math.pow(m, 0.57);
            double T = 5772 * Math.pow(L / (R * R), 0.25);
            return new PStar(seed, icrs, m, L, R, T, Type.MAIN_SEQUENCE, age);
        }
        if (age < tms * 1.1) { // short giant phase
            double L = msLuminosity(m) * r.range(10, 100), T = r.range(3500, 5000);
            double R = Math.sqrt(L) / Math.pow(T / 5772, 2);
            return new PStar(seed, icrs, m, L, R, T, Type.GIANT, age);
        }
        if (m < 8) {
            double cool = age - tms * 1.1; // Gyr since formation of the white dwarf
            double T = 25000 * Math.pow(1 + cool / 0.1, -0.4);
            double R = 0.0125, L = R * R * Math.pow(T / 5772, 4);
            return new PStar(seed, icrs, 0.6, L, R, T, Type.WHITE_DWARF, age);
        }
        if (m < 25) return new PStar(seed, icrs, 1.4, 1e-6, 1.7e-5, 1e6, Type.NEUTRON_STAR, age);
        double mbh = r.range(5, 20);
        return new PStar(seed, icrs, mbh, 0, 2 * 1477 * mbh / 6.957e8, 0, Type.BLACK_HOLE, age);
    }

    /** Main-sequence mass-luminosity approximation (solar units). */
    public static double msLuminosity(double m) {
        if (m < 0.43) return 0.23 * Math.pow(m, 2.3);
        if (m < 2) return Math.pow(m, 4);
        if (m < 55) return 1.4 * Math.pow(m, 3.5);
        return 32000 * m;
    }

    /** Inverse-CDF sample of the Kroupa (2001) IMF between 0.08 and 120 M_sun (slopes 1.3 below, 2.3 above 0.5). */
    public static double kroupaMass(double u) {
        // piecewise power law dN/dm = k_i m^-a_i, continuous at 0.5
        double a1 = 1.3, a2 = 2.3, m0 = 0.08, m1 = 0.5, m2 = 120;
        double k2 = Math.pow(m1, a2 - a1); // continuity: m1^-a1 = k2 m1^-a2
        double n1 = (Math.pow(m1, 1 - a1) - Math.pow(m0, 1 - a1)) / (1 - a1);
        double n2 = k2 * (Math.pow(m2, 1 - a2) - Math.pow(m1, 1 - a2)) / (1 - a2);
        double target = u * (n1 + n2);
        if (target < n1) return Math.pow(Math.pow(m0, 1 - a1) + target * (1 - a1), 1 / (1 - a1));
        return Math.pow(Math.pow(m1, 1 - a2) + (target - n1) * (1 - a2) / k2, 1 / (1 - a2));
    }
}
