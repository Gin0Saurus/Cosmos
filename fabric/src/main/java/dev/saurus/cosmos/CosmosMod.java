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
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PlanetTravel.sendInfo(handler.getPlayer(), Planets.bodyOf(handler.getPlayer().level())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PlanetTravel.forget(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(PlanetTravel::tick);
    }
}
