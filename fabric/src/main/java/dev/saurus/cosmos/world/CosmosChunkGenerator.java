package dev.saurus.cosmos.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.saurus.cosmos.core.terrain.Surface;
import dev.saurus.cosmos.core.terrain.SurfaceScale;
import dev.saurus.cosmos.core.terrain.TerrainColumn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Generates a body's surface chunk by chunk, on demand, from its terrain model (real elevation data + procedural
 * detail). Nothing is pre-generated: a chunk is built only when a player gets near it, and the same coordinates always
 * produce the same terrain. Vanilla decoration (trees, ores, grass, flowers) then runs from the biomes.
 *
 * <p>Column layout: bedrock floor ~{@value #CRUST} blocks below the surface (engine limitation: the planet is a shell,
 * not a full ball), body rock, sub-surface layer, top layer, then water/lava/methane up to the liquid level.
 */
public final class CosmosChunkGenerator extends ChunkGenerator {
    public static final MapCodec<CosmosChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            Codec.STRING.fieldOf("body").forGetter(g -> g.body)
    ).apply(i, CosmosChunkGenerator::new));

    static final int CRUST = 96;
    final String body;
    private volatile ColumnSampler sampler;

    public CosmosChunkGenerator(BiomeSource biomes, String body) {
        super(biomes);
        this.body = body;
    }

    public String body() { return body; }

    private ColumnSampler sampler() {
        ColumnSampler s = sampler;
        if (s == null) sampler = s = new ColumnSampler(body);
        return s;
    }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    /** A fully resolved column: block Y of the top solid block, liquid top (or Integer.MIN_VALUE), layers. */
    record Col(int top, int liquidTop, BlockState liquid, SurfacePalette.Layers layers, Surface surface, boolean frozen) {}

    private Col resolve(ColumnSampler s, int bx, int bz, double e, double slope) {
        TerrainColumn c = s.column(bx, bz, e, slope);
        double t = s.temperature(bx, bz, e);
        SurfacePalette.Layers L = SurfacePalette.of(c.surface(), t);
        int top = Math.max(SurfaceScale.MIN_Y + 8, Math.min(SurfaceScale.MAX_Y - 40, ColumnSampler.surfaceY(e)));
        int lq = Integer.MIN_VALUE; BlockState liquid = null;
        if (c.liquid() != TerrainColumn.Liquid.NONE) {
            lq = (int) Math.floor(SurfaceScale.blockYForElevation(c.liquidM())) - (c.liquid() == TerrainColumn.Liquid.WATER && body.equals("earth") ? 1 : 0);
            liquid = c.liquid() == TerrainColumn.Liquid.LAVA ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState();
            if (lq <= top) lq = Integer.MIN_VALUE;
        }
        return new Col(top, lq, liquid, L, c.surface(), c.surface() == Surface.FROZEN_OCEAN);
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState random, StructureManager structures,
                                                      BiomeManager biomeManager, WorldGenRegion region, Set<Holder<Biome>> biomes) {
        ColumnSampler s = sampler();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        double[] g = s.chunkGrid(x0, z0);
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int minY = chunk.getMinY();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState(), deepslate = Blocks.DEEPSLATE.defaultBlockState();
        boolean earth = body.equals("earth");
        for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
            double e = g[(dz + 1) * 18 + dx + 1];
            double ex = g[(dz + 1) * 18 + dx + 2] - g[(dz + 1) * 18 + dx], ez = g[(dz + 2) * 18 + dx + 1] - g[dz * 18 + dx + 1];
            double slope = Math.hypot(ex, ez) / (2 * SurfaceScale.METRES_PER_BLOCK_H);
            Col c = resolve(s, x0 + dx, z0 + dz, e, slope);
            int floor = Math.max(minY, c.top - CRUST);
            int topWithLiquid = Math.max(c.top, c.liquidTop);
            for (int y = floor; y <= topWithLiquid; y++) {
                BlockState st;
                if (y <= floor + 1) st = bedrock;
                else if (y > c.top) st = (c.frozen && y == c.liquidTop) ? Blocks.ICE.defaultBlockState() : c.liquid;
                else if (y == c.top) st = c.layers.top();
                else if (y > c.top - 1 - c.layers.underDepth()) st = c.layers.under();
                else if (c.surface == Surface.MARS_LAYERED) st = layered(y);
                else st = earth && y < SurfaceScale.DATUM_Y - 70 ? deepslate : c.layers.rock();
                if (st == null) continue;
                set(chunk, dx, y, dz, st);
            }
            ocean.update(dx, c.top, dz, c.layers.top());
            surface.update(dx, topWithLiquid, dz, topWithLiquid > c.top ? c.liquid == null ? c.layers.top() : c.liquid : c.layers.top());
        }
        return CompletableFuture.completedFuture(chunk);
    }

    private static final BlockState[] BANDS = {
            SurfacePalette.b("white_terracotta"), SurfacePalette.b("orange_terracotta"), Blocks.TERRACOTTA.defaultBlockState(),
            SurfacePalette.b("red_terracotta"), SurfacePalette.b("brown_terracotta"), SurfacePalette.b("light_gray_terracotta") };
    private static BlockState layered(int y) { return BANDS[Math.floorMod(y * 7 / 5 + (y >> 4), BANDS.length)]; }

    private static void set(ChunkAccess chunk, int x, int y, int z, BlockState st) {
        LevelChunkSection sec = chunk.getSection(chunk.getSectionIndex(y));
        sec.setBlockState(x, y & 15, z, st, false);
    }

    private Col columnAt(int x, int z) {
        ColumnSampler s = sampler();
        double e = s.elevation(x, z);
        double ex = s.elevation(x + 1, z) - s.elevation(x - 1, z), ez = s.elevation(x, z + 1) - s.elevation(x, z - 1);
        return resolve(s, x, z, e, Math.hypot(ex, ez) / (2 * SurfaceScale.METRES_PER_BLOCK_H));
    }

    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        Col c = columnAt(x, z);
        boolean solidOnly = type == Heightmap.Types.OCEAN_FLOOR_WG || type == Heightmap.Types.OCEAN_FLOOR;
        return (solidOnly ? c.top : Math.max(c.top, c.liquidTop)) + 1;
    }

    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        Col c = columnAt(x, z);
        int minY = level.getMinY(), h = level.getHeight();
        BlockState[] states = new BlockState[h];
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int i = 0; i < h; i++) {
            int y = minY + i;
            states[i] = y <= c.top ? (y == c.top ? c.layers.top() : c.layers.rock()) : y <= c.liquidTop ? c.liquid : air;
        }
        return new NoiseColumn(minY, states);
    }

    @Override public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos, SamplerContext ctx) {
        ColumnSampler s = sampler();
        double[] ll = s.proj.latLon(pos.getX() + 0.5, pos.getZ() + 0.5);
        double e = s.elevation(pos.getX(), pos.getZ());
        info.add(String.format("Cosmos %s: lat %.4f, lon %.4f, surface elevation %.0f m", body, Math.toDegrees(ll[0]), Math.toDegrees(ll[1]), e));
        info.add("Cosmos terrain: " + s.terrain.provenance());
    }

    @Override public void spawnOriginalMobs(WorldGenRegion region) {}
    @Override public int getGenDepth() { return SurfaceScale.HEIGHT; }
    @Override public int getSeaLevel() { return SurfaceScale.DATUM_Y; }
    @Override public int getMinY() { return SurfaceScale.MIN_Y; }
}
