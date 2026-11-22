package dev.saurus.cosmos.core;

import dev.saurus.cosmos.core.body.Body;
import dev.saurus.cosmos.core.body.SolarSystem;
import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.orbit.Kepler;
import dev.saurus.cosmos.core.time.SimTime;

/**
 * Validates the orbit engine against JPL Horizons (DE441 and satellite ephemerides) state vectors for
 * 2026-Nov-22 00:00 TDB (JD 2461366.5), fetched from ssd.jpl.nasa.gov/api/horizons.api, ICRF, km.
 */
final class SolarSystemTests {
    static final double T = (2461366.5 - SimTime.J2000_JD) * SimTime.DAY;

    static Vec3d km(double x, double y, double z) { return new Vec3d(x, y, z).mul(1000); }

    static void angleCheckTight(String what, Vec3d got, Vec3d ref, double maxArcmin, double maxDistErrM) {
        double ang = Math.toDegrees(Math.acos(Math.min(1, got.normalize().dot(ref.normalize())))) * 60;
        Check.that(String.format("%s direction error %.3f arcmin (limit %.1f)", what, ang, maxArcmin), ang <= maxArcmin);
        Check.near(what + " distance (km)", got.length() / 1e3, ref.length() / 1e3, maxDistErrM / 1e3);
    }

    static void angleCheck(String what, Vec3d got, Vec3d ref, double maxArcmin) {
        double ang = Math.toDegrees(Math.acos(Math.min(1, got.normalize().dot(ref.normalize())))) * 60;
        Check.that(String.format("%s direction error %.3f arcmin (limit %.1f)", what, ang, maxArcmin), ang <= maxArcmin);
        Check.rel(what + " distance", got.length(), ref.length(), 0.01);
    }

