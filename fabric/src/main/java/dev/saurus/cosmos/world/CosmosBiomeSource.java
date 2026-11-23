package dev.saurus.cosmos.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.saurus.cosmos.core.terrain.TerrainColumn;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/** Biomes follow the terrain model's surface types, so trees and mobs on Earth appear where the real biomes are. */
public final class CosmosBiomeSource extends BiomeSource {
    public static final MapCodec<CosmosBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("body").forGetter(s -> s.body),
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(i, CosmosBiomeSource::new));

    final String body;
    private final Map<String, Holder<Biome>> biomes = new HashMap<>();
    private final Holder<Biome> fallback;
    private volatile ColumnSampler sampler;

    public CosmosBiomeSource(String body, HolderGetter<Biome> getter) {
        this.body = body;
        for (String id : SurfacePalette.allBiomes()) {
            getter.get(ResourceKey.create(Registries.BIOME, Identifier.parse(id))).ifPresent(h -> biomes.put(id, h));
        }
        Holder<Biome> fb = biomes.get(body.equals("earth") ? "minecraft:plains" : "cosmos:grey_regolith");
        this.fallback = fb != null ? fb : biomes.values().iterator().next();
    }

    private ColumnSampler sampler() {
        ColumnSampler s = sampler;
        if (s == null) sampler = s = new ColumnSampler(body);
        return s;
    }

    @Override protected MapCodec<? extends BiomeSource> codec() { return CODEC; }
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() { return biomes.values().stream().distinct(); }

    // small per-thread cache: the engine asks for every 4x4x4 cell, but biomes here only vary horizontally
    private static final ThreadLocal<long[]> KEYS = ThreadLocal.withInitial(() -> { long[] a = new long[1024]; java.util.Arrays.fill(a, Long.MIN_VALUE); return a; });
    @SuppressWarnings("unchecked")
    private static final ThreadLocal<Holder<Biome>[]> VALS = ThreadLocal.withInitial(() -> (Holder<Biome>[]) new Holder[1024]);

    public Holder<Biome> biomeAt(int qx, int qz) {
        long key = ((long) qx << 32) ^ (qz & 0xffffffffL) ^ ((long) body.hashCode() << 17);
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 54);
        long[] ks = KEYS.get(); Holder<Biome>[] vs = VALS.get();
        if (ks[slot] == key && vs[slot] != null) return vs[slot];
        ColumnSampler s = sampler();
        int bx = (qx << 2) + 2, bz = (qz << 2) + 2;
        String near = nearLandmark(s, bx, bz);
        if (near != null) { Holder<Biome> h = biomes.getOrDefault(near, fallback); ks[slot] = key; vs[slot] = h; return h; }
        TerrainColumn c = s.columnAt(bx, bz);
        String id = SurfacePalette.of(c.surface(), s.temperature(bx, bz, c.elevationM())).biome();
        Holder<Biome> h = biomes.getOrDefault(id, fallback);
        ks[slot] = key; vs[slot] = h;
        return h;
    }

    /** Monuments stand in open ground (city parks, fields, the Giza plateau) so forests don't swallow them. */
    private volatile int[][] landmarkBoxes;
    private String nearLandmark(ColumnSampler s, int bx, int bz) {
        if (!body.equals("earth")) return null;
        int[][] boxes = landmarkBoxes;
        if (boxes == null) {
            var list = dev.saurus.cosmos.core.terrain.Landmarks.on("earth");
            boxes = new int[list.size()][];
            for (int i = 0; i < boxes.length; i++) {
                var l = list.get(i);
                boxes[i] = new int[] { (int) Math.floor(s.proj.x(Math.toRadians(l.lon()))), (int) Math.floor(s.proj.z(Math.toRadians(l.lat()))),
                        l.radius() + 60, l.kind() == dev.saurus.cosmos.core.terrain.Landmarks.Kind.PYRAMID ? 1 : 0 };
            }
            landmarkBoxes = boxes;
        }
        for (int[] b : boxes)
            if (Math.abs(bx - b[0]) <= b[2] && Math.abs(bz - b[1]) <= b[2]) return b[3] == 1 ? "minecraft:desert" : "minecraft:plains";
        return null;
    }

    @Override public BiomeResolver createResolver(Climate.Sampler climate) {
        return (x, y, z) -> biomeAt(x, z);
    }
}
