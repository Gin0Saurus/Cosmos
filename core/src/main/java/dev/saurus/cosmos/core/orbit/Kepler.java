package dev.saurus.cosmos.core.orbit;

import dev.saurus.cosmos.core.math.Vec3d;

/** Two-body orbital mechanics: Kepler's equation, elements <-> state vectors. Angles in radians. */
public final class Kepler {
    private Kepler() {}

    /** Solve M = E - e sin E (elliptic) to 1e-14 rad. */
    public static double eccentricAnomaly(double M, double e) {
        M = Math.IEEEremainder(M, 2 * Math.PI);
        double E = e < 0.8 ? M + e * Math.sin(M) : Math.PI * Math.signum(M == 0 ? 1 : M);
        for (int i = 0; i < 60; i++) {
            double f = E - e * Math.sin(E) - M, fp = 1 - e * Math.cos(E);
            double dE = f / fp;
            E -= dE;
            if (Math.abs(dE) < 1e-15) break;
        }
        return E;
    }

    /** Solve M = e sinh H - H (hyperbolic). */
    public static double hyperbolicAnomaly(double M, double e) {
        double H = Math.log(2 * Math.abs(M) / e + 1.8) * Math.signum(M);
        for (int i = 0; i < 100; i++) {
            double f = e * Math.sinh(H) - H - M, fp = e * Math.cosh(H) - 1;
            double dH = f / fp; H -= dH;
            if (Math.abs(dH) < 1e-15 * Math.max(1, Math.abs(H))) break;
        }
        return H;
    }

    /** Classical elements. a in metres (negative for hyperbolic), angles radians, meanAnomaly at the evaluation time. */
    public record Elements(double a, double e, double i, double node, double argPeri, double meanAnomaly) {}

    /** Position in the reference plane frame (x towards ascending-node reference direction) for the given elements. */
    public static Vec3d position(Elements el) {
        double a = el.a(), e = el.e();
        double xw, yw;
        if (e < 1) {
            double E = eccentricAnomaly(el.meanAnomaly(), e);
            xw = a * (Math.cos(E) - e);
            yw = a * Math.sqrt(1 - e * e) * Math.sin(E);
        } else {
            double H = hyperbolicAnomaly(el.meanAnomaly(), e);
            xw = a * (Math.cosh(H) - e);              // a < 0 for hyperbola
            yw = -a * Math.sqrt(e * e - 1) * Math.sinh(H);
        }
        return perifocalToReference(xw, yw, el);
    }

    public static Vec3d velocity(Elements el, double mu) {
        double a = el.a(), e = el.e(), vx, vy;
        if (e < 1) {
            double E = eccentricAnomaly(el.meanAnomaly(), e);
            double n = Math.sqrt(mu / (a * a * a)), d = 1 - e * Math.cos(E);
            vx = -a * n * Math.sin(E) / d;
            vy = a * n * Math.sqrt(1 - e * e) * Math.cos(E) / d;
        } else {
            double H = hyperbolicAnomaly(el.meanAnomaly(), e);
            // x = a(cosh H - e), y = -a sqrt(e^2-1) sinh H, dH/dt = n / (e cosh H - 1), a < 0
            double dHdt = Math.sqrt(mu / (-a * a * a)) / (e * Math.cosh(H) - 1);
            vx = a * Math.sinh(H) * dHdt;
            vy = -a * Math.sqrt(e * e - 1) * Math.cosh(H) * dHdt;
        }
        return perifocalToReference(vx, vy, el);
    }

    private static Vec3d perifocalToReference(double xw, double yw, Elements el) {
        double cO = Math.cos(el.node()), sO = Math.sin(el.node());
        double cw = Math.cos(el.argPeri()), sw = Math.sin(el.argPeri());
        double ci = Math.cos(el.i()), si = Math.sin(el.i());
        double x = (cw * cO - sw * sO * ci) * xw + (-sw * cO - cw * sO * ci) * yw;
        double y = (cw * sO + sw * cO * ci) * xw + (-sw * sO + cw * cO * ci) * yw;
        double z = (sw * si) * xw + (cw * si) * yw;
        return new Vec3d(x, y, z);
    }

    /** Osculating elements from a state vector (used by the navigation UI to show the craft's current orbit). */
    public static Elements fromState(Vec3d r, Vec3d v, double mu) {
        Vec3d h = r.cross(v);
        double rm = r.length(), vm2 = v.lengthSq();
        Vec3d evec = v.cross(h).mul(1 / mu).sub(r.mul(1 / rm));
        double e = evec.length();
        double energy = vm2 / 2 - mu / rm;
        double a = -mu / (2 * energy);
        double i = Math.acos(clamp(h.z() / h.length()));
        Vec3d nvec = new Vec3d(-h.y(), h.x(), 0);
        double nm = nvec.length();
        double node = nm < 1e-12 ? 0 : Math.atan2(nvec.y(), nvec.x());
        double argp;
        if (e < 1e-11) argp = 0;
        else if (nm < 1e-12) argp = Math.atan2(evec.y(), evec.x()) * (h.z() >= 0 ? 1 : -1);
        else { argp = Math.acos(clamp(nvec.dot(evec) / (nm * e))); if (evec.z() < 0) argp = 2 * Math.PI - argp; }
        // true anomaly -> mean anomaly
        double nu;
        if (e < 1e-11) {
            Vec3d ref = nm < 1e-12 ? Vec3d.X : nvec.mul(1 / nm);
            nu = Math.acos(clamp(ref.dot(r) / rm)); if (r.dot(h.cross(ref)) < 0) nu = 2 * Math.PI - nu;
        } else { nu = Math.acos(clamp(evec.dot(r) / (e * rm))); if (r.dot(v) < 0) nu = 2 * Math.PI - nu; }
        double M;
        if (e < 1) {
            double E = 2 * Math.atan2(Math.sqrt(1 - e) * Math.sin(nu / 2), Math.sqrt(1 + e) * Math.cos(nu / 2));
            M = E - e * Math.sin(E);
        } else {
            double H = 2 * atanh(Math.sqrt((e - 1) / (e + 1)) * Math.tan(nu / 2));
            M = e * Math.sinh(H) - H;
        }
        return new Elements(a, e, i, node, argp, M);
    }

    private static double clamp(double x) { return Math.max(-1, Math.min(1, x)); }
    private static double atanh(double x) { return 0.5 * Math.log((1 + x) / (1 - x)); }
}
