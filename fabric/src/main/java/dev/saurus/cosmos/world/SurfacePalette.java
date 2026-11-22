package dev.saurus.cosmos.world;

import dev.saurus.cosmos.core.terrain.Surface;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Blocks and biome for each surface type. Earth uses vanilla biomes so trees, grass, animals and villages work. */
public final class SurfacePalette {
    private SurfacePalette() {}
    public record Layers(BlockState top, BlockState under, int underDepth, BlockState rock, String biome) {}

    /** Looks a vanilla block up by id (26.3 keeps coloured blocks in ColorCollections, so ids are the stable handle). */
    public static BlockState b(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", id)).defaultBlockState();
    }

    private static Layers L(BlockState top, BlockState under, int d, BlockState rock, String biome) { return new Layers(top, under, d, rock, biome); }

    public static Layers of(Surface s, double tempC) {
        BlockState stone = Blocks.STONE.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState();
        return switch (s) {
            case OCEAN -> L(tempC > 10 ? Blocks.SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState(), Blocks.SAND.defaultBlockState(), 3, stone,
                    tempC > 24 ? "minecraft:warm_ocean" : tempC > 16 ? "minecraft:lukewarm_ocean" : tempC < 6 ? "minecraft:cold_ocean" : "minecraft:ocean");
            case DEEP_OCEAN -> L(Blocks.GRAVEL.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), 2, stone,
                    tempC > 16 ? "minecraft:deep_lukewarm_ocean" : tempC < 6 ? "minecraft:deep_cold_ocean" : "minecraft:deep_ocean");
            case FROZEN_OCEAN -> L(Blocks.GRAVEL.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), 2, stone, "minecraft:frozen_ocean");
            case BEACH -> L(Blocks.SAND.defaultBlockState(), Blocks.SAND.defaultBlockState(), 4, Blocks.SANDSTONE.defaultBlockState(), "minecraft:beach");
            case ICE_SHEET -> L(Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState(), 40, stone, "minecraft:ice_spikes");
            case SNOW -> L(grass, dirt, 3, stone, "minecraft:snowy_plains");
            case TUNDRA -> L(grass, dirt, 2, stone, "minecraft:snowy_plains");
            case TAIGA -> L(grass, dirt, 3, stone, tempC < 2 ? "minecraft:snowy_taiga" : "minecraft:taiga");
            case FOREST -> L(grass, dirt, 4, stone, tempC > 14 ? "minecraft:birch_forest" : "minecraft:forest");
            case GRASSLAND -> L(grass, dirt, 4, stone, tempC > 12 ? "minecraft:sunflower_plains" : "minecraft:plains");
            case SAVANNA -> L(grass, dirt, 3, stone, "minecraft:savanna");
            case DESERT -> L(Blocks.SAND.defaultBlockState(), Blocks.SAND.defaultBlockState(), 5, Blocks.SANDSTONE.defaultBlockState(), "minecraft:desert");
            case JUNGLE -> L(grass, dirt, 4, stone, "minecraft:jungle");
            case MOUNTAIN -> L(stone, stone, 1, stone, "minecraft:stony_peaks");
            case SNOWY_PEAKS -> L(Blocks.SNOW_BLOCK.defaultBlockState(), stone, 1, stone, "minecraft:frozen_peaks");

            case LUNAR_HIGHLAND -> L(b("light_gray_concrete_powder"), b("light_gray_concrete_powder"), 3, Blocks.CALCITE.defaultBlockState(), "cosmos:lunar_highland");
            case LUNAR_MARE -> L(b("gray_concrete_powder"), b("gray_concrete_powder"), 2, Blocks.SMOOTH_BASALT.defaultBlockState(), "cosmos:lunar_mare");
            case LUNAR_EJECTA -> L(b("white_concrete_powder"), b("light_gray_concrete_powder"), 2, Blocks.CALCITE.defaultBlockState(), "cosmos:lunar_ejecta");
            case LUNAR_POLAR_ICE -> L(Blocks.PACKED_ICE.defaultBlockState(), b("light_gray_concrete_powder"), 3, Blocks.CALCITE.defaultBlockState(), "cosmos:lunar_polar_ice");

