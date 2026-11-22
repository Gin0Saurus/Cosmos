package dev.saurus.cosmos.client;

import dev.saurus.cosmos.core.Universe;
import dev.saurus.cosmos.core.blackhole.BlackHole;
import dev.saurus.cosmos.core.body.Body;
import dev.saurus.cosmos.core.catalog.StarColor;
import dev.saurus.cosmos.core.catalog.StarSystem;
import dev.saurus.cosmos.core.coords.Frames;
import dev.saurus.cosmos.core.coords.UniversePos;
import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.time.SimTime;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** All mutable Cosmos client state: the loaded universe, the ship (camera) position, simulated time, options. */
public final class CosmosState {
    private CosmosState() {}

    public static CompletableFuture<Universe> universeFuture;
    public static Universe universe() { return universeFuture != null && universeFuture.isDone() && !universeFuture.isCompletedExceptionally() ? universeFuture.join() : null; }

    // ---- modes
    public static boolean spaceMode = false;
    public static boolean overworldSky = true;
    public static double observerLatDeg = 51.4779, observerLonDeg = 0.0; // Royal Observatory, Greenwich (default)

    // ---- ship
    public static UniversePos shipPos = UniversePos.ORIGIN;
    public static double speedFactor = 0.5;       // per second, times distance to the nearest surface
    public static String targetName = null;
    public static UniversePos targetPos = null;

    // ---- time
    public static final SimTime time = nowUtc();
    public static SimTime nowUtc() {
        ZonedDateTime z = ZonedDateTime.now(ZoneOffset.UTC);
        return SimTime.fromUtc(z.getYear(), z.getMonthValue(), z.getDayOfMonth(), z.getHour(), z.getMinute(), z.getSecond());
    }

    /** A thing the renderer can draw as a sphere, already resolved to an absolute position. */
    public record SceneBody(String name, Body body, UniversePos pos, double radiusM, int type, float[] colour, double emissive,
                            UniversePos lightPos, double lightLumSun, Body.Atmosphere atmosphere, boolean hasRings) {}

    /** Bodies near the ship worth drawing: Solar System, plus the local star (and its planets) when visiting one. */
    public static List<SceneBody> sceneBodies(Universe u, double t) {
        List<SceneBody> out = new ArrayList<>();
        // Solar System (positions relative to the Sun = universe origin)
        UniversePos sunPos = UniversePos.ORIGIN;
        for (Body b : u.solarSystem.all()) {
            UniversePos p = b.kind == Body.Kind.STAR ? sunPos : UniversePos.ofMetres(b.positionInSystem(t));
            out.add(toScene(b.name, b, p, sunPos, 1.0));
        }
        // nearest catalogue star other than the Sun, within 0.1 pc: draw it (and its known planets)
        Vec3d obsPc = shipPos.approxMetres().mul(1 / Units.PARSEC);
        if (obsPc.length() > 0.02) {
            int i = u.stars.nearest(obsPc, t);
            if (i >= 0) {
                Vec3d sp = u.stars.positionPc(i, t);
                if (sp.distance(obsPc) < 0.1) {
                    UniversePos starPos = UniversePos.ofParsecs(sp.x(), sp.y(), sp.z());
                    String name = u.stars.displayName(i);
                    StarSystem sys = u.systemOf(u.stars.name(i) == null ? "" : u.stars.name(i));
                    double teff = u.stars.teff(i);
                    double lum = Math.max(u.stars.luminosity(i), 1e-5);
                    double rM = u.stars.radiusEstimateRsun(i) * Units.R_SUN;
                    if (sys != null) { rM = sys.star().meanRadius(); teff = sys.star().teffK > 0 ? sys.star().teffK : teff; }
                    out.add(new SceneBody(name, sys != null ? sys.star() : null, starPos, rM, 2, StarColor.rgbFromTemperature(teff), lum, starPos, lum, null, false));
                    if (sys != null) for (Body pl : sys.planets())
                        out.add(new SceneBody(pl.name, pl, starPos.add(pl.positionInSystem(t)), pl.meanRadius(),
                                pl.kind == Body.Kind.GAS_GIANT ? 1 : pl.kind == Body.Kind.ICE_GIANT ? 3 : 0,
                                pl.kind == Body.Kind.GAS_GIANT ? new float[]{0.75f, 0.65f, 0.5f} : pl.kind == Body.Kind.ICE_GIANT ? new float[]{0.55f, 0.7f, 0.8f} : new float[]{0.45f, 0.4f, 0.37f},
                                0, starPos, lum, null, false));
                }
            }
        }
        return out;
    }

