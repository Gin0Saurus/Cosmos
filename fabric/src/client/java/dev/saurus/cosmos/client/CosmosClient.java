package dev.saurus.cosmos.client;

import dev.saurus.cosmos.client.render.CosmosRenderer;
import dev.saurus.cosmos.client.render.ShipMotion;
import dev.saurus.cosmos.core.Universe;
import dev.saurus.cosmos.core.coords.UniversePos;
import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.nav.TravelPlanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import dev.saurus.cosmos.net.CosmosPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

public final class CosmosClient implements ClientModInitializer {
    private static Vec3 anchor = null;

    @Override
    public void onInitializeClient() {
        // Catalogues load in the background (~2-3 s): 112k stars, 5.4k exoplanets, 12k deep-sky objects.
        CosmosState.universeFuture = CompletableFuture.supplyAsync(() -> {
            try { return Universe.load(); } catch (Exception e) { throw new RuntimeException("Cosmos: failed to load catalogues", e); }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> CosmosCommands.register(dispatcher));
        ClientPlayNetworking.registerGlobalReceiver(CosmosPayloads.Launch.TYPE, (payload, ctx) -> ctx.client().execute(() -> SurfaceLink.onLaunch(payload)));
        ClientPlayNetworking.registerGlobalReceiver(CosmosPayloads.WorldInfo.TYPE, (payload, ctx) -> ctx.client().execute(() -> SurfaceLink.onWorldInfo(payload)));

        // Overworld stars and the space view are drawn from mixins (SkyRendererMixin, GameRendererMixin),
        // at points where Minecraft has no render pass open.

        // While flying in space the Minecraft body stays parked where it was.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) { anchor = null; return; }
            if (CosmosState.spaceMode) {
                SurfaceLink.tick();
                if (anchor == null) anchor = mc.player.position();
                mc.player.setDeltaMovement(Vec3.ZERO);
                mc.player.setPos(anchor.x, anchor.y, anchor.z);
            } else anchor = null;
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("cosmos", "hud"), (g, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!CosmosState.spaceMode) return;
            var u = CosmosState.universe();
            if (u == null) return;
            int x = 6, y = 6, white = 0xFFFFFFFF, grey = 0xFFAAAAAA, cyan = 0xFF66DDFF;
            double t = CosmosState.time.seconds();
            g.text(mc.font, "COSMOS  space mode   (/cosmos help)", x, y, cyan, true); y += 11;
            if (CosmosState.targetName != null && CosmosState.targetPos != null)
                { g.text(mc.font, "Target: " + CosmosState.targetName + "  " + Units.formatDistance(CosmosState.shipPos.distanceTo(CosmosState.targetPos)), x, y, white, true); y += 11; }
            g.text(mc.font, "Nearest surface: " + Units.formatDistance(ShipMotion.lastNearest), x, y, grey, true); y += 11;
            double v = ShipMotion.lastSpeed;
            String speed = v < 1e3 ? String.format("%.0f m/s", v) : v < 0.01 * Units.C ? String.format("%,.1f km/s", v / 1e3)
                    : v < Units.C ? String.format("%.3f c", v / Units.C) : String.format("%.4g c  (WARP - gameplay, not physics)", v / Units.C);
            g.text(mc.font, "Speed: " + speed, x, y, v > Units.C ? 0xFFFFAA44 : grey, true); y += 11;
            UniversePos p = CosmosState.shipPos;
            g.text(mc.font, "From the Sun: " + Units.formatDistance(UniversePos.ORIGIN.distanceTo(p)), x, y, grey, true); y += 11;
            var date = Instant.ofEpochMilli((long) ((CosmosState.time.julianDate() - 2440587.5) * 86400000.0)).atZone(ZoneOffset.UTC);
            g.text(mc.font, String.format("Date: %tF %<tR UTC   time x%.4g", date, CosmosState.time.warp()), x, y, grey, true);
        });
    }

    @SuppressWarnings("unused") private static String fmt(double s) { return TravelPlanner.formatDuration(s); }
}
