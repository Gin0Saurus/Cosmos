package dev.saurus.cosmos.client;

import dev.saurus.cosmos.core.Universe;
import dev.saurus.cosmos.core.body.Body;
import dev.saurus.cosmos.core.coords.UniversePos;
import dev.saurus.cosmos.core.math.Mat3;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.terrain.PlanetProjection;
import dev.saurus.cosmos.core.terrain.PlanetTerrains;
import dev.saurus.cosmos.net.CosmosPayloads;
import dev.saurus.cosmos.world.Planets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * Client half of the space <-> surface hand-over. Launch: the server says you left the top of a body's atmosphere at
 * (lat, lon); we put the ship above exactly that point of the rotating body. Landing: when the ship descends through a
 * walkable body's entry altitude we compute the latitude/longitude underneath it and ask the server to set us down there.
 */
public final class SurfaceLink {
    private SurfaceLink() {}

    /** Body the player is standing on ("" for none / unknown). */
    public static volatile String currentBody = "";
    private static String launchedFrom = null;
    private static boolean armed = true;
    private static long landRequestedAt = 0;

    /** Famous landing sites used when /cosmos land is given only a body. lat, lon (east positive) in degrees. */
    public static final Map<String, double[]> SITES = Map.ofEntries(
            Map.entry("earth", new double[] { 51.5007, -0.1246 }),      // Westminster, London
            Map.entry("moon", new double[] { 0.6741, 23.4730 }),        // Apollo 11, Tranquility Base
            Map.entry("mars", new double[] { 18.4447, 77.4508 }),       // Perseverance, Jezero crater
            Map.entry("venus", new double[] { -7.5, -57.0 }),           // Venera 13
            Map.entry("titan", new double[] { -10.25, 167.7 }),         // Huygens probe
            Map.entry("pluto", new double[] { 25.0, 175.0 }),           // Sputnik Planitia
            Map.entry("io", new double[] { 0.0, 0.0 }),
            Map.entry("europa", new double[] { 0.0, 0.0 }));

    /** Altitude below which flying toward a walkable body hands you to its surface. */
    public static double entryAltitude(Body b) { return Math.max(5_000, Math.min(100_000, b.meanRadius() * 0.04)); }

    static Mat3 orientation(Body b, double t) { return b.rotation != null ? b.rotation.orientation(t) : Mat3.IDENTITY; }

    public static void onLaunch(CosmosPayloads.Launch p) {
        Universe u = CosmosState.universe();
        Minecraft mc = Minecraft.getInstance();
        if (u == null) return;
        Body b = u.solarSystem.get(p.body());
        if (b == null) return;
        double t = CosmosState.time.seconds();
        UniversePos centre = UniversePos.ofMetres(b.positionInSystem(t));
        double[] uv = PlanetProjection.unit(Math.toRadians(p.latDeg()), Math.toRadians(p.lonDeg()));
        Vec3d up = orientation(b, t).apply(new Vec3d(uv[0], uv[1], uv[2]));
        double alt = entryAltitude(b) * 1.6;
        CosmosState.shipPos = centre.add(up.mul(b.meanRadius() + alt));
        CosmosState.targetName = b.name;
        CosmosState.targetPos = centre;
        CosmosState.spaceMode = true;
        launchedFrom = b.id; armed = false; currentBody = "";
        float[] yp = CosmosState.lookAt(up.negate().add(up.cross(CosmosState.upHint()).normalize().mul(0.9)).normalize());
        if (mc.player != null) {
            mc.player.setYRot(yp[0]); mc.player.setXRot(yp[1]);
            mc.player.sendSystemMessage(Component.literal(String.format((b.atmosphere != null ? "§bLeft %s's atmosphere" : "§bLifted off %s") + " at %.2f, %.2f. Fly back down into any planet or moon to land.", b.name, p.latDeg(), p.lonDeg())));
        }
    }

    /** View direction to apply once a landing completes (e.g. face the landmark you came to see). */
    public static volatile float pendingYaw = Float.NaN, pendingPitch = 0;

    public static void onWorldInfo(CosmosPayloads.WorldInfo p) {
        currentBody = p.body();
        Minecraft mc = Minecraft.getInstance();
        if (!Float.isNaN(pendingYaw) && mc.player != null) { mc.player.setYRot(pendingYaw); mc.player.setXRot(pendingPitch); pendingYaw = Float.NaN; }
        Planets.overworldIsEarth = p.overworldIsEarth();
        if (!p.body().isEmpty()) CosmosState.spaceMode = false;
    }

    /** Called every client tick while in space mode. */
    public static void tick() {
        Universe u = CosmosState.universe();
        if (u == null || !CosmosState.spaceMode) return;
        if (System.currentTimeMillis() - landRequestedAt < 3000) return;
        double t = CosmosState.time.seconds();
        for (Body b : u.solarSystem.all()) {
            if (!PlanetTerrains.isWalkable(b.id)) continue;
            UniversePos centre = UniversePos.ofMetres(b.positionInSystem(t));
            Vec3d rel = centre.to(CosmosState.shipPos);
            double alt = rel.length() - b.meanRadius(), entry = entryAltitude(b);
            if (b.id.equals(launchedFrom) && !armed) { if (alt > entry * 1.25) armed = true; else continue; }
            if (alt > entry) continue;
            Vec3d local = orientation(b, t).transpose().apply(rel.normalize());
            double lat = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, local.z())))), lon = Math.toDegrees(Math.atan2(local.y(), local.x()));
            requestLanding(b.id, lat, lon);
            return;
        }
    }

    public static boolean requestLanding(String body, double latDeg, double lonDeg) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientPlayNetworking.canSend(CosmosPayloads.Land.TYPE)) {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§cThis server doesn't have Cosmos installed, so you can't land on other worlds here."));
            return false;
        }
        landRequestedAt = System.currentTimeMillis();
        ClientPlayNetworking.send(new CosmosPayloads.Land(body, latDeg, lonDeg));
        if (!body.isEmpty()) { CosmosState.spaceMode = false; launchedFrom = null; armed = true; }
        return true;
    }
}
