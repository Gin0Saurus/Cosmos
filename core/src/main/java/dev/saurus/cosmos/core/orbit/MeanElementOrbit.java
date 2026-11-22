package dev.saurus.cosmos.core.orbit;

import dev.saurus.cosmos.core.coords.Frames;
import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.math.Mat3;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.time.SimTime;

/**
 * Satellite orbit from JPL "Planetary Satellite Mean Elements" (epoch 2000-01-01.5 TDB):
 * a, e, omega (arg. of periapsis), M, i, node, P (sidereal period, days), Papsis and Pnode (precession periods of
 * omega and of the node, years). The elements are referred to one of three planes:
 * <ul>
 *   <li>ECLIPTIC: J2000 ecliptic, node from the equinox (the Moon);</li>
 *   <li>LAPLACE: the satellite's local Laplace plane, given by its pole (RA, Dec); node measured from the ascending
 *       node of that plane on the ICRF equator;</li>
 *   <li>EQUATORIAL: the planet's equator; node from the ascending node of the equator on the ICRF equator.</li>
 * </ul>
 * Precession: node regresses (prograde orbits) at 360/Pnode deg/yr, omega advances at 360/Papsis deg/yr, and the
 * mean anomaly advances at the anomalistic rate 360/P - d(varpi)/dt. For the Moon this reproduces the 27.5546 d
 * anomalistic month. Mean elements omit short-period perturbations (the Moon's evection alone is ~1.27 deg), so
 * these are approximate positions; JPL states they are not for precise ephemeris work.
 */
public final class MeanElementOrbit implements OrbitModel {
    public enum Plane { ECLIPTIC, LAPLACE, EQUATORIAL }

    private final double a, e, w0, M0, i, node0, periodDays, wRate, nodeRate, mRate;
    private final Mat3 planeToIcrs;
    /** Phase (rad) and mean-motion (rad/day) corrections fitted to JPL Horizons; zero when uncalibrated. */
    private double calLon0, calRate;
    private String calibration = "uncalibrated";

    public MeanElementOrbit(double aKm, double e, double wDeg, double mDeg, double iDeg, double nodeDeg,
                            double periodDays, double papsisYears, double pnodeYears, Plane plane, Vec3d planePole) {
        this.a = aKm * Units.KM; this.e = e;
        this.w0 = Math.toRadians(wDeg); this.M0 = Math.toRadians(mDeg); this.i = Math.toRadians(iDeg);
        this.node0 = Math.toRadians(nodeDeg); this.periodDays = periodDays;
        double perDay = 1.0 / 365.25;
        double nodeSign = Math.cos(this.i) >= 0 ? -1 : 1; // J2 nodal precession ~ -cos(i)
        this.nodeRate = pnodeYears > 0 ? nodeSign * Math.toRadians(360.0 / pnodeYears * perDay) : 0; // rad/day
        this.wRate = papsisYears > 0 ? Math.toRadians(360.0 / papsisYears * perDay) : 0;
        double meanLongRate = Math.toRadians(360.0 / periodDays);
        this.mRate = meanLongRate - (wRate + nodeRate);
        this.planeToIcrs = switch (plane) {
            case ECLIPTIC -> Frames.ECLIPTIC_TO_EQUATORIAL;
            case LAPLACE, EQUATORIAL -> planeFromPole(planePole);
        };
    }

    /** Frame whose z is the plane pole and x is the ascending node of the plane on the ICRF equator. */
    public static Mat3 planeFromPole(Vec3d pole) {
        Vec3d z = pole.normalize();
        Vec3d x = Vec3d.Z.cross(z);
        if (x.length() < 1e-12) x = Vec3d.X; else x = x.normalize();
        Vec3d y = z.cross(x);
        return Mat3.fromColumns(x, y, z);
    }

    @Override public Vec3d position(double t) {
        double d = t / SimTime.DAY;
        Kepler.Elements el = new Kepler.Elements(a, e, i, node0 + nodeRate * d, w0 + wRate * d, M0 + calLon0 + (mRate + calRate) * d);
        return planeToIcrs.apply(Kepler.position(el));
    }

    @Override public double period() {
        double lonRate = mRate + calRate + wRate + nodeRate; // rad/day of mean longitude
        return 2 * Math.PI / lonRate * SimTime.DAY;
    }

    public String calibration() { return calibration; }

    /**
     * Fit a constant phase offset and a mean-motion correction so the orbit passes through two reference
     * positions (JPL Horizons). The whole-revolution ambiguity over the long baseline is resolved with a prior
     * mean-motion (for synchronous moons the IAU rotation rate, which equals the mean orbital rate).
     */
    public void calibrate(double t0, Vec3d ref0, double t1, Vec3d ref1, double priorLonRateRadPerDay, String label) {
        calLon0 = 0; calRate = 0;
        double d0 = t0 / SimTime.DAY, d1 = t1 / SimTime.DAY;
        double e0 = signedAngle(t0, ref0), e1 = signedAngle(t1, ref1);
        double modelRate = mRate + wRate + nodeRate;
        double best = Double.MAX_VALUE, bestRate = 0;
        for (int k = -2000; k <= 2000; k++) {
            double dr = (e1 - e0 + 2 * Math.PI * k) / (d1 - d0);
            double err = Math.abs(modelRate + dr - priorLonRateRadPerDay);
            if (err < best) { best = err; bestRate = dr; }
        }
        calRate = bestRate;
        calLon0 = e0 - calRate * d0;
        calibration = label;
    }

    /** Angle (rad) from the model position to the reference position, measured about the model orbit normal. */
    public double signedAngle(double t, Vec3d ref) {
        Vec3d r = position(t), v = velocity(t);
        Vec3d n = r.cross(v).normalize();
        Vec3d rp = ref.sub(n.mul(ref.dot(n)));
        return Math.atan2(r.cross(rp).dot(n), r.dot(rp));
    }
}
