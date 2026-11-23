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

    private volatile dev.saurus.cosmos.core.terrain.Caves caves;
    private dev.saurus.cosmos.core.terrain.Caves caves() {
        dev.saurus.cosmos.core.terrain.Caves c = caves;
        if (c == null) caves = c = new dev.saurus.cosmos.core.terrain.Caves(body, Planets.PLANET_SEED, sampler().proj.circumference());
        return c;
    }

    /** How deep below the surface each cave style is evaluated (cost control). */
    private static int caveDepth(dev.saurus.cosmos.core.terrain.Caves.Style st) {
        return switch (st) { case EARTH -> CRUST; case LAVA_TUBES -> 40; case ICE -> 60; case SPARSE -> 50; case NONE -> 0; };
    }

    private static boolean falls(BlockState st) { return st.getBlock() instanceof net.minecraft.world.level.block.FallingBlock; }

    /** Resources in body rock (deterministic per block). Moon: ilmenite (FeTiO3) in mare basalt; Mars: iron oxides. */
    private BlockState resource(int x, int y, int z, Surface s, BlockState rock) {
        double h = dev.saurus.cosmos.core.terrain.Noise.hash01(0x0E5L, x >> 1, y >> 1, z >> 1);
        switch (body) {
            case "moon": if (s == Surface.LUNAR_MARE && h < 0.012) return Blocks.IRON_ORE.defaultBlockState(); break;
            case "mars": if (h < 0.008) return Blocks.IRON_ORE.defaultBlockState(); break;
            case "mercury": if (h < 0.006) return Blocks.IRON_ORE.defaultBlockState(); break;
            default: break;
        }
        return rock;
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
        int[] tops = new int[256], solidTops = new int[256];
        BlockState[] topStates = new BlockState[256], solidStates = new BlockState[256];
        dev.saurus.cosmos.core.terrain.Caves cv = caves();
        int cdepth = caveDepth(cv.style);
        BlockState air = Blocks.AIR.defaultBlockState(), lava = Blocks.LAVA.defaultBlockState(), packedIce = Blocks.PACKED_ICE.defaultBlockState();
        for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
            int bx = x0 + dx, bz = z0 + dz;
            double e = g[(dz + 1) * 18 + dx + 1];
            double ex = g[(dz + 1) * 18 + dx + 2] - g[(dz + 1) * 18 + dx], ez = g[(dz + 2) * 18 + dx + 1] - g[dz * 18 + dx + 1];
            double slope = Math.hypot(ex, ez) / (2 * SurfaceScale.METRES_PER_BLOCK_H);
            Col c = resolve(s, bx, bz, e, slope);
            double[] ll = s.proj.latLon(bx + 0.5, bz + 0.5);
            double[] u = dev.saurus.cosmos.core.terrain.PlanetProjection.unit(ll[0], ll[1]);
            boolean underLiquid = c.liquidTop > c.top;
            boolean groundIce = body.equals("mars") && Math.abs(Math.toDegrees(ll[0])) > 50;   // SHARAD/Phoenix: buried water ice
            int floor = Math.max(minY, c.top - CRUST);
            int topWithLiquid = Math.max(c.top, c.liquidTop);
            boolean carvedBelow = false;
            for (int y = floor; y <= topWithLiquid; y++) {
                BlockState st;
                int depth = c.top - y;
                if (y <= floor + 1) st = bedrock;
                else if (y > c.top) st = (c.frozen && y == c.liquidTop) ? Blocks.ICE.defaultBlockState() : c.liquid;
                else if (y == c.top) st = c.layers.top();
                else if (depth <= c.layers.underDepth()) st = c.layers.under();
                else if (groundIce && depth <= c.layers.underDepth() + 10) st = packedIce;
                else if (c.surface == Surface.MARS_LAYERED) st = layered(y);
                else st = resource(bx, y, bz, c.surface, earth && y < SurfaceScale.DATUM_Y - 70 ? deepslate : c.layers.rock());
                if (st == null) continue;
                // caves: never within 8 blocks of a sea/lake bed, never through the bedrock floor
                if (y > floor + 3 && depth < cdepth && y <= c.top && !(underLiquid && depth < 8)
                        && cv.carve(u, y, depth, c.surface) < 0) {
                    carvedBelow = true;
                    if (earth && depth > 70) set(chunk, dx, y, dz, lava);
                    continue;                      // leave air
                }
                if (carvedBelow && falls(st)) st = c.layers.rock();   // no sand/regolith hanging over a void
                carvedBelow = false;
                set(chunk, dx, y, dz, st);
            }
            int ci = dz * 16 + dx;
            tops[ci] = topWithLiquid; solidTops[ci] = c.top; solidStates[ci] = c.layers.top();
            topStates[ci] = topWithLiquid > c.top && c.liquid != null ? c.liquid : c.layers.top();
        }
        placeLandmarks(chunk, x0, z0, tops, solidTops, topStates, solidStates);
        for (int i = 0; i < 256; i++) {
            ocean.update(i & 15, solidTops[i], i >> 4, solidStates[i]);
            surface.update(i & 15, tops[i], i >> 4, topStates[i]);
        }
        return CompletableFuture.completedFuture(chunk);
    }

    /** Ground level of each landmark (computed once from the terrain model, so every chunk agrees). */
    private final java.util.Map<String, Integer> landmarkBase = new java.util.concurrent.ConcurrentHashMap<>();

    private void placeLandmarks(ChunkAccess chunk, int x0, int z0, int[] tops, int[] solidTops, BlockState[] topStates, BlockState[] solidStates) {
        ColumnSampler s = sampler();
        for (dev.saurus.cosmos.core.terrain.Landmarks.Landmark l : dev.saurus.cosmos.core.terrain.Landmarks.on(body)) {
            int ox = (int) Math.floor(s.proj.x(Math.toRadians(l.lon()))), oz = (int) Math.floor(s.proj.z(Math.toRadians(l.lat())));
            int pad = l.radius() + 3;
            if (ox + pad < x0 || ox - pad > x0 + 15 || oz + pad < z0 || oz - pad > z0 + 15) continue;
            int base = landmarkBase.computeIfAbsent(l.id(), k -> {
                // level the pad at the median ground height of the footprint, at least at sea level
                int[] hs = new int[25]; int n = 0;
                for (int i = -2; i <= 2; i++) for (int k2 = -2; k2 <= 2; k2++)
                    hs[n++] = ColumnSampler.surfaceY(s.elevation(ox + i * l.radius() / 2.0, oz + k2 * l.radius() / 2.0));
                java.util.Arrays.sort(hs);
                return Math.max(hs[12], body.equals("earth") ? SurfaceScale.DATUM_Y - 1 : Integer.MIN_VALUE) + 1;
            });
            BlockState found = LandmarkPainter.foundation(l);
            for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
                int lx = x0 + dx - ox, lz = z0 + dz - oz;
                if (Math.abs(lx) > pad || Math.abs(lz) > pad) continue;
                int top = tops[dz * 16 + dx];
                for (int y = Math.min(top, base - 1) + 1; y <= base - 1; y++) set(chunk, dx, y, dz, found);        // fill up to the pad
                for (int y = base; y <= Math.min(top, base + l.height() + 2); y++) set(chunk, dx, y, dz, Blocks.AIR.defaultBlockState()); // cut down to it
                set(chunk, dx, base - 1, dz, found);
                int hi = base - 1;
                for (int ly = 0; ly <= l.height(); ly++) {
                    BlockState st = LandmarkPainter.at(l, lx, ly, lz);
                    if (st != null) { set(chunk, dx, base + ly, dz, st); hi = base + ly; }
                }
                int ci = dz * 16 + dx;
                tops[ci] = solidTops[ci] = hi; topStates[ci] = solidStates[ci] = found;
            }
        }
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

    /** Earth gets its biomes' animals as terrain generates, like vanilla. Nowhere else has known life. */
    @Override public void spawnOriginalMobs(WorldGenRegion region) {
        if (!body.equals("earth")) return;
        net.minecraft.world.level.ChunkPos pos = region.getCenter();
        BlockPos at = pos.getWorldPosition().atY(region.getMaxY());
        net.minecraft.world.level.levelgen.WorldgenRandom r = new net.minecraft.world.level.levelgen.WorldgenRandom(
                new net.minecraft.world.level.levelgen.LegacyRandomSource(net.minecraft.world.level.levelgen.RandomSupport.generateUniqueSeed()));
        r.setDecorationSeed(region.getSeed(), pos.getMinBlockX(), pos.getMinBlockZ());
        net.minecraft.world.level.NaturalSpawner.spawnMobsForChunkGeneration(region, at, pos, r);
    }
    @Override public int getGenDepth() { return SurfaceScale.HEIGHT; }
    @Override public int getSeaLevel() { return SurfaceScale.DATUM_Y; }
    @Override public int getMinY() { return SurfaceScale.MIN_Y; }
}
