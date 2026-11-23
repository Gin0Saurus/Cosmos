package dev.saurus.cosmos;

import dev.saurus.cosmos.net.CosmosPayloads;
import dev.saurus.cosmos.world.CosmosBiomeSource;
import dev.saurus.cosmos.world.CosmosChunkGenerator;
import dev.saurus.cosmos.world.PlanetTravel;
import dev.saurus.cosmos.world.Planets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Common (server + client) initialisation: world generation types, networking, planetary travel. */
public final class CosmosMod implements ModInitializer {
    /** First start of a Cosmos Universe world: spawn on Westminster Bridge Road, a short walk south of Big Ben. */
    private static void spawnInLondon(net.minecraft.server.MinecraftServer server) {
        try {
            java.nio.file.Path marker = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("cosmos_spawn_set");
            if (java.nio.file.Files.exists(marker)) return;
            var proj = Planets.projection("earth");
            int x = (int) Math.floor(proj.x(Math.toRadians(-0.1246))) + 12, z = (int) Math.floor(proj.z(Math.toRadians(51.5007))) + 24;
            int y = Math.max(dev.saurus.cosmos.world.ColumnSampler.surfaceY(new dev.saurus.cosmos.world.ColumnSampler("earth").elevation(x, z)),
                    dev.saurus.cosmos.core.terrain.SurfaceScale.DATUM_Y) + 1;
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "setworldspawn " + x + " " + y + " " + z);
            java.nio.file.Files.writeString(marker, "spawn set by Cosmos at " + x + " " + y + " " + z);
        } catch (Exception e) { System.err.println("[Cosmos] could not set the London spawn: " + e); }
    }

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.fromNamespaceAndPath("cosmos", "planet"), CosmosChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Identifier.fromNamespaceAndPath("cosmos", "planet"), CosmosBiomeSource.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(CosmosPayloads.Launch.TYPE, CosmosPayloads.Launch.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CosmosPayloads.WorldInfo.TYPE, CosmosPayloads.WorldInfo.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CosmosPayloads.Land.TYPE, CosmosPayloads.Land.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CosmosPayloads.Land.TYPE, (payload, ctx) -> ctx.server().execute(() -> PlanetTravel.land(ctx.player(), payload)));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Planets.overworldIsEarth = server.overworld().getChunkSource().getGenerator() instanceof CosmosChunkGenerator g && g.body().equals("earth");
            Thread t = new Thread(() -> Planets.terrain("earth"), "cosmos-terrain-warmup");   // loads elevation grids off-thread
            t.setDaemon(true); t.start();
            if (Planets.overworldIsEarth) spawnInLondon(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PlanetTravel.sendInfo(handler.getPlayer(), Planets.bodyOf(handler.getPlayer().level())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PlanetTravel.forget(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(PlanetTravel::tick);
    }
}
