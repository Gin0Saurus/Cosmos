package dev.saurus.cosmos.core.terrain;

import dev.saurus.cosmos.core.body.Body;
import dev.saurus.cosmos.core.terrain.ProceduralTerrain.Profile;
import dev.saurus.cosmos.core.terrain.ProceduralTerrain.Region;
import dev.saurus.cosmos.core.terrain.TerrainColumn.Liquid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.saurus.cosmos.core.terrain.Surface.*;

/** Which bodies are walkable, and the terrain model for each. */
public final class PlanetTerrains {
    private PlanetTerrains() {}

    private static final Map<String, Profile> PROFILES = new LinkedHashMap<>();
    private static void add(Profile p) { PROFILES.put(p.bodyId(), p); }
    private static Profile prof(String id, String notes, double cMax, double cDen, double relief, double relWl, double ridge, double ridgeWl,
                                Surface base, Surface low, Surface high, Surface fresh, Region... regions) {
        return new Profile(id, notes, cMax, cDen, relief, relWl, ridge, ridgeWl, base, low, high, fresh, Liquid.NONE, 0, 0,
                false, false, false, false, List.of(regions));
    }
    static {
        add(prof("mercury", "heavily cratered like the Moon, lobate scarps, Caloris basin (1550 km)", 60_000, 1.0, 1500, 500_000, 900, 60_000,
                GREY_REGOLITH, DARK_REGOLITH, GREY_REGOLITH, LUNAR_EJECTA,
                new Region("Caloris Planitia", 30.5, 170.2, 12.0, -1500, true, GREY_REGOLITH)));
        add(prof("venus", "volcanic basalt plains, ~1000 craters planet-wide, Ishtar/Aphrodite highlands, Maxwell Montes 11 km (positions from Magellan)", 30_000, 0.03, 1100, 900_000, 1600, 150_000,
                BASALT_PLAINS, BASALT_PLAINS, DARK_REGOLITH, null,
                new Region("Ishtar Terra", 70, 15, 16, 3000, false, DARK_REGOLITH), new Region("Maxwell Montes", 65.2, 3.3, 4, 6500, false, DARK_REGOLITH),
                new Region("Aphrodite Terra", -5, 105, 28, 2200, false, DARK_REGOLITH)));
        add(prof("phobos", "tiny, cratered, Stickney crater (9 km)", 3_000, 1.0, 400, 8_000, 0, 1, DARK_REGOLITH, DARK_REGOLITH, DARK_REGOLITH, GREY_REGOLITH,
                new Region("Stickney", -1, -49, 22, -1800, true, GREY_REGOLITH)));
        add(prof("deimos", "tiny, smooth regolith-mantled", 2_000, 0.5, 250, 6_000, 0, 1, DARK_REGOLITH, DARK_REGOLITH, DARK_REGOLITH, GREY_REGOLITH));
        add(new Profile("io", "no impact craters (resurfaced by volcanism), sulfur plains, lava paterae, isolated mountains up to 17 km",
                0, 0, 700, 300_000, 3500, 200_000, SULFUR_PLAINS, SULFUR_PLAINS, SULFUR_PLAINS, null, Liquid.NONE, 0, 0,
                false, false, true, false, List.of()));
        add(new Profile("europa", "young water-ice crust, few craters, global lineae (double ridges)", 25_000, 0.04, 250, 200_000, 0, 1,
                CLEAN_ICE, CLEAN_ICE, DIRTY_ICE, CLEAN_ICE, Liquid.NONE, 0, 0, true, false, false, false, List.of()));
        add(prof("ganymede", "bright grooved terrain and dark ancient terrain, moderate cratering", 40_000, 0.5, 900, 400_000, 700, 30_000,
                DIRTY_ICE, DARK_ICE, CLEAN_ICE, CLEAN_ICE, new Region("Galileo Regio", 35, -145, 30, 0, false, DARK_ICE)));
        add(prof("callisto", "saturated with craters, dark icy regolith, Valhalla multi-ring basin", 60_000, 1.0, 1200, 400_000, 0, 1,
                DARK_ICE, DARK_ICE, DIRTY_ICE, CLEAN_ICE, new Region("Valhalla", 18, -57, 20, -500, true, DIRTY_ICE)));
        add(prof("mimas", "Herschel crater (139 km) covers a third of its diameter", 30_000, 1.0, 1500, 60_000, 0, 1,
                DIRTY_ICE, DIRTY_ICE, CLEAN_ICE, CLEAN_ICE, new Region("Herschel", 1.4, -111.8, 21, -8000, false, DIRTY_ICE)));
        add(new Profile("enceladus", "brightest surface in the Solar System, south-polar 'tiger stripe' fissures venting water", 15_000, 0.35, 600, 60_000, 400, 15_000,
                CLEAN_ICE, CLEAN_ICE, CLEAN_ICE, CLEAN_ICE, Liquid.NONE, 0, 0, false, true, false, false, List.of()));
        add(prof("tethys", "cratered ice, Odysseus crater and Ithaca Chasma", 40_000, 0.9, 1500, 150_000, 0, 1, CLEAN_ICE, DIRTY_ICE, CLEAN_ICE, CLEAN_ICE,
                new Region("Odysseus", 32, -128, 18, -4000, false, DIRTY_ICE)));
        add(prof("dione", "cratered ice with bright wispy fractures", 40_000, 0.8, 1300, 150_000, 500, 40_000, CLEAN_ICE, DIRTY_ICE, CLEAN_ICE, CLEAN_ICE));
        add(prof("rhea", "heavily cratered water ice", 50_000, 1.0, 1500, 200_000, 0, 1, CLEAN_ICE, DIRTY_ICE, CLEAN_ICE, CLEAN_ICE));
        add(new Profile("titan", "thick nitrogen atmosphere, equatorial organic dunes, methane/ethane lakes near the poles (shown as dark water), few craters",
                30_000, 0.05, 500, 400_000, 600, 80_000, DIRTY_ICE, ORGANIC_SHORE, DIRTY_ICE, null, Liquid.METHANE, -250, 55,
                false, false, false, true, List.of(new Region("Kraken Mare", 68, -50, 9, -600, true, ORGANIC_SHORE))));
        add(prof("iapetus", "two-tone: dark leading hemisphere (Cassini Regio), bright trailing", 60_000, 1.0, 2500, 200_000, 0, 1,
                CLEAN_ICE, CLEAN_ICE, CLEAN_ICE, CLEAN_ICE, new Region("Cassini Regio", 0, -90, 62, 0, false, DARK_REGOLITH)));
        add(prof("miranda", "patchwork coronae and Verona Rupes, the tallest known cliff", 20_000, 0.6, 1800, 60_000, 2500, 20_000, DIRTY_ICE, DIRTY_ICE, CLEAN_ICE, CLEAN_ICE));
        add(prof("ariel", "youngest Uranian surface, fault valleys", 30_000, 0.4, 900, 100_000, 900, 25_000, CLEAN_ICE, DIRTY_ICE, CLEAN_ICE, CLEAN_ICE));
        add(prof("umbriel", "dark, old, heavily cratered", 40_000, 1.0, 1000, 150_000, 0, 1, DARK_ICE, DARK_ICE, DARK_ICE, DIRTY_ICE));
        add(prof("titania", "cratered with large fault canyons", 40_000, 0.7, 1300, 150_000, 1200, 40_000, DIRTY_ICE, DARK_ICE, CLEAN_ICE, CLEAN_ICE));
        add(prof("oberon", "old, cratered, dark crater floors", 50_000, 1.0, 1400, 150_000, 0, 1, DIRTY_ICE, DARK_ICE, DIRTY_ICE, CLEAN_ICE));
        add(prof("triton", "young nitrogen-ice 'cantaloupe' terrain, pinkish south polar cap, cryovolcanic plumes", 15_000, 0.05, 500, 150_000, 500, 12_000,
                NITROGEN_ICE, DIRTY_ICE, NITROGEN_ICE, null, new Region("South polar cap", -60, 0, 40, 0, false, THOLIN)));
        add(prof("pluto", "Sputnik Planitia nitrogen-ice basin (~1000 km), dark tholin Cthulhu Macula, water-ice mountains", 40_000, 0.5, 1800, 300_000, 2500, 50_000,
                DIRTY_ICE, NITROGEN_ICE, CLEAN_ICE, null,
                new Region("Sputnik Planitia", 25, 175, 17, -2500, true, NITROGEN_ICE), new Region("Cthulhu Macula", -10, 100, 30, 0, false, THOLIN)));
        add(prof("charon", "grey water ice, reddish Mordor Macula at the north pole, large canyons", 40_000, 0.6, 2000, 200_000, 1500, 40_000,
                DIRTY_ICE, DIRTY_ICE, CLEAN_ICE, CLEAN_ICE, new Region("Mordor Macula", 90, 0, 12, 0, false, THOLIN)));
    }