            case MARS_DUST -> L(Blocks.RED_SAND.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(), 3, Blocks.TERRACOTTA.defaultBlockState(), "cosmos:mars_dust");
            case MARS_DARK_SAND -> L(b("brown_concrete_powder"), Blocks.RED_SAND.defaultBlockState(), 3, Blocks.TERRACOTTA.defaultBlockState(), "cosmos:mars_dark_sand");
            case MARS_ROCK -> L(Blocks.TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(), 2, b("red_terracotta"), "cosmos:mars_rock");
            case MARS_VOLCANIC -> L(b("red_terracotta"), Blocks.SMOOTH_BASALT.defaultBlockState(), 3, Blocks.BASALT.defaultBlockState(), "cosmos:mars_volcanic");
            case MARS_LAYERED -> L(b("orange_terracotta"), b("white_terracotta"), 2, Blocks.TERRACOTTA.defaultBlockState(), "cosmos:mars_layered");
            case MARS_POLAR_ICE -> L(Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState(), 40, Blocks.TERRACOTTA.defaultBlockState(), "cosmos:mars_polar_ice");

            case GREY_REGOLITH -> L(b("light_gray_concrete_powder"), b("gray_concrete_powder"), 3, Blocks.ANDESITE.defaultBlockState(), "cosmos:grey_regolith");
            case DARK_REGOLITH -> L(Blocks.TUFF.defaultBlockState(), b("gray_concrete_powder"), 3, Blocks.DEEPSLATE.defaultBlockState(), "cosmos:dark_regolith");
            case BASALT_PLAINS -> L(Blocks.SMOOTH_BASALT.defaultBlockState(), Blocks.BASALT.defaultBlockState(), 4, Blocks.BLACKSTONE.defaultBlockState(), "cosmos:basalt_plains");
            case SULFUR_PLAINS -> L(b("yellow_concrete_powder"), b("yellow_terracotta"), 3, Blocks.BASALT.defaultBlockState(), "cosmos:sulfur_plains");
            case VOLCANIC_VENT -> L(Blocks.BLACKSTONE.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(), 2, Blocks.BASALT.defaultBlockState(), "cosmos:volcanic_vent");
            case CLEAN_ICE -> L(Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState(), 6, Blocks.BLUE_ICE.defaultBlockState(), "cosmos:clean_ice");
            case DIRTY_ICE -> L(Blocks.PACKED_ICE.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState(), 6, Blocks.BLUE_ICE.defaultBlockState(), "cosmos:dirty_ice");
            case DARK_ICE -> L(b("gray_concrete_powder"), Blocks.PACKED_ICE.defaultBlockState(), 4, Blocks.BLUE_ICE.defaultBlockState(), "cosmos:dark_ice");
            case NITROGEN_ICE -> L(b("white_concrete"), Blocks.PACKED_ICE.defaultBlockState(), 8, Blocks.PACKED_ICE.defaultBlockState(), "cosmos:nitrogen_ice");
            case THOLIN -> L(b("brown_terracotta"), Blocks.PACKED_ICE.defaultBlockState(), 3, Blocks.PACKED_ICE.defaultBlockState(), "cosmos:tholin");
            case ORGANIC_DUNES -> L(b("brown_concrete_powder"), b("brown_concrete_powder"), 5, Blocks.PACKED_ICE.defaultBlockState(), "cosmos:organic_dunes");
            case ORGANIC_SHORE -> L(Blocks.MUD.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState(), 3, Blocks.PACKED_ICE.defaultBlockState(), "cosmos:organic_shore");
        };
    }

    /** Every biome id any body can produce (for BiomeSource.possibleBiomes). */
    public static java.util.Set<String> allBiomes() {
        java.util.Set<String> s = new java.util.LinkedHashSet<>();
        for (Surface x : Surface.values()) for (double t : new double[] { -20, 0, 8, 13, 15, 20, 30 }) s.add(of(x, t).biome());
        return s;
    }
}
