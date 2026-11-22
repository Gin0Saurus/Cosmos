package dev.saurus.cosmos.test;

import dev.saurus.cosmos.client.CosmosCommands;
import dev.saurus.cosmos.client.CosmosState;
import dev.saurus.cosmos.client.SurfaceLink;
import dev.saurus.cosmos.core.coords.UniversePos;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.terrain.PlanetProjection;
import dev.saurus.cosmos.core.terrain.SurfaceScale;
import dev.saurus.cosmos.world.ColumnSampler;
import dev.saurus.cosmos.world.Planets;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Launches the real Minecraft 26.3 client with Cosmos and actually goes places: lands on Earth, the Moon, Mars and
 * other worlds at real coordinates, checks gravity by jumping, wraps around a planet, launches into space and lands
 * again by flying down. Screenshots and a results file are published by CI for inspection.
 */
public final class CosmosClientGameTest implements FabricClientGameTest {
    record Stop(String name, String body, double lat, double lon, float yaw, float pitch) {}
    private static final Stop[] SURFACE_TOUR = {
            new Stop("Earth-Alps-Mont-Blanc", "earth", 45.80, 6.80, 45, 8),
            new Stop("Earth-Himalaya-Everest", "earth", 27.90, 86.85, 30, 6),
            new Stop("Earth-Amazon", "earth", -3.0, -60.0, 0, 12),
            new Stop("Earth-Sahara", "earth", 23.0, 13.0, 0, 10),
            new Stop("Earth-Greenland-ice", "earth", 72.0, -40.0, 0, 10),
            new Stop("Earth-English-coast", "earth", 50.75, 0.25, 180, 10),
            new Stop("Moon-Apollo-11", "moon", 0.674, 23.473, 90, 6),
            new Stop("Moon-Tycho-highlands", "moon", -43.3, -11.4, 0, 8),
            new Stop("Mars-Jezero", "mars", 18.44, 77.45, 270, 6),
            new Stop("Mars-Valles-Marineris", "mars", -10.9, -59.2, 0, 22),
            new Stop("Mars-Olympus-Mons-flank", "mars", 18.0, -131.0, 270, 4),
            new Stop("Mars-north-polar-cap", "mars", 84.5, 0.0, 0, 8),
            new Stop("Europa", "europa", 10.0, 30.0, 0, 8),
            new Stop("Io", "io", 5.0, 10.0, 0, 10),
            new Stop("Titan-lakes", "titan", 70.0, -45.0, 0, 10),
            new Stop("Pluto-Sputnik-Planitia-edge", "pluto", 25.0, 160.0, 90, 8),
    };
    private final List<String> results = new ArrayList<>();
    private void log(String s) { System.out.println("[cosmos-test] " + s); results.add(s); }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(mc -> CosmosState.universe() != null, 20 * 60);
            context.runOnClient(mc -> { mc.options.renderDistance().set(8); });
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("gamerule doDaylightCycle false");
            world.getServer().runCommand("gamemode creative @a");