    /** Ids of every body you can land on and walk around. */
    public static List<String> walkable() {
        java.util.ArrayList<String> l = new java.util.ArrayList<>(List.of("earth", "moon", "mars"));
        l.addAll(PROFILES.keySet());
        return l;
    }

    public static boolean isWalkable(String id) { return id.equals("earth") || id.equals("moon") || id.equals("mars") || PROFILES.containsKey(id); }

    private static final Map<String, PlanetTerrain> CACHE = new ConcurrentHashMap<>();

    /** Terrain for a walkable body (cached per body and seed), or null if it has no solid surface. */
    public static PlanetTerrain forBody(Body body, long seed) {
        if (body == null || !isWalkable(body.id)) return null;
        return CACHE.computeIfAbsent(body.id + "@" + seed, k -> create(body, seed));
    }

    private static PlanetTerrain create(Body body, long seed) {
        double r = body.meanRadius();
        long s = seed ^ (body.id.hashCode() * 0x632BE59BD9B4E019L);
        switch (body.id) {
            case "earth": { ElevationGrid g = load("earth"); if (g != null) return new EarthTerrain(g, r, s); break; }
            case "moon": { ElevationGrid g = load("moon"); if (g != null) return new MoonTerrain(g, r, s); break; }
            case "mars": { ElevationGrid g = load("mars"); if (g != null) return new MarsTerrain(g, r, s); break; }
            default: break;
        }
        Profile p = PROFILES.get(body.id);
        if (p == null) p = prof(body.id, "generic rocky body", 30_000, 0.7, 1000, 200_000, 0, 1, GREY_REGOLITH, DARK_REGOLITH, GREY_REGOLITH, LUNAR_EJECTA);
        return new ProceduralTerrain(p, r, s);
    }

    private static ElevationGrid load(String name) { return ElevationGrid.bundled(name); }
}
