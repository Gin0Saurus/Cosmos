package dev.saurus.cosmos.test;

import dev.saurus.cosmos.client.CosmosCommands;
import dev.saurus.cosmos.client.CosmosState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Launches the real Minecraft 26.3 client with Cosmos, creates a world and photographs the universe.
 * Run by CI (xvfb + Mesa); screenshots are published for inspection.
 */
public final class CosmosClientGameTest implements FabricClientGameTest {
    private static final String[] TOUR = {"Earth", "Moon", "Saturn", "Jupiter", "Mars", "Proxima Centauri b", "TRAPPIST-1",
            "Sirius", "Gaia BH3", "Sagittarius A*", "Andromeda"};

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(mc -> CosmosState.universe() != null, 20 * 60);
            // overworld night sky with real stars
            world.getServer().runCommand("time set midnight");
            world.getServer().runCommand("weather clear");
            context.runOnClient(mc -> { if (mc.player != null) { mc.player.setXRot(-55f); mc.player.setYRot(180f); } });
            context.waitTicks(60);
            context.takeScreenshot("cosmos-00-overworld-night-sky");
            int i = 1;
            for (String target : TOUR) {
                context.runOnClient(mc -> CosmosCommands.goTo(target).forEach(System.out::println));
                context.waitTicks(80); // star field + Milky Way texture rebuild asynchronously
                context.takeScreenshot(String.format("cosmos-%02d-%s", i++, target.replaceAll("[^A-Za-z0-9]+", "_")));
            }
            context.runOnClient(mc -> CosmosState.spaceMode = false);
            context.waitTicks(10);
        }
    }
}
