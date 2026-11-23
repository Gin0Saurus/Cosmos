package dev.saurus.cosmos.core;

import dev.saurus.cosmos.core.body.Body;
import dev.saurus.cosmos.core.body.SolarSystem;
import dev.saurus.cosmos.core.terrain.*;

import static dev.saurus.cosmos.core.Check.*;

final class TerrainTests {
    static double r(double d) { return Math.toRadians(d); }

    static void run(SolarSystem ss) {
        long seed = 12345;
        System.out.println("[Planet projection]");
        PlanetProjection pe = PlanetProjection.forRadius(ss.get("earth").meanRadius());
        info("Earth circumference " + pe.circumference() + " blocks; Moon " + PlanetProjection.forRadius(ss.get("moon").meanRadius()).circumference()
                + "; Mars " + PlanetProjection.forRadius(ss.get("mars").meanRadius()).circumference() + "; Phobos " + PlanetProjection.forRadius(ss.get("phobos").meanRadius()).circumference());
        near("Earth circumference at 1 block = 100 m (blocks)", pe.circumference(), 2 * Math.PI * 6371.0e3 / 100, 64);
        double[] ll = pe.latLon(pe.x(r(-0.1278)), pe.z(r(51.5074)));
        near("London round-trips through block coords (lat deg)", Math.toDegrees(ll[0]), 51.5074, 1e-9);
        near("London round-trips through block coords (lon deg)", Math.toDegrees(ll[1]), -0.1278, 1e-9);
        double[] w = pe.latLon(pe.circumference() + 1000, -300);
        double[] w0 = pe.latLon(1000, -300);
        near("walking once around the equator returns to the same place", Math.hypot(w[0] - w0[0], w[1] - w0[1]), 0, 1e-12);
        double[] over = pe.latLon(0, -pe.quarterC() - 100);  // 100 blocks past the north pole
        near("past the north pole you're on the far side (lon 180)", Math.abs(Math.toDegrees(over[1])), 180, 1e-6);
        double[] c = pe.canonicalise(0, -pe.quarterC() - 100, 0);
        double[] cl = pe.latLon(c[0], c[1]);
        near("pole crossing teleport keeps you on the same spot (lat)", cl[0], over[0], 1e-12);
        near("pole crossing teleport keeps you on the same spot (lon)", Math.IEEEremainder(cl[1] - over[1], 2 * Math.PI), 0, 1e-12);
        near("pole crossing turns you around (yaw)", c[2], 180, 1e-9);

        System.out.println("[Earth terrain: NOAA ETOPO 2022]");
        PlanetTerrain earth = PlanetTerrains.forBody(ss.get("earth"), seed);
        that("Earth terrain uses measured data", earth.measured());
        double ev = earth.elevation(r(27.988), r(86.925));
        that("Himalaya near Everest above 5,500 m (got " + Math.round(ev) + ")", ev > 5500);
        double mt = earth.elevation(r(11.35), r(142.2));
        that("Mariana Trench below -7,000 m (got " + Math.round(mt) + ")", mt < -7000);
        double gcRim = earth.elevation(r(36.20), r(-112.06)), gcFloor = earth.elevation(r(36.10), r(-112.09));
        that("Grand Canyon: North Rim " + Math.round(gcRim) + " m stands > 800 m above the Phantom Ranch river level " + Math.round(gcFloor) + " m", gcRim - gcFloor > 800);
        double fuji = earth.elevation(r(35.3606), r(138.7274));
        that("Mount Fuji above 3,000 m (got " + Math.round(fuji) + ")", fuji > 3000);
        that("mid-Atlantic is ocean", earth.elevation(r(30), r(-40)) < -2000);
        that("Tibetan plateau above 4,000 m", earth.elevation(r(33), r(88)) > 4000);
        expectSurface(earth, "Sahara", 23, 13, Surface.DESERT);
        expectSurface(earth, "Amazon basin", -3, -60, Surface.JUNGLE);
        expectSurface(earth, "Antarctic plateau", -80, 40, Surface.ICE_SHEET);
        expectSurface(earth, "Greenland ice sheet", 72, -40, Surface.ICE_SHEET);
        expectAny(earth, "Siberia", 63, 100, Surface.TAIGA, Surface.TUNDRA);
        expectAny(earth, "southern England", 51.8, -1.3, Surface.FOREST, Surface.GRASSLAND);
        expectAny(earth, "Arabian desert", 22, 47, Surface.DESERT, Surface.SAVANNA);
        expectAny(earth, "Australian outback", -25, 133, Surface.DESERT, Surface.SAVANNA);
        expectAny(earth, "Congo rainforest", 0, 22, Surface.JUNGLE, Surface.FOREST);

        System.out.println("[Moon terrain: NASA LOLA]");
        PlanetTerrain moon = PlanetTerrains.forBody(ss.get("moon"), seed);
        double spa = moon.elevation(r(-53), r(-169));
        that("South Pole-Aitken basin floor below -4,000 m (got " + Math.round(spa) + ")", spa < -4000);
        double far = moon.elevation(r(5), r(-158));
        that("far-side highlands near the Korolev region above +3,000 m (got " + Math.round(far) + ")", far > 3000);
        expectSurface(moon, "Mare Tranquillitatis", 8.5, 31.4, Surface.LUNAR_MARE);
        expectAny(moon, "far-side highlands", 10, 180, Surface.LUNAR_HIGHLAND, Surface.LUNAR_EJECTA);

        System.out.println("[Mars terrain: NASA MOLA]");
        PlanetTerrain mars = PlanetTerrains.forBody(ss.get("mars"), seed);
        double om = mars.elevation(r(18.65), r(-133.8));
        that("Olympus Mons summit above 18,000 m (got " + Math.round(om) + ")", om > 18000);
        double hel = mars.elevation(r(-42.4), r(70.5));
        that("Hellas basin floor below -5,500 m (got " + Math.round(hel) + ")", hel < -5500);
        double vm = mars.elevation(r(-13.9), r(-59.2)), rim = mars.elevation(r(-10.5), r(-59.2));
        that("Valles Marineris floor > 3 km below its rim (" + Math.round(rim - vm) + " m)", rim - vm > 3000);
        expectSurface(mars, "north polar cap", 87, 0, Surface.MARS_POLAR_ICE);

        System.out.println("[Procedural worlds]");
        PlanetTerrain pluto = PlanetTerrains.forBody(ss.get("pluto"), seed);
        that("Pluto is labelled procedural", !pluto.measured() && pluto.provenance().startsWith("Procedural"));
        expectSurface(pluto, "Sputnik Planitia", 25, 175, Surface.NITROGEN_ICE);
        that("Io has no impact craters but has lava", PlanetTerrains.isWalkable("io"));
        that("Jupiter has no walkable surface", PlanetTerrains.forBody(ss.get("jupiter"), seed) == null);
        near("same seed -> same terrain", moon.elevation(0.3, 1.1), PlanetTerrains.forBody(ss.get("moon"), seed).elevation(0.3, 1.1), 0);
        that("different seed -> different procedural detail", PlanetTerrains.forBody(ss.get("moon"), 999).elevation(0.3, 1.1) != moon.elevation(0.3, 1.1));
        info("walkable bodies: " + PlanetTerrains.walkable());

        System.out.println("[Caves]");
        for (String id : new String[] { "earth", "moon", "mars", "europa" }) {
            PlanetTerrain t = PlanetTerrains.forBody(ss.get(id), seed);
            PlanetProjection pp = PlanetProjection.forRadius(t.radiusM());
            Caves cv = new Caves(id, seed, pp.circumference());
            int depthMax = id.equals("earth") ? 96 : id.equals("europa") ? 60 : 40;
            long t0 = System.nanoTime(); int carved = 0, total = 0, chunks = 40;
            for (int ch = 0; ch < chunks; ch++) for (int i = 0; i < 16; i++) for (int k = 0; k < 16; k++) {
                double[] q = pp.latLon(ch * 16 + i + 2000, k + ch * 37);
                double[] u = PlanetProjection.unit(q[0], q[1]);
                double e = t.elevation(q[0], q[1]);
                Surface sf = t.column(q[0], q[1], e, 0).surface();
                int top = (int) Math.floor(SurfaceScale.blockYForElevation(e));
                for (int d = 1; d < depthMax; d++) { total++; if (cv.carve(u, top - d, d, sf) < 0) carved++; }
            }
            double ms = (System.nanoTime() - t0) / 1e6 / chunks;
            info(String.format("%s (%s): %.1f%% of the crust is cave, %.1f ms per chunk", id, cv.style, 100.0 * carved / total, ms));
            double lo = cv.style == Caves.Style.LAVA_TUBES ? 0.002 : 0.01;
            that(id + " has caves but is mostly rock", carved > total * lo && carved < total * 0.25);
            that(id + " caves cost under 30 ms per chunk", ms < 30);
        }
        {   // a lava tube near the Apollo 11 landing site (Mare Tranquillitatis)
            PlanetTerrain t = PlanetTerrains.forBody(ss.get("moon"), seed);
            PlanetProjection pp = PlanetProjection.forRadius(t.radiusM());
            Caves cv = new Caves("moon", seed, pp.circumference());
            double x0 = pp.x(r(23.47)), z0 = pp.z(r(0.67));
            int found = 0;
            for (int i = -1500; i <= 1500 && found == 0; i += 3) for (int k = -1500; k <= 1500 && found == 0; k += 3) {
                double[] q = pp.latLon(x0 + i, z0 + k);
                double e = t.elevation(q[0], q[1]);
                if (t.column(q[0], q[1], e, 0).surface() != Surface.LUNAR_MARE) continue;
                int top = (int) Math.floor(SurfaceScale.blockYForElevation(e));
                if (cv.carve(PlanetProjection.unit(q[0], q[1]), top - 14, 14, Surface.LUNAR_MARE) < -0.5) found = 1;
            }
            that("a lava tube runs under Mare Tranquillitatis within 15 km of Apollo 11", found == 1);
        }

        System.out.println("[Terrain performance]");
        for (String id : new String[] { "earth", "moon", "mars", "europa" }) {
            PlanetTerrain t = PlanetTerrains.forBody(ss.get(id), seed);
            PlanetProjection pp = PlanetProjection.forRadius(t.radiusM());
            long t0 = System.nanoTime(); int chunks = 200;
            for (int ch = 0; ch < chunks; ch++) for (int i = 0; i < 18; i++) for (int k = 0; k < 18; k++) {
                double[] q = pp.latLon(ch * 16 + i + 5000, k - 3000);
                t.elevation(q[0], q[1]);
            }
            double ms = (System.nanoTime() - t0) / 1e6 / chunks;
            info(String.format("%s: %.2f ms of terrain sampling per chunk (18x18 columns)", id, ms));
            that(id + " samples a chunk in under 25 ms on one core", ms < 25);
        }
    }

    static void expectSurface(PlanetTerrain t, String name, double lat, double lon, Surface s) {
        Surface got = t.column(r(lat), r(lon)).surface();
        that(name + " is " + s + " (got " + got + ")", got == s);
    }
    static void expectAny(PlanetTerrain t, String name, double lat, double lon, Surface a, Surface b) {
        Surface got = t.column(r(lat), r(lon)).surface();
        that(name + " is " + a + " or " + b + " (got " + got + ")", got == a || got == b);
    }
}
