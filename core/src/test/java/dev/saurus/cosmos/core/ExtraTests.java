package dev.saurus.cosmos.core;

import dev.saurus.cosmos.core.blackhole.BlackHole;
import dev.saurus.cosmos.core.blackhole.Geodesics;
import dev.saurus.cosmos.core.body.Body;
import dev.saurus.cosmos.core.body.SolarSystem;
import dev.saurus.cosmos.core.catalog.StarColor;
import dev.saurus.cosmos.core.catalog.StarSystem;
import dev.saurus.cosmos.core.coords.UniversePos;
import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.galaxy.MilkyWayModel;
import dev.saurus.cosmos.core.galaxy.ProceduralStars;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.nav.ObjectIndex;
import dev.saurus.cosmos.core.nav.TravelPlanner;
import dev.saurus.cosmos.core.physics.FlightModel;
import dev.saurus.cosmos.core.time.SimTime;

import java.util.List;

final class ExtraTests {
    static void run(SolarSystem ignored) throws Exception {
        long t0 = System.nanoTime();
        Universe u = Universe.load();
        Check.info(String.format("Universe loaded in %.2f s: %,d stars, %,d exoplanets in %,d systems, %,d deep-sky objects, %,d searchable names",
                (System.nanoTime() - t0) / 1e9, u.stars.size(), u.exoplanets.planetCount(), u.exoplanetHosts().size(), u.deepSky.all().size(), u.index.size()));

        System.out.println("[Search]");
        expect(u, "Sirius", "Sirius", ObjectIndex.Kind.STAR);
        expect(u, "Proxima Centauri", "Proxima Centauri", ObjectIndex.Kind.STAR);
        expect(u, "Sagittarius A*", "Sagittarius A*", ObjectIndex.Kind.BLACK_HOLE);
        expect(u, "Sgr A*", "Sagittarius A*", ObjectIndex.Kind.BLACK_HOLE);
        expect(u, "Andromeda", "Andromeda Galaxy", ObjectIndex.Kind.GALAXY);
        expect(u, "M31", "Andromeda Galaxy", ObjectIndex.Kind.GALAXY);
        expect(u, "TRAPPIST-1", "TRAPPIST-1", ObjectIndex.Kind.STAR);
        expect(u, "TRAPPIST-1 system", "TRAPPIST-1 system", ObjectIndex.Kind.STAR);
        expect(u, "Alpha Centauri", "Rigil Kentaurus", ObjectIndex.Kind.STAR);
        expect(u, "Betelgeuse", "Betelgeuse", ObjectIndex.Kind.STAR);
        expect(u, "Orion Nebula", "Great Orion Nebula", ObjectIndex.Kind.NEBULA);
        expect(u, "Titan", "Titan", ObjectIndex.Kind.MOON);
        expect(u, "HD 209458", "HIP 108859", ObjectIndex.Kind.STAR); // HD 209458 = HIP 108859 (Osiris host)

        System.out.println("[Distances from real data]");
        double t = (2461366.5 - SimTime.J2000_JD) * SimTime.DAY;
        double dProx = UniversePos.ORIGIN.distanceTo(first(u, "Proxima Centauri").position().apply(t)) / Units.LIGHT_YEAR;
        Check.near("Proxima Centauri distance (ly; HYG/Hipparcos 1.2959 pc)", dProx, 4.227, 0.01);
        double dSirius = UniversePos.ORIGIN.distanceTo(first(u, "Sirius").position().apply(t)) / Units.LIGHT_YEAR;
        Check.near("Sirius distance (ly)", dSirius, 8.60, 0.05);
        double dM31 = UniversePos.ORIGIN.distanceTo(first(u, "M31").position().apply(t)) / Units.LIGHT_YEAR / 1e6;
        Check.near("Andromeda distance (Mly; Li et al. 2021 761 kpc)", dM31, 2.482, 0.01);
        double dSgr = UniversePos.ORIGIN.distanceTo(first(u, "Sgr A*").position().apply(t)) / Units.PARSEC;
        Check.near("Sgr A* distance (pc)", dSgr, 8277, 1);
        Vec3d gcDir = dev.saurus.cosmos.core.coords.Frames.GALACTIC_TO_EQUATORIAL.apply(Vec3d.X);
        Vec3d sgrDir = UniversePos.ORIGIN.to(first(u, "Sgr A*").position().apply(t)).normalize();
        Check.near("Sgr A* lies at the Galactic centre direction (deg)", Math.toDegrees(Math.acos(gcDir.dot(sgrDir))), 0, 0.1);

        System.out.println("[Exoplanet systems]");
        StarSystem trap = u.systemOf("TRAPPIST-1");
        Check.that("TRAPPIST-1 has 7 planets", trap != null && trap.planets().size() == 7);
        Body trapE = trap.planets().stream().filter(b -> b.name.equals("TRAPPIST-1 e")).findFirst().orElseThrow();
        Check.rel("TRAPPIST-1 e period (d)", trapE.orbit.period() / SimTime.DAY, 6.099570, 0.003);
        StarSystem prox = u.systemOf("Proxima Centauri");
        Check.that("Proxima system has catalogued planets", prox != null && prox.planets().size() >= 2);
        Body proxB = prox.planets().stream().filter(b -> b.name.equals("Proxima Centauri b")).findFirst().orElseThrow();
        Check.rel("Proxima b semi-major axis (AU)", proxB.orbit.position(0).length() / Units.AU, 0.04857, 0.001);
        // transiting planet must actually transit as seen from Earth: at some phase it lies between star and Sun
        Vec3d toSun = u.stars.positionPc(trap.starIndex(), 0).normalize().negate();
        double best = -1;
        for (int k = 0; k < 400; k++) { Vec3d p = trapE.orbit.position(k * trapE.orbit.period() / 400).normalize(); best = Math.max(best, p.dot(toSun)); }
        Check.that(String.format("TRAPPIST-1 e transits from Earth's viewpoint (max cos = %.5f)", best), best > 0.9999);

        System.out.println("[Star colours]");
        Check.near("Sun B-V 0.656 -> Teff (K)", StarColor.teffFromBV(0.656), 5772, 150);
        float[] sirius = StarColor.rgbFromTemperature(9940), betel = StarColor.rgbFromTemperature(3600);
        Check.that("Sirius renders blue-white (b >= r)", sirius[2] >= sirius[0]);
        Check.that("Betelgeuse renders orange-red (r > b)", betel[0] > betel[2] * 1.5);

        System.out.println("[Black hole physics]");
        BlackHole sgr = u.blackHoles.get(0);
        Check.rel("Sgr A* Schwarzschild radius (km)", sgr.schwarzschildRadiusM() / 1e3, 1.269e7, 0.005);
        Check.near("Schwarzschild ISCO = 6M", BlackHole.iscoInM(0), 6.0, 1e-9);
        Check.near("Extremal Kerr prograde ISCO -> 1M", BlackHole.iscoInM(0.99999), 1.0, 0.05);
        double b = 1000, defl = Geodesics.deflection(b);
        Check.rel("weak-field deflection 4M/b at b=1000M", defl, 4.0 / b, 0.02);
        Check.that("ray with b = 5.19 M (just below 3*sqrt3) is captured", Double.isNaN(Geodesics.deflection(5.19)));
        Check.that("ray with b = 5.21 M (just above 3*sqrt3) escapes", !Double.isNaN(Geodesics.deflection(5.21)));
        Check.near("disc redshift at ISCO, face-on (g = sqrt(1-3/r))", Geodesics.discRedshiftFactor(6, 0), Math.sqrt(0.5), 1e-12);

        System.out.println("[Procedural determinism]");
        ProceduralStars ps = new ProceduralStars(Universe.DEFAULT_SEED);
        List<ProceduralStars.PStar> c1 = ps.cell(100, -40, 2), c2 = new ProceduralStars(Universe.DEFAULT_SEED).cell(100, -40, 2);
        Check.that("same cell -> identical stars (" + c1.size() + ")", c1.size() == c2.size() && c1.size() > 0 && c1.get(0).equals(c2.get(0)) && c1.get(c1.size() - 1).equals(c2.get(c2.size() - 1)));
        Check.that("different seed -> different content", !new ProceduralStars(1).cell(100, -40, 2).equals(c1));
        double local = MilkyWayModel.densityAtIcrs(Vec3d.ZERO);
        Check.near("model density at the Sun = 1", local, 1.0, 0.36);
        Check.that("density toward the Galactic centre is far higher", MilkyWayModel.density(300, 0, 0) > 50);
        Check.that("no procedural stars inside the catalogue sphere", ps.cell(ProceduralStars.cellIndex(MilkyWayModel.R0_PC), 0, 0).stream().allMatch(s -> s.icrsPc().length() >= 25));

        System.out.println("[Travel planner]");
        double proxM = dProx * Units.LIGHT_YEAR;
        TravelPlanner.Plan p = TravelPlanner.brachistochrone(proxM, 9.80665);
        Check.near("1 g brachistochrone to Proxima: Earth time (yr)", p.earthTimeS() / Units.JULIAN_YEAR_S, 5.9, 0.2);
        Check.near("1 g brachistochrone to Proxima: ship time (yr)", p.shipTimeS() / Units.JULIAN_YEAR_S, 3.6, 0.2);
        Check.that("warp is flagged as non-physical", !TravelPlanner.warp(proxM, 1e6).realPhysics());

        System.out.println("[Flight model]");
        Body earth = u.solarSystem.get("earth");
        FlightModel fm = new FlightModel(new FlightModel.Bodies() {
            public List<Body> bodies() { return u.solarSystem.all(); }
            public Vec3d position(Body bb, double tt) { return bb.positionInSystem(tt); }
            public Vec3d velocity(Body bb, double tt) { return bb.velocityInSystem(tt); }
        });
        Check.near("sea-level air density from 1 bar and 8 km scale height (kg/m^3)", FlightModel.airDensity(earth, 0), 1.27, 0.08);
        // 500 km circular orbit (above the 100 km rendering-atmosphere top) for 5 orbits
        double r0 = earth.meanRadius() + 500e3, v0 = Math.sqrt(earth.gm / r0);
        Vec3d ep = earth.positionInSystem(t), ev = earth.velocityInSystem(t);
        FlightModel.State s = new FlightModel.State(ep.add(new Vec3d(r0, 0, 0)), ev.add(new Vec3d(0, v0, 0)), 1e4);
        double period = 2 * Math.PI * Math.sqrt(r0 * r0 * r0 / earth.gm);
        double tt = t;
        for (int k = 0; k < 50; k++) { s = fm.step(s, tt, period / 10, null); tt += period / 10; }
        double alt = s.pos().distance(earth.positionInSystem(tt)) - earth.meanRadius();
        Check.near("altitude after 5 orbits in full N-body field (km)", alt / 1e3, 500, 15);
    }

    static ObjectIndex.Entry first(Universe u, String q) { return u.index.search(q, 1).get(0).entry(); }
    static void expect(Universe u, String q, String name, ObjectIndex.Kind kind) {
        var hits = u.index.search(q, 3);
        boolean ok = !hits.isEmpty() && hits.get(0).entry().name().equals(name) && hits.get(0).entry().kind() == kind;
        Check.that("search \"" + q + "\" -> " + (hits.isEmpty() ? "nothing" : hits.get(0).entry().name() + " [" + hits.get(0).entry().kind() + "]"), ok);
    }
}
