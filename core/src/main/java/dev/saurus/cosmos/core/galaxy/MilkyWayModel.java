package dev.saurus.cosmos.core.galaxy;

import dev.saurus.cosmos.core.coords.Frames;
import dev.saurus.cosmos.core.math.Vec3d;

/**
 * Smooth stellar density model of the Milky Way, used to drive deterministic procedural star generation where no
 * catalogue exists, and the volumetric galaxy renderer. Densities are relative to the solar neighbourhood (=1).
 *
 * <p>Measured / literature parameters (ESTIMATE): R0 = 8277 pc (GRAVITY Collaboration 2022); Sun 20.8 pc above the
 * midplane (Bennett & Bovy 2019); thin disc scale length 2.6 kpc and height 300 pc, thick disc 2.0 kpc / 900 pc with
 * 12% of the local density, bar angle ~27 deg (review: Bland-Hawthorn & Gerhard 2016, ARA&A 54, 529).
 * PROCEDURAL approximations: four logarithmic spiral arms (pitch 12 deg, amplitude 0.35), bulge/bar profile shape,
 * stellar halo normalisation. These shape the look of the galaxy but are not a map of real arms.
 */
public final class MilkyWayModel {
    public static final double R0_PC = 8277, Z_SUN_PC = 20.8;
    public static final double THIN_HR = 2600, THIN_HZ = 300, THICK_HR = 2000, THICK_HZ = 900, THICK_FRAC = 0.12;
    public static final double BAR_ANGLE = Math.toRadians(27);
    public static final double ARM_PITCH = Math.toRadians(12), ARM_AMPLITUDE = 0.35;
    public static final int ARMS = 4;
    public static final double DISK_EDGE_PC = 25_000;
    /** Local stellar number density (stars of >= 0.08 M_sun per pc^3), solar neighbourhood census order of magnitude. */
    public static final double LOCAL_STARS_PER_PC3 = 0.1;

    /** Heliocentric ICRS parsecs -> galactocentric Cartesian parsecs (X from GC towards the Sun's side, Z to NGP). */
    public static Vec3d toGalactocentric(Vec3d icrsPc) {
        Vec3d g = Frames.EQUATORIAL_TO_GALACTIC.apply(icrsPc); // x towards GC, y towards l=90, z towards NGP
        return new Vec3d(R0_PC - g.x(), -g.y(), g.z() + Z_SUN_PC);
    }
    public static Vec3d fromGalactocentric(Vec3d gc) {
        Vec3d g = new Vec3d(R0_PC - gc.x(), -gc.y(), gc.z() - Z_SUN_PC);
        return Frames.GALACTIC_TO_EQUATORIAL.apply(g);
    }

    /** Relative stellar density at a galactocentric position (pc). */
    public static double density(double x, double y, double z) {
        double R = Math.hypot(x, y), az = Math.abs(z);
        double norm = Math.exp(-Z_SUN_PC / THIN_HZ) + THICK_FRAC * Math.exp(-Z_SUN_PC / THICK_HZ);
        double thin = Math.exp(-(R - R0_PC) / THIN_HR - az / THIN_HZ);
        double thick = THICK_FRAC * Math.exp(-(R - R0_PC) / THICK_HR - az / THICK_HZ);
        double disk = (thin * armFactor(x, y, R) + thick) / norm;
        if (R > DISK_EDGE_PC) disk *= Math.exp(-(R - DISK_EDGE_PC) / 1500);
        // bar / bulge: rotated triaxial profile
        double c = Math.cos(BAR_ANGLE), s = Math.sin(BAR_ANGLE);
        double bx = x * c + y * s, by = -x * s + y * c;
        double rb = Math.sqrt(sq(bx / 1.0) + sq(by / 0.4) + sq(z / 0.3));
        double bulge = 120.0 * Math.exp(-Math.pow(rb / 750.0, 1.6));
        double r = Math.sqrt(R * R + z * z);
        double halo = 0.002 * Math.pow(Math.max(r, 500) / R0_PC, -3.0);
        return disk + bulge + halo;
    }

    /** Spiral-arm modulation (>= 1-A, <= 1+A), fades inside the bar. */
    public static double armFactor(double x, double y, double R) {
        if (R < 1) return 1;
        double theta = Math.atan2(y, x);
        double phase = ARMS * (theta - Math.log(R / 3000.0) / Math.tan(ARM_PITCH));
        double barFade = Math.min(1, Math.max(0, (R - 3000) / 2000));
        return 1 + ARM_AMPLITUDE * barFade * Math.cos(phase);
    }

    public static double densityAtIcrs(Vec3d icrsPc) { Vec3d g = toGalactocentric(icrsPc); return density(g.x(), g.y(), g.z()); }
    private static double sq(double v) { return v * v; }
}
