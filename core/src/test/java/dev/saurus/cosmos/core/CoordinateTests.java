package dev.saurus.cosmos.core;

import dev.saurus.cosmos.core.coords.FloatingOrigin;
import dev.saurus.cosmos.core.coords.Frames;
import dev.saurus.cosmos.core.coords.UniversePos;
import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.math.Vec3d;

final class CoordinateTests {
    static void run() {
        System.out.println("[Coordinates]");
        // Proxima Centauri at its HYG position (pc) - distance in light years
        UniversePos prox = UniversePos.ofParsecs(-0.472264, -0.361451, -1.151219);
        Check.rel("Proxima distance (ly) from HYG xyz", UniversePos.ORIGIN.distanceTo(prox) / Units.LIGHT_YEAR, 4.2266, 0.001);

        // A point 2.5 Mly away; a 1 mm offset there must survive exactly
        UniversePos far = UniversePos.ofParsecs(761_000, 120_000, -40_000);
        UniversePos farPlus = far.add(new Vec3d(0.001, 0, 0));
        Check.near("1 mm offset preserved at Andromeda distance (m)", far.to(farPlus).x(), 0.001, 2e-6);
        // At 10 Gly
        UniversePos edge = UniversePos.ofLightYears(new Vec3d(1e10, -3e9, 2e9));
        Check.near("1 mm offset preserved at 10 Gly (m)", edge.to(edge.add(new Vec3d(0, 0.001, 0))).y(), 0.001, 2e-6);
        // Relative vector between two nearby points far from origin is exact
        UniversePos a = edge.add(new Vec3d(1.5e11, 0, 0)), b = edge.add(new Vec3d(1.5e11 + 6.371e6, 0, 0));
        Check.near("Earth-radius separation at 10 Gly (m)", a.to(b).x(), 6.371e6, 1e-3);
        // Crossing sector boundaries in both directions
        UniversePos s = UniversePos.of(0, 0, 0, UniversePos.SECTOR - 0.25, 0, 0).add(new Vec3d(0.5, 0, 0));
        Check.that("sector carry", s.sx == 1 && Math.abs(s.lx - 0.25) < 1e-9);
        UniversePos n = UniversePos.of(0, 0, 0, 0.25, 0, 0).add(new Vec3d(-0.5, 0, 0));
        Check.that("sector borrow", n.sx == -1 && Math.abs(n.lx - (UniversePos.SECTOR - 0.25)) < 1e-3);

        System.out.println("[Floating origin / scaled-space rendering]");
        FloatingOrigin fo = new FloatingOrigin(50_000, 1_000_000);
        fo.rebase(UniversePos.ofMetres(new Vec3d(1.496e11, 0, 0)));
        FloatingOrigin.Projected sunP = fo.project(UniversePos.ORIGIN);
        double angTrue = 2 * Math.atan(Units.R_SUN / 1.496e11);
        double angRender = 2 * Math.atan(Units.R_SUN * sunP.scale() / Math.sqrt(sunP.x() * (double) sunP.x()));
        Check.rel("Sun angular size preserved by scaled-space projection", angRender, angTrue, 1e-6);
        Check.that("render distance inside far shell", Math.abs(sunP.x()) < 1_000_000);
        Check.that("compression is monotonic", fo.scaleFor(1e9) * 1e9 < fo.scaleFor(2e9) * 2e9);

        System.out.println("[Frames]");
        // Galactic centre direction (l=0,b=0) should be RA 266.40, Dec -28.94 (IAU 1958 system in ICRS)
        Vec3d gc = Frames.GALACTIC_TO_EQUATORIAL.apply(Vec3d.X);
        double ra = Math.toDegrees(Math.atan2(gc.y(), gc.x())) + 360, dec = Math.toDegrees(Math.asin(gc.z()));
        Check.near("Galactic centre RA (deg)", ra, 266.405, 0.01);
        Check.near("Galactic centre Dec (deg)", dec, -28.936, 0.01);
        Vec3d ngp = Frames.GALACTIC_TO_EQUATORIAL.apply(Vec3d.Z);
        Check.near("North Galactic Pole Dec (deg)", Math.toDegrees(Math.asin(ngp.z())), 27.128, 0.01);
    }
}
