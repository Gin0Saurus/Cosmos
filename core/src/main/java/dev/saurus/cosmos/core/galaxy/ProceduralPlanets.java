package dev.saurus.cosmos.core.galaxy;

import dev.saurus.cosmos.core.body.Body;
import dev.saurus.cosmos.core.body.Provenance;
import dev.saurus.cosmos.core.body.RotationModel;
import dev.saurus.cosmos.core.catalog.ExoplanetCatalog;
import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.orbit.Kepler;
import dev.saurus.cosmos.core.orbit.OrbitModel;
import dev.saurus.cosmos.core.util.Hash;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic planetary systems for procedural stars (and for real stars with no known planets, when the player
 * opts in). Occurrence is loosely informed by Kepler/RV statistics (small planets common, more around M dwarfs; giant
 * planets ~10% of FGK stars); spacing follows roughly constant period ratios. Everything here is PROCEDURAL.
 */
public final class ProceduralPlanets {
    private ProceduralPlanets() {}

    public static List<Body> generate(long starSeed, Body star, double massSun, double lumSun) {
        Hash.Rng r = new Hash.Rng(Hash.of(starSeed, 0x91A, 3, 5));
        double lambda = massSun < 0.6 ? 2.5 : 1.6;
        int n = Math.min(r.poisson(lambda), 9);
        List<Body> out = new ArrayList<>();
        double a = Math.pow(10, r.range(-1.7, -0.9)) * Math.cbrt(massSun);
        double mu = massSun * Units.GM_SUN;
        double hz = Math.sqrt(Math.max(lumSun, 1e-6)); // AU of Earth-equivalent insolation
        Vec3d pole = new Vec3d(r.gaussian(), r.gaussian(), r.gaussian()).normalize();
        for (int i = 0; i < n; i++) {
            double mEarth = Math.pow(10, r.range(-1, 1.3));
            boolean giant = r.nextDouble() < (massSun > 0.6 ? 0.12 : 0.03) && a > 0.5 * hz;
            if (giant) mEarth = Math.pow(10, r.range(1.8, 3.6));
            double rEarth = ExoplanetCatalog.radiusFromMass(mEarth);
            double e = Math.min(0.6, Math.abs(r.gaussian()) * 0.06);
            double inc = Math.toRadians(Math.abs(r.gaussian()) * 2);
            final double aa = a * Units.AU, ee = e, ii = inc, node = r.range(0, 2 * Math.PI), w = r.range(0, 2 * Math.PI), m0 = r.range(0, 2 * Math.PI);
            final double nmot = Math.sqrt(mu / (aa * aa * aa));
            final var frame = dev.saurus.cosmos.core.orbit.MeanElementOrbit.planeFromPole(pole);
            OrbitModel orbit = new OrbitModel() {
                @Override public Vec3d position(double t) { return frame.apply(Kepler.position(new Kepler.Elements(aa, ee, ii, node, w, m0 + nmot * t))); }
                @Override public double period() { return 2 * Math.PI / nmot; }
            };
            Body.Kind kind = rEarth < 1.6 ? Body.Kind.ROCKY : rEarth < 6 ? Body.Kind.ICE_GIANT : Body.Kind.GAS_GIANT;
            double rM = rEarth * Units.EARTH_RADIUS;
            boolean locked = a < 0.1;
            RotationModel rot = RotationModel.fixedPeriod(pole, locked ? orbit.period() : r.range(8, 60) * 3600, r.range(0, 360));
            String name = star.name + " " + (char) ('b' + i);
            Body b = new Body("proc:" + name, name, kind, star, mEarth * Units.EARTH_MASS * Units.G, new double[]{rM, rM, rM},
                    orbit, rot, null, null, 0, r.range(0.1, 0.6));
            b.fact("Origin", "procedurally generated (deterministic)", Provenance.PROCEDURAL, "seed " + Long.toHexString(starSeed));
            out.add(b);
            a *= Math.pow(r.range(1.4, 2.6), 2.0 / 3.0) * (giant ? 1.3 : 1.0); // period ratio 1.4-2.6
        }
        return out;
    }
}