    private static SceneBody toScene(String name, Body b, UniversePos p, UniversePos light, double lum) {
        int type = switch (b.kind) { case STAR -> 2; case GAS_GIANT -> 1; case ICE_GIANT -> 3; default -> 0; };
        float[] col = switch (b.name) {
            // representative albedo colours (approximate, from spacecraft imagery) -- flagged in docs
            case "Sun" -> StarColor.rgbFromTemperature(5772);
            case "Mercury" -> new float[]{0.50f, 0.47f, 0.44f};
            case "Venus" -> new float[]{0.95f, 0.88f, 0.70f};
            case "Earth" -> new float[]{0.22f, 0.32f, 0.45f};
            case "Moon" -> new float[]{0.42f, 0.40f, 0.38f};
            case "Mars" -> new float[]{0.62f, 0.36f, 0.22f};
            case "Jupiter" -> new float[]{0.85f, 0.75f, 0.60f};
            case "Saturn" -> new float[]{0.90f, 0.82f, 0.62f};
            case "Uranus" -> new float[]{0.62f, 0.82f, 0.88f};
            case "Neptune" -> new float[]{0.35f, 0.52f, 0.88f};
            case "Io" -> new float[]{0.85f, 0.78f, 0.40f};
            case "Europa" -> new float[]{0.85f, 0.80f, 0.72f};
            case "Titan" -> new float[]{0.80f, 0.60f, 0.30f};
            default -> new float[]{0.55f, 0.53f, 0.50f};
        };
        return new SceneBody(name, b, p, b.meanRadius(), type, col, type == 2 ? 1.0 : 0, light, lum, b.atmosphere, !b.rings.isEmpty());
    }

    /** Distance from the ship to the nearest surface (body, star, black hole horizon), metres. */
    public static double nearestSurfaceDistance(Universe u, double t) {
        double best = Double.MAX_VALUE;
        for (SceneBody s : sceneBodies(u, t)) best = Math.min(best, shipPos.distanceTo(s.pos()) - s.radiusM());
        Vec3d obsPc = shipPos.approxMetres().mul(1 / Units.PARSEC);
        int i = u.stars.nearest(obsPc, t);
        if (i >= 0) best = Math.min(best, u.stars.positionPc(i, t).distance(obsPc) * Units.PARSEC);
        for (BlackHole bh : u.blackHoles) best = Math.min(best, shipPos.distanceTo(bh.position()) - bh.horizonRadiusM());
        return Math.max(best, 1.0);
    }

    // ---- ship orientation: Minecraft yaw/pitch interpreted in the ecliptic frame (ecliptic north = "up")
    public static Vec3d forward(float yRotDeg, float xRotDeg) {
        double yaw = Math.toRadians(-yRotDeg), pitch = Math.toRadians(-xRotDeg);
        Vec3d ecl = new Vec3d(Math.cos(pitch) * Math.cos(yaw), Math.cos(pitch) * Math.sin(yaw), Math.sin(pitch));
        return Frames.ECLIPTIC_TO_EQUATORIAL.apply(ecl);
    }
    public static Vec3d upHint() { return Frames.ECLIPTIC_TO_EQUATORIAL.apply(Vec3d.Z); }
    /** Yaw/pitch (degrees, Minecraft convention) that look along an ICRS direction. */
    public static float[] lookAt(Vec3d icrsDir) {
        Vec3d e = Frames.EQUATORIAL_TO_ECLIPTIC.apply(icrsDir.normalize());
        float yaw = (float) -Math.toDegrees(Math.atan2(e.y(), e.x()));
        float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, e.z()))));
        return new float[]{yaw, pitch};
    }
}
