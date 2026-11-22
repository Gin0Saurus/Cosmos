package dev.saurus.cosmos.core.blackhole;

import dev.saurus.cosmos.core.coords.UniversePos;
import dev.saurus.cosmos.core.coords.Units;

/**
 * A black hole with its physical scales. Geometric units: M = GM/c^2 (gravitational radius).
 * Spin a* in [0,1); for Kerr holes the horizon and ISCO follow Bardeen, Press & Teukolsky (1972).
 */
public record BlackHole(String name, double massSun, double spin, UniversePos position, String source, java.util.List<String> aliases) {

    public double gravitationalRadiusM() { return Units.G * massSun * Units.M_SUN / (Units.C * Units.C); }
    /** Schwarzschild radius 2GM/c^2 (event horizon of a non-rotating hole). */
    public double schwarzschildRadiusM() { return 2 * gravitationalRadiusM(); }
    /** Outer horizon r+ = M (1 + sqrt(1 - a^2)). */
    public double horizonRadiusM() { double a = spin(); return gravitationalRadiusM() * (1 + Math.sqrt(1 - a * a)); }
    /** Photon sphere (equatorial prograde orbit for Kerr; 3M for Schwarzschild). */
    public double photonSphereM() {
        double a = spin();
        return 2 * gravitationalRadiusM() * (1 + Math.cos(2.0 / 3.0 * Math.acos(-a)));
    }
    /** Prograde innermost stable circular orbit (BPT 1972). */
    public double iscoM() { return gravitationalRadiusM() * iscoInM(spin()); }
    public static double iscoInM(double a) {
        double z1 = 1 + Math.cbrt(1 - a * a) * (Math.cbrt(1 + a) + Math.cbrt(1 - a));
        double z2 = Math.sqrt(3 * a * a + z1 * z1);
        return 3 + z2 - Math.signum(a) * Math.sqrt((3 - z1) * (3 + z1 + 2 * z2));
    }
    /** Shadow (critical impact parameter) radius, Schwarzschild: 3*sqrt(3) M. */
    public double shadowRadiusM() { return 3 * Math.sqrt(3) * gravitationalRadiusM(); }
}
