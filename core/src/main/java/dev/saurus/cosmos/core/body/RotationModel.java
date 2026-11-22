package dev.saurus.cosmos.core.body;

import dev.saurus.cosmos.core.coords.Frames;
import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.math.Mat3;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.time.SimTime;

/**
 * IAU WGCCRE rotation model: alpha0 = a0 + a1*T, delta0 = d0 + d1*T, W = w0 + w1*d + w2*d^2
 * (T in Julian centuries, d in days, both from J2000 TDB). Periodic nutation/precession terms are not applied
 * (they change the Moon's orientation by up to ~0.1 deg, Neptune's pole by up to ~0.7 deg).
 */
public record RotationModel(double[] ra, double[] dec, double[] pm) {

    public static RotationModel fixedPeriod(Vec3d pole, double periodSeconds, double w0deg) {
        double ra = Math.toDegrees(Math.atan2(pole.y(), pole.x())), dec = Math.toDegrees(Math.asin(pole.z()));
        return new RotationModel(new double[]{ra, 0, 0}, new double[]{dec, 0, 0}, new double[]{w0deg, 360.0 / (periodSeconds / SimTime.DAY), 0});
    }

    public double poleRa(double tSeconds) { double T = tSeconds / SimTime.DAY / SimTime.JULIAN_CENTURY_DAYS; return (ra[0] + ra[1] * T) * Units.DEG; }
    public double poleDec(double tSeconds) { double T = tSeconds / SimTime.DAY / SimTime.JULIAN_CENTURY_DAYS; return (dec[0] + dec[1] * T) * Units.DEG; }
    public double primeMeridian(double tSeconds) {
        double d = tSeconds / SimTime.DAY;
        double w = pm[0] + pm[1] * d + (pm.length > 2 ? pm[2] * d * d : 0);
        return Math.toRadians(w % 360.0);
    }

    /** Body-fixed -> ICRS rotation at time t. */
    public Mat3 orientation(double tSeconds) { return Frames.bodyFixed(poleRa(tSeconds), poleDec(tSeconds), primeMeridian(tSeconds)); }
    public Vec3d pole(double tSeconds) { return Vec3d.fromRaDec(poleRa(tSeconds), poleDec(tSeconds)); }

    /**
     * Direction of the rotational angular momentum. Equals the IAU north pole when W increases, and is opposite
     * to it for retrograde rotators (Venus, Uranus) whose IAU "north" pole is defined by the invariable plane.
     */
    public Vec3d spinAxis(double tSeconds) { Vec3d p = pole(tSeconds); return pm[1] < 0 ? p.negate() : p; }

    /** Sidereal rotation period in seconds (negative = retrograde in the IAU sense). */
    public double siderealPeriodSeconds() { return 360.0 / pm[1] * SimTime.DAY; }
}
