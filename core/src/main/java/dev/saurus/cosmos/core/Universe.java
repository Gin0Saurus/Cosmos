package dev.saurus.cosmos.core;

import dev.saurus.cosmos.core.blackhole.BlackHole;
import dev.saurus.cosmos.core.body.Body;
import dev.saurus.cosmos.core.body.SolarSystem;
import dev.saurus.cosmos.core.catalog.DeepSkyCatalog;
import dev.saurus.cosmos.core.catalog.ExoplanetCatalog;
import dev.saurus.cosmos.core.catalog.StarCatalog;
import dev.saurus.cosmos.core.catalog.StarSystem;
import dev.saurus.cosmos.core.coords.UniversePos;
import dev.saurus.cosmos.core.galaxy.ProceduralStars;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.nav.ObjectIndex;
import dev.saurus.cosmos.core.util.Json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One connected universe: real catalogues + deterministic procedural fill, all in the same coordinates. */
public final class Universe {
    /** Fixed seed: every player's universe is the same universe. Changing it would move every procedural object. */
    public static final long DEFAULT_SEED = 0x5A0_C05_305L;

    public final SolarSystem solarSystem;
    public final StarCatalog stars;
    public final ExoplanetCatalog exoplanets;
    public final DeepSkyCatalog deepSky;
    public final List<BlackHole> blackHoles = new ArrayList<>();
    public final ProceduralStars procedural;
    public final ObjectIndex index;
    private final Map<String, Integer> exoHosts;
    private final Map<Integer, StarSystem> systems = new HashMap<>();

    private Universe(long seed) throws IOException {
        solarSystem = SolarSystem.loadDefault();
        stars = StarCatalog.loadDefault();
        exoplanets = ExoplanetCatalog.loadDefault();
        exoHosts = exoplanets.attachHosts(stars);
        deepSky = DeepSkyCatalog.loadDefault();
        procedural = new ProceduralStars(seed);
        loadBlackHoles();
        index = ObjectIndex.build(this);
    }

    public static Universe load() throws IOException { return new Universe(DEFAULT_SEED); }

    private void loadBlackHoles() {
        for (Object o : Json.arr(solarSystem.raw.get("black_holes"))) {
            Map<String, Object> b = Json.obj(o);
            UniversePos pos;
            double dist = Json.num(b, "dist_pc");
            if (b.containsKey("ra_deg")) {
                Vec3d p = Vec3d.fromRaDec(Math.toRadians(Json.num(b, "ra_deg")), Math.toRadians(Json.num(b, "dec_deg"))).mul(dist);
                pos = UniversePos.ofParsecs(p.x(), p.y(), p.z());
            } else if (b.containsKey("host_dso")) {
                DeepSkyCatalog.Dso d = deepSky.byId(Json.str(b, "host_dso"));
                Vec3d p = d.direction().mul(dist);
                pos = UniversePos.ofParsecs(p.x(), p.y(), p.z());
            } else {
                Integer i = stars.indexOfHyg(Json.str(b, "host_hyg"));
                Vec3d p = stars.positionPc(i, 0).normalize().mul(dist); // direction from catalogue, distance overridden
                pos = UniversePos.ofParsecs(p.x(), p.y(), p.z());
            }
            List<String> aliases = new ArrayList<>();
            for (Object a : Json.arr(b.get("aliases"))) aliases.add(a.toString());
            Object spin = b.get("spin");
            blackHoles.add(new BlackHole(Json.str(b, "name"), Json.num(b, "mass_msun"), spin instanceof Number n ? n.doubleValue() : 0.0,
                    pos, Json.str(b, "source"), aliases));
        }
    }

    /** The star system around catalogue star i: the Solar System for the Sun, catalogued planets, or none. */
    public StarSystem systemOf(String hostName) {
        Integer i = exoHosts.get(hostName);
        if (i == null) return null;
        return systems.computeIfAbsent(i, k -> exoplanets.buildSystem(hostName, stars, k));
    }
    public Map<String, Integer> exoplanetHosts() { return exoHosts; }

    public Body body(String name) { return solarSystem.get(name); }
}
