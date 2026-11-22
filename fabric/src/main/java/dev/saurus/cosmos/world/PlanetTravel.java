package dev.saurus.cosmos.world;

import dev.saurus.cosmos.core.terrain.PlanetProjection;
import dev.saurus.cosmos.core.terrain.SurfaceScale;
import dev.saurus.cosmos.net.CosmosPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of moving around a spherical world:
 * <ul>
 *   <li>wrap-around: walking past longitude +/-180 or over a pole moves you onto the matching spot (same terrain);</li>
 *   <li>launch: climbing out of the top of the atmosphere column hands you to the client's space flight;</li>
 *   <li>landing: the client asks to come down at a latitude/longitude and is placed above that spot.</li>
 * </ul>
 */
public final class PlanetTravel {
    private PlanetTravel() {}

    /** Players currently flying in space (their body is parked, weightless, at the launch point). */
    private static final Map<UUID, Vec3> IN_SPACE = new HashMap<>();

    /** Players on their way down after a landing request: fall damage is suppressed until they touch down. */
    private static final java.util.Set<UUID> DESCENDING = new java.util.HashSet<>();

    public static boolean inSpace(ServerPlayer p) { return IN_SPACE.containsKey(p.getUUID()); }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (DESCENDING.contains(p.getUUID())) {
                p.resetFallDistance();
                if (p.onGround() || p.isInWater()) DESCENDING.remove(p.getUUID());
            }
            if (IN_SPACE.containsKey(p.getUUID())) {
                Vec3 park = IN_SPACE.get(p.getUUID());
                p.setDeltaMovement(Vec3.ZERO);
                p.resetFallDistance();
                if (p.position().distanceToSqr(park) > 4) p.teleportTo(park.x, park.y, park.z);
                continue;
            }
            String body = Planets.bodyOf(p.level());
            if (body == null) continue;
            PlanetProjection proj = Planets.projection(body);
            double[] c = proj.canonicalise(p.getX(), p.getZ(), p.getYRot());
            if (c != null) {
                // same terrain on both sides of the seam; keep velocity so the crossing is a single frame
                Vec3 v = p.getDeltaMovement();
                if (c[2] != p.getYRot()) v = new Vec3(-v.x, v.y, -v.z);
                p.teleport(new TeleportTransition((ServerLevel) p.level(), new Vec3(c[0], p.getY(), c[1]), v, (float) c[2], p.getXRot(), TeleportTransition.DO_NOTHING));
                continue;
            }
            if (p.getY() >= SurfaceScale.LAUNCH_Y) launch(p, body, proj);
        }
    }

    private static void launch(ServerPlayer p, String body, PlanetProjection proj) {
        double[] ll = proj.latLon(p.getX(), p.getZ());
        double up = Math.max(0, p.getDeltaMovement().y * 20);
        IN_SPACE.put(p.getUUID(), p.position());
        p.setNoGravity(true);
        p.setDeltaMovement(Vec3.ZERO);
        if (ServerPlayNetworking.canSend(p, CosmosPayloads.Launch.TYPE))
            ServerPlayNetworking.send(p, new CosmosPayloads.Launch(body, Math.toDegrees(ll[0]), Math.toDegrees(ll[1]), up));
    }

    /** Handles a landing request from a client. */
    public static void land(ServerPlayer p, CosmosPayloads.Land req) {
        MinecraftServer server = p.level().getServer();
        Vec3 parked = IN_SPACE.remove(p.getUUID());
        p.setNoGravity(false);
        if (req.body().isEmpty()) {           // back down where we left
            ServerLevel lvl = (ServerLevel) p.level();
            Vec3 at = parked != null ? parked : p.position();
            int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(at.x), (int) Math.floor(at.z));
            p.teleport(new TeleportTransition(lvl, new Vec3(at.x, y + 1, at.z), Vec3.ZERO, p.getYRot(), p.getXRot(), TeleportTransition.DO_NOTHING));
            p.resetFallDistance();
            return;
        }
        ResourceKey<Level> key = Planets.dimension(req.body());
        ServerLevel target = key == null ? null : server.getLevel(key);
        if (target == null) {
            p.sendSystemMessage(Component.literal("§cCosmos: " + req.body() + " has no walkable surface in this world."));
            if (parked != null) { IN_SPACE.put(p.getUUID(), parked); p.setNoGravity(true); }
            return;
        }
        PlanetProjection proj = Planets.projection(req.body());
        double x = proj.x(Math.toRadians(req.lonDeg())), z = proj.z(Math.toRadians(req.latDeg()));
        ColumnSampler s = new ColumnSampler(req.body());
        int ground = ColumnSampler.surfaceY(s.elevation(Math.floor(x), Math.floor(z)));
        int startY = Math.min(SurfaceScale.LAUNCH_Y - 40, Math.max(ground, SurfaceScale.DATUM_Y) + 60);
        p.teleport(new TeleportTransition(target, new Vec3(Math.floor(x) + 0.5, startY, Math.floor(z) + 0.5), Vec3.ZERO, p.getYRot(), 20, TeleportTransition.DO_NOTHING));
        p.resetFallDistance();
        DESCENDING.add(p.getUUID());      // final approach: the lander absorbs the touchdown
        sendInfo(p, req.body());
        p.sendSystemMessage(Component.literal(String.format("§bTouching down on %s at %.3f, %.3f  (g = %.2f m/s²)", Planets.body(req.body()).name,
                req.latDeg(), req.lonDeg(), Planets.surfaceGravity(req.body()))));
    }

    public static void sendInfo(ServerPlayer p, String body) {
        if (ServerPlayNetworking.canSend(p, CosmosPayloads.WorldInfo.TYPE))
            ServerPlayNetworking.send(p, new CosmosPayloads.WorldInfo(Planets.overworldIsEarth, body == null ? "" : body));
    }

    public static void forget(ServerPlayer p) { IN_SPACE.remove(p.getUUID()); DESCENDING.remove(p.getUUID()); }
}
