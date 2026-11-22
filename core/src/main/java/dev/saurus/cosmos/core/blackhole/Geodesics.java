package dev.saurus.cosmos.core.blackhole;

import dev.saurus.cosmos.core.math.Vec3d;

/**
 * Null geodesics in Schwarzschild spacetime, CPU reference implementation (the GLSL shader uses the same equations;
 * this class is the ground truth the shader is tested against).
 *
 * <p>In geometric units (G = c = M = 1) a photon's spatial path obeys, in Cartesian-like coordinates with r = |x|,
 * x'' = -3 M h^2 x / r^5 with h = |x cross x'| constant, which is exactly the photon orbit equation
 * u'' + u = 3 M u^2 (u = 1/r). (With r_s = 1 units this is often written -(3/2) h^2 x / r^5.) RK4 integration.
 */
public final class Geodesics {
    private Geodesics() {}

    public record Result(boolean captured, Vec3d finalDirection, int steps, double minRadius) {}

    /** Trace from position x0 along direction d (|d|=1), M = 1 units, until captured (r < 2) or escaped (r > rEscape). */
    public static Result trace(Vec3d x0, Vec3d d, double rEscape, double baseStep) {
        Vec3d x = x0, v = d.normalize();
        double h2 = x.cross(v).lengthSq();
        double rmin = x.length();
        for (int i = 0; i < 200_000; i++) {
            double r = x.length();
            rmin = Math.min(rmin, r);
            if (r < 2.0 + 1e-9) return new Result(true, v, i, rmin);
            if (r > rEscape && x.dot(v) > 0) return new Result(false, v, i, rmin);
            double dt = baseStep * Math.max(0.02, Math.min(1.0, (r - 1.9) * 0.15)) * Math.max(1, r / 20);
            Vec3d k1x = v, k1v = acc(x, h2);
            Vec3d k2x = v.addScaled(k1v, dt / 2), k2v = acc(x.addScaled(k1x, dt / 2), h2);
            Vec3d k3x = v.addScaled(k2v, dt / 2), k3v = acc(x.addScaled(k2x, dt / 2), h2);
            Vec3d k4x = v.addScaled(k3v, dt), k4v = acc(x.addScaled(k3x, dt), h2);
            x = x.add(k1x.add(k2x.mul(2)).add(k3x.mul(2)).add(k4x).mul(dt / 6));
            v = v.add(k1v.add(k2v.mul(2)).add(k3v.mul(2)).add(k4v).mul(dt / 6));
        }
        return new Result(false, v, -1, rmin);
    }

    static Vec3d acc(Vec3d x, double h2) {
        double r2 = x.lengthSq(), r5 = r2 * r2 * Math.sqrt(r2);
        return x.mul(-3.0 * h2 / r5);
    }

    /** Total deflection angle (rad) of a ray coming from infinity with impact parameter b (units of M). */
    public static double deflection(double b) {
        double start = 400 * Math.max(b, 10); // far enough that the asymptotic deflection is captured to <0.1%
        Vec3d x0 = new Vec3d(-start, b, 0), d = Vec3d.X;
        Result r = trace(x0, d, start + 1, 0.5);
        if (r.captured()) return Double.NaN;
        return Math.acos(Math.max(-1, Math.min(1, r.finalDirection().dot(d))));
    }

    /**
     * Redshift factor g = nu_obs / nu_emit for light from a thin disc in circular Keplerian (geodesic) orbit at radius r
     * in Schwarzschild spacetime, reaching a distant observer: 1+z = u^t (1 - Omega * L_z) with u^t = 1/sqrt(1 - 3/r),
     * Omega = r^-3/2, and L_z the photon's specific angular momentum about the disc axis (sign: positive = co-rotating).
     * Combines gravitational redshift, transverse Doppler and line-of-sight Doppler (relativistic beaming uses g^4).
     */
    public static double discRedshiftFactor(double r, double lz) {
        if (r <= 3) return 0;
        double ut = 1 / Math.sqrt(1 - 3 / r), omega = Math.pow(r, -1.5);
        return 1 / (ut * (1 - omega * lz));
    }
}
