package dev.saurus.cosmos.core.orbit;

import dev.saurus.cosmos.core.math.Vec3d;

/** Position of a body relative to its parent, ICRS axes, metres, at TDB seconds since J2000. */
public interface OrbitModel {
    Vec3d position(double t);

    /** Central-difference velocity (m/s). Implementations with analytic velocity may override. */
    default Vec3d velocity(double t) {
        double h = 30.0;
        return position(t + h).sub(position(t - h)).mul(1.0 / (2 * h));
    }

    /** Orbital period in seconds if periodic, else NaN. */
    default double period() { return Double.NaN; }

    /** Points along the current osculating ellipse, for orbit-line rendering. */
    default Vec3d[] path(double t, int n) {
        double p = period();
        Vec3d[] pts = new Vec3d[n];
        if (Double.isNaN(p)) { for (int i = 0; i < n; i++) pts[i] = position(t); return pts; }
        for (int i = 0; i < n; i++) pts[i] = position(t + p * i / (n - 1));
        return pts;
    }
}