            int i = 0;
            for (Stop s : SURFACE_TOUR) {
                try { visit(context, world, s, i++); }
                catch (Throwable t) { log("FAIL visiting " + s.name() + ": " + t); }
            }
            try { gravityCheck(context, world); } catch (Throwable t) { log("FAIL gravity: " + t); }
            try { wrapCheck(context, world); } catch (Throwable t) { log("FAIL wrap-around: " + t); }
            try { launchAndLand(context, world); } catch (Throwable t) { log("FAIL launch/land: " + t); }
            try { spaceTour(context); } catch (Throwable t) { log("FAIL space tour: " + t); }
        } finally {
            try { java.nio.file.Files.write(java.nio.file.Path.of("cosmos-test-results.txt"), results); } catch (Exception ignored) {}
        }
    }

    private void land(ClientGameTestContext context, TestSingleplayerContext world, String body, double lat, double lon) {
        context.runOnClient(mc -> SurfaceLink.requestLanding(body, lat, lon));
        context.waitFor(mc -> mc.level != null && body.equals(Planets.bodyOf(mc.level)) && mc.player != null, 20 * 60);
        // skip the descent in the test: put the player straight on the ground at the requested spot
        PlanetProjection proj = Planets.projection(body);
        double x = Math.floor(proj.x(Math.toRadians(lon))) + 0.5, z = Math.floor(proj.z(Math.toRadians(lat))) + 0.5;
        int ground = ColumnSampler.surfaceY(new ColumnSampler(body).elevation(Math.floor(x), Math.floor(z)));
        int y = Math.max(ground, SurfaceScale.DATUM_Y - 1) + 2;
        world.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute as @a at @s run tp @s %.1f %d %.1f", x, y + 1, z));
        context.waitFor(mc -> mc.player != null && Math.abs(mc.player.getX() - x) < 2, 20 * 30);
    }

    private void visit(ClientGameTestContext context, TestSingleplayerContext world, Stop s, int i) {
        land(context, world, s.body(), s.lat(), s.lon());
        context.runOnClient(mc -> { mc.player.setYRot(s.yaw()); mc.player.setXRot(s.pitch()); });
        context.waitTicks(260);    // chunk generation + meshing on a software renderer
        double[] info = context.computeOnClient(mc -> {
            double[] ll = Planets.projection(s.body()).latLon(mc.player.getX(), mc.player.getZ());
            return new double[] { Math.toDegrees(ll[0]), Math.toDegrees(ll[1]), mc.player.getY(), SurfaceScale.elevationForBlockY(mc.player.getY()) };
        });
        String biome = context.computeOnClient(mc -> mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(k -> k.toString()).orElse("?"));
        log(String.format("%s: at lat %.3f lon %.3f, y=%.1f (elevation ~%.0f m), biome %s", s.name(), info[0], info[1], info[2], info[3], biome));
        context.takeScreenshot(String.format("cosmos-s%02d-%s", i, s.name()));
    }

    private void gravityCheck(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("gamemode survival @a");
        for (String body : new String[] { "earth", "moon", "mars" }) {
            double[] site = SurfaceLink.SITES.get(body);
            land(context, world, body, site[0], site[1]);
            context.waitFor(mc -> mc.player.onGround(), 20 * 20);
            context.waitTicks(10);
            double g = context.computeOnClient(mc -> mc.player.getGravity());
            double y0 = context.computeOnClient(mc -> mc.player.getY());
            context.runOnClient(mc -> mc.player.jumpFromGround());
            double peak = y0;
            for (int t = 0; t < 200; t++) {
                context.waitTick();
                peak = Math.max(peak, context.computeOnClient(mc -> mc.player.getY()));
                if (t > 5 && context.computeOnClient(mc -> mc.player.onGround())) break;
            }
            log(String.format("gravity %s: per-tick gravity %.5f blocks/tick^2 (Earth 0.08000), ratio %.4f, expected g/g0 = %.4f; jump height %.2f blocks",
                    body, g, g / 0.08, Planets.surfaceGravity(body) / 9.80665, peak - y0));
        }
        world.getServer().runCommand("gamemode creative @a");
    }

    private void wrapCheck(ClientGameTestContext context, TestSingleplayerContext world) {
        land(context, world, "moon", 0, 179.99);
        int C = Planets.projection("moon").circumference();
        double before = context.computeOnClient(mc -> mc.player.getX());
        world.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute as @a at @s run tp @s %.1f ~ ~", C / 2.0 + 3));
        context.waitTicks(40);
        double after = context.computeOnClient(mc -> mc.player.getX());
        double lon = context.computeOnClient(mc -> Math.toDegrees(Planets.projection("moon").latLon(mc.player.getX(), mc.player.getZ())[1]));
        log(String.format("wrap-around: x %.1f -> pushed to %.1f -> now x %.1f (longitude %.3f); circumference %d blocks", before, C / 2.0 + 3, after, lon, C));
    }

    private void launchAndLand(ClientGameTestContext context, TestSingleplayerContext world) {
        land(context, world, "moon", 0.674, 23.473);
        context.waitTicks(40);
        world.getServer().runCommand("execute as @a at @s run tp @s ~ " + (SurfaceScale.LAUNCH_Y + 2) + " ~");
        context.waitFor(mc -> CosmosState.spaceMode, 20 * 20);
        context.waitTicks(80);
        log("launch: left the Moon's surface column, now in space mode at " + context.computeOnClient(mc -> String.format("%.0f km from the Moon's centre",
                CosmosState.shipPos.distanceTo(CosmosState.targetPos) / 1000)));
        context.takeScreenshot("cosmos-l01-launched-above-moon");
        // fly to Mars and descend into it: entry triggers a landing at the point below the ship
        context.runOnClient(mc -> CosmosCommands.goTo("Mars"));
        context.waitTicks(80);
        context.takeScreenshot("cosmos-l02-approaching-mars");
        context.runOnClient(mc -> {
            UniversePos c = CosmosState.targetPos;
            Vec3d d = c.to(CosmosState.shipPos).normalize();
            CosmosState.shipPos = c.add(d.mul(Planets.body("mars").meanRadius() + 3000));
        });
        context.waitFor(mc -> mc.level != null && "mars".equals(Planets.bodyOf(mc.level)), 20 * 60);
        context.waitFor(mc -> mc.player.onGround(), 20 * 60);
        context.waitTicks(200);
        String where = context.computeOnClient(mc -> {
            double[] ll = Planets.projection("mars").latLon(mc.player.getX(), mc.player.getZ());
            return String.format("lat %.2f lon %.2f y %.0f", Math.toDegrees(ll[0]), Math.toDegrees(ll[1]), mc.player.getY());
        });
        log("landing by flying down into Mars: touched down at " + where);
        context.runOnClient(mc -> { mc.player.setXRot(10); });
        context.waitTicks(20);
        context.takeScreenshot("cosmos-l03-landed-on-mars");
    }

    private void spaceTour(ClientGameTestContext context) {
        String[] tour = { "Earth", "Saturn", "Sagittarius A*", "Andromeda" };
        int i = 0;
        for (String t : tour) {
            context.runOnClient(mc -> CosmosCommands.goTo(t));
            context.waitTicks(80);
            context.takeScreenshot(String.format("cosmos-x%02d-%s", i++, t.replaceAll("[^A-Za-z0-9]+", "_")));
        }
        context.runOnClient(mc -> CosmosState.spaceMode = false);
    }
}