    static void run(SolarSystem ss) {
        System.out.println("[Solar System vs JPL Horizons, 2026-11-22 TDB]");
        // Heliocentric planets (Horizons: center @10, ICRF)
        angleCheck("Earth heliocentric", ss.position(ss.get("earth"), T), km(7.540820970466869E+07, 1.166004984826071E+08, 5.054304132621233E+07), 1.0);
        angleCheck("Mars heliocentric", ss.position(ss.get("mars"), T), km(-8.379875352591993E+07, 2.051869750551792E+08, 9.637456595662700E+07), 2.0);
        angleCheck("Jupiter heliocentric", ss.position(ss.get("jupiter"), T), km(-5.656657963357213E+08, 5.101295288080875E+08, 2.324248635127834E+08), 2.0);
        angleCheck("Neptune heliocentric", ss.position(ss.get("neptune"), T), km(4.462238688641308E+09, 2.547178804012141E+08, -6.830793539413751E+06), 2.0);
        // Moon: ELP-2000/82 truncated theory, no fitting -> compare at all three reference epochs
        Body moon = ss.get("moon"), io = ss.get("io"), titan = ss.get("titan");
        java.util.Map<String, Object> h = ss.horizons;
        double[] jd = dev.saurus.cosmos.core.util.Json.nums(h.get("epochs_jd_tdb"));
        java.util.Map<String, Object> ref = dev.saurus.cosmos.core.util.Json.obj(h.get("moons"));
        for (int k = 0; k < 3; k++) {
            Vec3d r = SolarSystem.vec(dev.saurus.cosmos.core.util.Json.arr(ref.get("301")).get(k));
            double t = (jd[k] - SimTime.J2000_JD) * SimTime.DAY;
            angleCheckTight("Moon geocentric JD " + jd[k], moon.orbit.position(t), r, 3.0, 50e3);
        }
        // Satellites: calibrated at epochs 0 and 2, validated at the independent middle epoch (2013-06-01)
        System.out.println("[Satellites at independent validation epoch 2013-06-01 (not used in fit)]");
        double tv = (jd[1] - SimTime.J2000_JD) * SimTime.DAY;
        for (String id : ref.keySet()) {
            if (id.equals("301")) continue;
            Body b = ss.naif(Integer.parseInt(id));
            Vec3d r = SolarSystem.vec(dev.saurus.cosmos.core.util.Json.arr(ref.get(id)).get(1));
            // Measured accuracy of calibrated mean elements; the larger limits are physical, not bugs:
            //  Phobos: tidal secular acceleration + 1.1-yr apsidal precession; Mimas: +/-43 deg libration from the
            //  Mimas-Tethys resonance; Europa: forced eccentricity (Laplace resonance); Titan/Iapetus/Triton: apsidal or
            //  plane-orientation error left after a phase-only fit. Fix path: Chebyshev tables from Horizons (ROADMAP).
            double lim = switch (id) { case "401" -> 180; case "601" -> 1500; case "606" -> 240; case "608" -> 150; case "801" -> 240; default -> 60; };
            double dlim = switch (id) { case "401" -> 0.04; case "502" -> 0.02; case "601" -> 0.025; case "606" -> 0.03; case "608" -> 0.01; default -> 0.01; };
            Vec3d got = b.orbit.position(tv);
            double ang = Math.toDegrees(Math.acos(Math.min(1, got.normalize().dot(r.normalize())))) * 60;
            Check.that(String.format("%s (%s) direction error %.1f arcmin (limit %.0f)", b.name, id, ang, lim), ang <= lim);
            Check.rel(b.name + " distance", got.length(), r.length(), dlim);
        }
        System.out.println("[Scale]");
        Body earth = ss.get("earth"), jup = ss.get("jupiter"), sun = ss.sun;
        Check.near("Earth equatorial radius (km)", earth.equatorialRadius() / 1e3, 6378.1366, 1e-9);
        Check.rel("Jupiter/Earth equatorial radius ratio ~11.2", jup.equatorialRadius() / earth.equatorialRadius(), 11.21, 0.005);
        Check.near("Sun radius (km)", sun.equatorialRadius() / 1e3, 695700, 1e-6);
        Check.rel("Earth(-Moon barycentre) mean semi-major axis ~1 AU", meanA(ss, "earth") / Units.AU, 1.0, 1e-5);
        Check.rel("Neptune mean semi-major axis ~30 AU", meanA(ss, "neptune") / Units.AU, 30.07, 0.001);
        Check.rel("Earth sidereal year (days)", earth.orbit.period() / SimTime.DAY, 365.256, 0.0005);

        System.out.println("[Rotation / tidal locking]");
        Check.rel("Earth sidereal day (h)", earth.rotation.siderealPeriodSeconds() / 3600, 23.9344696, 1e-6);
        Check.that("Moon is tidally locked", moon.isTidallyLocked());
        Check.that("Io is tidally locked", io.isTidallyLocked());
        Check.that("Titan is tidally locked", titan.isTidallyLocked());
        Check.that("Earth is not tidally locked", !earth.isTidallyLocked());
        Check.that("Venus rotation is retrograde", ss.get("venus").rotation.siderealPeriodSeconds() < 0);
        double tilt = Math.toDegrees(Math.acos(earth.rotation.pole(T).dot(eclipticPole())));
        Check.near("Earth obliquity from IAU pole vs ecliptic (deg)", tilt, 23.44, 0.02);
        double uTilt = Math.toDegrees(Math.acos(ss.get("uranus").rotation.spinAxis(T).dot(orbitPole(ss, "uranus"))));
        Check.near("Uranus obliquity (deg, NASA 97.8)", uTilt, 97.8, 0.3);

        System.out.println("[Seasons: Earth-Sun distance]");
        // Perihelion early January, aphelion early July
        double dJan = ss.position(earth, (SimTime.julianDate(2026, 1, 3, 17, 0, 0) - SimTime.J2000_JD) * SimTime.DAY).length() / Units.AU;
        double dJul = ss.position(earth, (SimTime.julianDate(2026, 7, 6, 18, 0, 0) - SimTime.J2000_JD) * SimTime.DAY).length() / Units.AU;
        Check.near("Earth perihelion 2026-01-03 (AU)", dJan, 0.98330, 0.0003);
        Check.near("Earth aphelion 2026-07-06 (AU)", dJul, 1.01666, 0.0003);

        System.out.println("[Kepler round trip]");
        Vec3d r = ss.position(ss.get("mars"), T), v = ss.velocity(ss.get("mars"), T);
        Kepler.Elements el = Kepler.fromState(r, v, sun.gm + ss.get("mars").gm);
        Check.rel("Mars osculating a (AU)", el.a() / Units.AU, 1.5237, 0.002);
        Check.near("Mars osculating e", el.e(), 0.0934, 0.002);
        Vec3d back = Kepler.position(el);
        Check.near("Kepler state->elements->state position error (m)", back.distance(r), 0, 1.0);
        Kepler.Elements hyp = new Kepler.Elements(-2e7, 1.5, 0.3, 0.2, 0.1, 0.7);
        Vec3d hr = Kepler.position(hyp), hv = Kepler.velocity(hyp, 3.986e14);
        Kepler.Elements hyp2 = Kepler.fromState(hr, hv, 3.986e14);
        Check.rel("hyperbolic round trip a", hyp2.a(), -2e7, 1e-9);
        Check.near("hyperbolic round trip e", hyp2.e(), 1.5, 1e-9);
    }

    static double meanA(SolarSystem ss, String n) {
        Body b = ss.get(n);
        dev.saurus.cosmos.core.orbit.OrbitModel o = b.orbit;
        if (!(o instanceof dev.saurus.cosmos.core.orbit.JplPlanetOrbit)) o = embOf(o);
        return ((dev.saurus.cosmos.core.orbit.JplPlanetOrbit) o).elementsAt(0).a();
    }
    static dev.saurus.cosmos.core.orbit.OrbitModel embOf(dev.saurus.cosmos.core.orbit.OrbitModel o) {
        try { var f = o.getClass().getDeclaredField("emb"); f.setAccessible(true); return (dev.saurus.cosmos.core.orbit.OrbitModel) f.get(o); }
        catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    static double semiMajor(SolarSystem ss, String n) {
        Body b = ss.get(n);
        return Kepler.fromState(b.orbit.position(0), b.orbit.velocity(0), ss.sun.gm + b.gm).a();
    }
    static Vec3d eclipticPole() { return dev.saurus.cosmos.core.coords.Frames.ECLIPTIC_TO_EQUATORIAL.apply(Vec3d.Z); }
    static Vec3d orbitPole(SolarSystem ss, String n) {
        Body b = ss.get(n); return b.orbit.position(T).cross(b.orbit.velocity(T)).normalize();
    }
}
