package dev.saurus.cosmos.world;

import dev.saurus.cosmos.core.body.Body;
import dev.saurus.cosmos.core.body.SolarSystem;
import dev.saurus.cosmos.core.terrain.PlanetProjection;
import dev.saurus.cosmos.core.terrain.PlanetTerrain;
import dev.saurus.cosmos.core.terrain.PlanetTerrains;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Server- and client-side registry of walkable bodies: terrain models, map projections, dimension keys, gravity. */
public final class Planets {
    private Planets() {}
    /** Real bodies are the same in every world (like real astronomy): procedural detail uses this fixed seed, not the world seed. */
    public static final long PLANET_SEED = 0x5EED_C05305L;
    public static final double G0 = 9.80665;

    private static volatile SolarSystem solarSystem;
    private static final Map<String, PlanetProjection> PROJ = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, String> DIM_TO_BODY = new HashMap<>();
    private static final Map<String, ResourceKey<Level>> BODY_TO_DIM = new HashMap<>();
    /** Set by the server when the overworld uses the Cosmos Earth generator (Cosmos Universe preset). */
    public static volatile boolean overworldIsEarth;

    static {
        for (String id : PlanetTerrains.walkable()) {
            ResourceKey<Level> k = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("cosmos", id));
            DIM_TO_BODY.put(k, id); BODY_TO_DIM.put(id, k);
        }
    }

    public static SolarSystem solarSystem() {
        SolarSystem s = solarSystem;
        if (s == null) synchronized (Planets.class) {
            if ((s = solarSystem) == null) {
                try { solarSystem = s = SolarSystem.loadDefault(); }
                catch (Exception e) { throw new IllegalStateException("Cosmos: cannot load Solar System data", e); }
            }
        }
        return s;
    }

    public static Body body(String id) { return solarSystem().get(id); }
    public static PlanetTerrain terrain(String id) { return PlanetTerrains.forBody(body(id), PLANET_SEED); }
    public static PlanetProjection projection(String id) { return PROJ.computeIfAbsent(id, k -> PlanetProjection.forRadius(body(k).meanRadius())); }

    public static ResourceKey<Level> dimension(String body) {
        if (body.equals("earth") && overworldIsEarth) return Level.OVERWORLD;
        return BODY_TO_DIM.get(body);
    }

    /** Which body a level is the surface of, or null for ordinary dimensions. */
    public static String bodyOf(Level level) {
        if (level == null) return null;
        String b = DIM_TO_BODY.get(level.dimension());
        if (b == null && overworldIsEarth && level.dimension() == Level.OVERWORLD) b = "earth";
        return b;
    }

    /** Surface gravity g = GM / R^2, in m/s^2 (1 block = 1 m locally). */
    public static double surfaceGravity(String body) {
        Body b = body(body);
        double r = b.meanRadius();
        return b.gm / (r * r);
    }

    private static final Map<String, Double> FACTOR = new ConcurrentHashMap<>();
    /** Multiplier on Minecraft's gravity for entities in this level (1 on Earth and in ordinary dimensions). */
    public static double gravityFactor(Level level) {
        String b = bodyOf(level);
        if (b == null || b.equals("earth")) return 1.0;
        return FACTOR.computeIfAbsent(b, k -> surfaceGravity(k) / G0);
    }
}
