package dev.saurus.cosmos.core.physics;

import dev.saurus.cosmos.core.body.Body;
import dev.saurus.cosmos.core.math.Vec3d;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Spacecraft dynamics inside a star system: Newtonian gravity summed over every body of the system (not patched conics),
 * engine thrust, and atmospheric drag with an exponential atmosphere. State is system-centred ICRS metres, which keeps
 * ~1 mm precision out to ~60 AU; outside systems the craft hands over to interstellar mode (straight line + warp).
 *
 * <p>Integrator: adaptive-substep RK4. The substep is limited so that the craft never moves more than 0.2% of its
 * distance to the nearest body per step, which keeps low orbits stable at high time-warp.
 */
public final class FlightModel {
    public record State(Vec3d pos, Vec3d vel, double massKg) {}

    public interface Bodies { List<Body> bodies(); Vec3d position(Body b, double t); Vec3d velocity(Body b, double t); }

    public final Bodies system;
    public double dragArea = 10.0, dragCd = 1.2;

    public FlightModel(Bodies system) { this.system = system; }

    /** Gravitational acceleration at position p (system frame) and time t. */
    public Vec3d gravity(Vec3d p, double t) {
        Vec3d a = Vec3d.ZERO;
        for (Body b : system.bodies()) {
            if (b.gm <= 0) continue;
            Vec3d d = system.position(b, t).sub(p);
            double r2 = d.lengthSq();
            double r = Math.sqrt(r2);
            double rr = Math.max(r, b.meanRadius() * 0.5); // never singular, even inside a body
            a = a.addScaled(d, b.gm / (rr * rr * r));
        }
        return a;
    }

    /** Body with the strongest gravitational pull at p (the "dominant" body for navigation and LOD). */
    public Body dominant(Vec3d p, double t) {
        Body best = null; double bestA = -1;
        for (Body b : system.bodies()) {
            double r = system.position(b, t).distance(p);
            double a = b.gm / (r * r);
            if (a > bestA) { bestA = a; best = b; }
        }
        return best;
    }

    /** Air density (kg/m^3) at altitude h above body b, from surface pressure and scale height (ideal gas, isothermal). */
    public static double airDensity(Body b, double altitude) {
        if (b.atmosphere == null || altitude > b.atmosphere.topM() || Double.isNaN(b.atmosphere.surfacePressureBar())) return 0;
        double g = b.gm / (b.meanRadius() * b.meanRadius());
        double H = b.atmosphere.rayleighH();
        double rho0 = b.atmosphere.surfacePressureBar() * 1e5 / (g * H); // hydrostatic: P0 = rho0 g H
        return rho0 * Math.exp(-Math.max(0, altitude) / H);
    }

    /** Advance the craft by dt seconds with thrust (N, system frame) supplied as a function of (state, t). */
    public State step(State s, double t, double dt, BiFunction<State, Double, Vec3d> thrust) {
        Body dom = dominant(s.pos(), t);
        double rDom = system.position(dom, t).distance(s.pos());
        double speed = Math.max(s.vel().length(), 1);
        double maxStep = Math.max(1e-3, 0.002 * rDom / speed);
        int n = (int) Math.min(100_000, Math.ceil(Math.abs(dt) / maxStep));
        double h = dt / Math.max(n, 1);
        State cur = s; double tt = t;
        for (int i = 0; i < Math.max(n, 1); i++) { cur = rk4(cur, tt, h, thrust, dom); tt += h; }
        return cur;
    }

    private State rk4(State s, double t, double h, BiFunction<State, Double, Vec3d> thrust, Body dom) {
        Vec3d[] k1 = deriv(s, t, thrust, dom);
        State s2 = new State(s.pos().addScaled(k1[0], h / 2), s.vel().addScaled(k1[1], h / 2), s.massKg());
        Vec3d[] k2 = deriv(s2, t + h / 2, thrust, dom);
        State s3 = new State(s.pos().addScaled(k2[0], h / 2), s.vel().addScaled(k2[1], h / 2), s.massKg());
        Vec3d[] k3 = deriv(s3, t + h / 2, thrust, dom);
        State s4 = new State(s.pos().addScaled(k3[0], h), s.vel().addScaled(k3[1], h), s.massKg());
        Vec3d[] k4 = deriv(s4, t + h, thrust, dom);
        Vec3d dp = k1[0].add(k2[0].mul(2)).add(k3[0].mul(2)).add(k4[0]).mul(h / 6);
        Vec3d dv = k1[1].add(k2[1].mul(2)).add(k3[1].mul(2)).add(k4[1]).mul(h / 6);
        return new State(s.pos().add(dp), s.vel().add(dv), s.massKg());
    }

    private Vec3d[] deriv(State s, double t, BiFunction<State, Double, Vec3d> thrust, Body dom) {
        Vec3d a = gravity(s.pos(), t);
        if (thrust != null) a = a.add(thrust.apply(s, t).mul(1 / s.massKg()));
        if (dom.atmosphere != null) {
            Vec3d rel = s.pos().sub(system.position(dom, t));
            double alt = rel.length() - dom.meanRadius();
            double rho = airDensity(dom, alt);
            if (rho > 0) {
                // atmosphere co-rotates with the body
                Vec3d omega = dom.rotation.spinAxis(t).mul(2 * Math.PI / Math.abs(dom.rotation.siderealPeriodSeconds()));
                Vec3d vAir = system.velocity(dom, t).add(omega.cross(rel));
                Vec3d vRel = s.vel().sub(vAir);
                double vr = vRel.length();
                a = a.add(vRel.mul(-0.5 * rho * vr * dragCd * dragArea / s.massKg()));
            }
        }
        return new Vec3d[]{s.vel(), a};
    }

    /** Sutton-Graves stagnation-point convective heat flux (W/m^2) for Earth-like air, nose radius rn (m). */
    public static double heatFlux(double rho, double v, double rn) { return 1.7415e-4 * Math.sqrt(rho / rn) * v * v * v; }
}
