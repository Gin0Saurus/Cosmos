package dev.saurus.cosmos.world;

import dev.saurus.cosmos.core.terrain.Landmarks.Kind;
import dev.saurus.cosmos.core.terrain.Landmarks.Landmark;
import net.minecraft.world.level.block.state.BlockState;

import static dev.saurus.cosmos.world.SurfacePalette.b;

/**
 * Life-size block models of real landmarks (1 block = 1 m). x = east, z = south, y = metres above the ground pad.
 * Shapes follow published dimensions; detail is simplified to Minecraft blocks.
 */
public final class LandmarkPainter {
    private LandmarkPainter() {}

    private static final BlockState AIR = null;

    public static BlockState at(Landmark l, int x, int y, int z) {
        return switch (l.kind()) {
            case PYRAMID -> pyramid(x, y, z);
            case STONEHENGE -> stonehenge(x, y, z);
            case EIFFEL -> eiffel(x, y, z);
            case ELIZABETH_TOWER -> elizabeth(x, y, z);
            case PISA -> pisa(x, y, z);
            case COLOSSEUM -> colosseum(x, y, z);
            case APOLLO_LM -> apollo(x, y, z, false);
            case APOLLO_LM_ROVER -> apollo(x, y, z, true);
            case LANDER -> lander(x, y, z);
            case ROVER -> rover(x, y, z);
            case PROBE -> probe(x, y, z);
        };
    }

    /** Ground pad material under and around a landmark. */
    public static BlockState foundation(Landmark l) {
        return switch (l.body()) {
            case "earth" -> l.kind() == Kind.PYRAMID ? b("sandstone") : b("stone");
            case "moon" -> b("light_gray_concrete_powder");
            case "mars" -> b("red_sandstone");
            default -> b("stone");
        };
    }

    // ---- Great Pyramid: 230.3 m square base, original apex 146.6 m, truncated today at 138.5 m
    private static BlockState pyramid(int x, int y, int z) {
        if (y >= 139) return AIR;
        double half = 115.15 * (1 - y / 146.6);
        int m = Math.max(Math.abs(x), Math.abs(z));
        if (m > half) return AIR;
        if (x == 0 && z > 0 && z < half && y >= 17 && y <= 18) return AIR;    // entrance passage, north face is z<0; keep simple
        return m > half - 1.5 ? b("smooth_sandstone") : b("sandstone");
    }

    // ---- Stonehenge: sarsen circle d = 33 m (30 uprights, ~17 standing), lintels at 4 m, trilithon horseshoe, bluestones, Heel Stone
    private static final int[] FALLEN = { 3, 7, 8, 12, 13, 14, 17, 18, 19, 21, 22, 25, 26 };
    private static boolean fallen(int i) { for (int f : FALLEN) if (f == i) return true; return false; }
    private static BlockState stonehenge(int x, int y, int z) {
        BlockState sarsen = b("stone"), blue = b("andesite");
        double az = Math.toRadians(49.95);                         // axis: midsummer sunrise
        double ax = Math.sin(az), azz = -Math.cos(az);
        double r = Math.hypot(x + 0.5, z + 0.5), th = Math.atan2(z + 0.5, x + 0.5);
        // outer sarsen circle
        if (Math.abs(r - 16.5) < 0.75 && y <= 4) {
            double step = 2 * Math.PI / 30;
            int i = (int) Math.floor(Math.floorMod((long) Math.floor((th + Math.PI) / step * 1000), 30_000) / 1000.0);
            double centre = -Math.PI + (i + 0.5) * step;
            double arc = Math.abs(Math.IEEEremainder(th - centre, 2 * Math.PI)) * 16.5;
            if (y < 4 && arc < 1.05 && !fallen(i)) return sarsen;
            if (y == 4 && !fallen(i) && !fallen((i + 1) % 30)) return sarsen;          // lintel ring
            return AIR;
        }
        // trilithon horseshoe (5 trilithons, open toward the axis), heights 6, 6.5, 7.3 m
        double along = (x + 0.5) * ax + (z + 0.5) * azz, across = (x + 0.5) * azz * -1 + (z + 0.5) * ax;
        double[][] tri = { { -8.5, 0, 7 }, { -5.5, 6.0, 6 }, { -5.5, -6.0, 6 }, { -1.0, 8.0, 5 }, { -1.0, -8.0, 5 } };
        for (int k = 0; k < tri.length; k++) {
            if (k == 2) continue;                                       // one trilithon has fallen
            double da = along - tri[k][0], dc = across - tri[k][1];
            if (Math.abs(da) < 0.8 && Math.abs(dc) < 2.6) {
                int h = (int) tri[k][2];
                if (y < h && Math.abs(dc) > 0.6) return sarsen;
                if (y == h) return sarsen;
                return AIR;
            }
        }
        // bluestone circle (r ~ 11.5 m), low stones, many missing
        if (Math.abs(r - 11.5) < 0.5 && y < 2 && Math.floorMod((int) Math.floor((th + Math.PI) * 40), 3) == 0) return blue;
        // Heel Stone, 77 m out on the axis, 4.7 m tall leaning
        if (Math.hypot(x + 0.5 - ax * 77, z + 0.5 - azz * 77) < 1.3 && y < 5) return sarsen;
        return AIR;
    }

    // ---- Eiffel Tower: 125 m base, platforms at 57.6, 115.7, 276.1 m; lattice to 300 m, antenna to 330 m
    private static double eHalf(double y) {
        if (y < 57.6) return 62.5 - (62.5 - 23) * Math.pow(y / 57.6, 0.8);
        if (y < 115.7) return 23 - 13 * (y - 57.6) / 58.1;
        if (y < 276) return 10 - 7 * Math.pow((y - 115.7) / 160.3, 0.7);
        return Math.max(0.6, 3 - 2.4 * (y - 276) / 24);
    }
    private static BlockState eiffel(int x, int y, int z) {
        BlockState iron = b("brown_terracotta"), bars = b("iron_bars"), floor = b("dark_oak_planks");
        double ax = Math.abs(x + 0.5), az = Math.abs(z + 0.5);
        if (y > 300) return (ax < 1 && az < 1 && y <= 330) ? bars : AIR;
        double h = eHalf(y);
        if (ax > h + 0.5 || az > h + 0.5) return AIR;
        boolean edgeX = ax > h - 1.5, edgeZ = az > h - 1.5;
        // platforms
        if ((y == 57 || y == 58) && Math.max(ax, az) < h + 0.5) return y == 58 ? floor : iron;
        if ((y == 115 || y == 116) && Math.max(ax, az) < h + 0.5) return y == 116 ? floor : iron;
        if (y == 276 && Math.max(ax, az) < h + 0.5) return floor;
        if (y < 116) {
            // four legs: lattice boxes at the corners, leg width shrinking with height
            double leg = y < 57.6 ? 15 - 5 * y / 57.6 : 10 - 4 * (y - 57.6) / 58.1;
            boolean inLeg = ax > h - leg && az > h - leg;
            if (!inLeg) {
                // arches between the legs on each face below the first platform
                if (y < 57.6 && (edgeX || edgeZ)) {
                    double span = h - leg, along = edgeX ? az : ax;
                    double archTop = 39 * Math.sqrt(Math.max(0, 1 - (along / Math.max(span, 1)) * (along / Math.max(span, 1))));
                    if (Math.abs(y - archTop) < 1.2 || y > 50) return iron;
                }
                return AIR;
            }
            boolean legEdge = ax > h - 1.5 || az > h - 1.5 || ax < h - leg + 1.5 || az < h - leg + 1.5;
            if (!legEdge) return AIR;
            boolean corner = (ax > h - 1.5 || ax < h - leg + 1.5) && (az > h - 1.5 || az < h - leg + 1.5);
            if (corner || Math.floorMod(y, 6) == 0) return iron;
            return Math.floorMod(y + (int) (ax + az), 3) == 0 ? bars : AIR;   // diagonal bracing
        }
        // single tapering shaft: hollow square lattice
        if (!(edgeX || edgeZ)) return AIR;
        if (edgeX && edgeZ) return iron;                                   // corner members
        if (Math.floorMod(y, 8) == 0) return iron;                          // horizontal rings
        return Math.floorMod(y + (int) (ax + az), 4) == 0 ? bars : AIR;
    }

    // ---- Elizabeth Tower: 12 m square, clock faces at ~55-62 m, belfry, cast-iron spire to 96 m
    private static BlockState elizabeth(int x, int y, int z) {
        BlockState stone = b("smooth_sandstone"), trim = b("cut_sandstone");
        int ax = Math.abs(x), az = Math.abs(z);   // footprint -6..6 (13 blocks)
        if (y < 72) {
            if (ax > 6 || az > 6) return AIR;
            boolean wall = ax == 6 || az == 6;
            if (!wall) return y % 12 == 0 ? b("spruce_planks") : AIR;
            if (y >= 54 && y <= 62) {   // clock faces, centred at 58 m, radius 3.5
                int u = ax == 6 ? z : x, v = y - 58;
                double rr = Math.hypot(u, v);
                if (rr < 3.6) {
                    if (rr < 0.6) return b("gold_block");
                    if ((u == 0 && v > 0 && v < 3) || (v == 0 && u > 0 && u < 2)) return b("black_concrete");   // hands at 3 o'clock-ish
                    if (rr > 2.9) return b("gold_block");
                    return b("white_concrete");
                }
            }
            if (y >= 63 && y <= 70 && Math.abs(ax == 6 ? z : x) <= 3 && Math.floorMod(ax == 6 ? z : x, 2) == 0) return AIR;  // belfry openings
            boolean window = y > 6 && y < 52 && Math.floorMod(y, 6) >= 2 && Math.abs(ax == 6 ? z : x) <= 2;
            if (window) return b("glass_pane");
            return (ax == 6 && az == 6) || y % 6 == 0 ? trim : stone;
        }
        if (y > 96) return AIR;
        double half = 6 * (1 - (y - 72) / 25.0);
        if (y >= 94) return (ax == 0 && az == 0) ? b("gold_block") : AIR;
        if (ax <= half && az <= half) return (ax >= half - 1 || az >= half - 1) ? b("black_concrete") : AIR;
        return AIR;
    }

    // ---- Leaning Tower of Pisa: 15.5 m diameter, 56 m, tilted ~3.97 deg to the south
    private static BlockState pisa(int x, int y, int z) {
        if (y > 57) return AIR;
        double shift = y * Math.tan(Math.toRadians(3.97));
        double r = Math.hypot(x + 0.5, z + 0.5 - shift);
        double outer = y >= 51 ? 4.8 : 7.75;
        if (r > outer) return AIR;
        if (r < outer - 1.6) return Math.floorMod(y, 7) == 0 ? b("smooth_quartz") : AIR;
        int storey = y / 7;
        boolean gallery = storey >= 1 && storey <= 6 && Math.floorMod(y, 7) >= 2 && Math.floorMod(y, 7) <= 5;
        if (gallery && r > outer - 0.8) {
            double th = Math.atan2(z + 0.5 - shift, x + 0.5);
            if (Math.floorMod((int) Math.floor((th + Math.PI) / (2 * Math.PI) * 30), 2) == 0) return b("quartz_pillar");
            return AIR;
        }
        return b("smooth_quartz");
    }

    // ---- Colosseum: outer ellipse 189 x 156 m, arena 87 x 55 m, 48 m tall; outer ring lost on the south side
    private static BlockState colosseum(int x, int y, int z) {
        BlockState trav = b("smooth_sandstone"), brick = b("terracotta");
        double ex = (x + 0.5) / 94.5, ez = (z + 0.5) / 78.0;
        double ro = Math.hypot(ex, ez);                         // 1 at the outer wall
        if (ro > 1.0) return AIR;
        double ax2 = (x + 0.5) / 43.5, az2 = (z + 0.5) / 27.5;
        double ri = Math.hypot(ax2, az2);
        if (ri < 1.0) return y == 0 ? b("light_gray_concrete_powder") : AIR;   // arena floor (sand over hypogeum)
        boolean south = z > 10;
        double wallTop = south ? 31 : 48;                       // the outer ring survives only on the north side
        if (ro > 0.97) {                                       // outer facade with four storeys of arches
            if (y > wallTop) return AIR;
            double th = Math.atan2(ez, ex);
            int bay = (int) Math.floor((th + Math.PI) / (2 * Math.PI) * 80);
            int storeyY = Math.floorMod(y, 12);
            boolean arch = y < 36 && storeyY >= 2 && storeyY <= 9 && Math.floorMod(bay, 1) == 0
                    && Math.abs(((th + Math.PI) / (2 * Math.PI) * 80) - bay - 0.5) < 0.32;
            if (south && ro > 0.985) return AIR;
            return arch ? AIR : trav;
        }
        // seating (cavea): rises from 4 m at the podium to ~40 m near the outer wall, in tiers
        double f = (ro - 0.62) / 0.35;
        double seatTop = 4 + 36 * Math.max(0, Math.min(1, f));
        if (south) seatTop = Math.min(seatTop, 28);
        if (y > seatTop) return AIR;
        if (y > seatTop - 1.5) return Math.floorMod((int) (ro * 140), 3) == 0 ? AIR : trav;
        return Math.floorMod((int) Math.floor(Math.atan2(ez, ex) * 40), 4) == 0 && y < seatTop - 4 ? AIR : brick;  // vaulted corridors
    }

    // ---- Apollo Lunar Module descent stage (4.2 m across, gold foil), flag, and on 15-17 the Lunar Roving Vehicle
    private static BlockState apollo(int x, int y, int z, boolean rover) {
        BlockState gold = b("gold_block"), leg = b("iron_bars"), pad = b("light_gray_concrete");
        int ax = Math.abs(x), az = Math.abs(z);
        if (y == 0 && ((ax == 3 && z == 0) || (az == 3 && x == 0))) return pad;                     // footpads
        if (y >= 1 && y <= 2 && ((ax == 3 && z == 0) || (az == 3 && x == 0))) return leg;           // legs
        if (y >= 2 && y <= 3 && ax <= 2 && az <= 2 && !(ax == 2 && az == 2)) return gold;            // octagonal descent stage
        if (y == 1 && x == 0 && z == 2) return b("iron_trapdoor");          // ladder porch side
        // flag 5 m in front (south) of the lander
        if (x == 2 && z == 6 && y <= 3) return b("iron_bars");
        if (z == 6 && y >= 2 && y <= 3 && x >= 3 && x <= 5) {
            if (x == 3 && y == 3) return b("blue_wool");
            return (y == 3) ? b("white_wool") : b("red_wool");
        }
        if (rover && y == 0 && z >= -7 && z <= -5 && (x == 5 || x == 7)) return b("black_concrete");   // LRV wheels
        if (rover && y == 1 && z >= -7 && z <= -5 && x >= 5 && x <= 7) return b("iron_trapdoor");
        if (rover && y == 2 && z == -5 && x == 6) return b("daylight_detector");   // high-gain antenna
        return AIR;
    }

    private static BlockState lander(int x, int y, int z) {
        int ax = Math.abs(x), az = Math.abs(z);
        if (y == 0 && ax == 2 && az == 2) return b("light_gray_concrete");
        if (y == 1 && ax <= 1 && az <= 1) return b("iron_block");
        if (y == 2 && ax <= 1 && az <= 1) return b("gold_block");
        if (y == 2 && ((ax == 3 && az <= 1) || (az == 3 && ax <= 1))) return b("blue_stained_glass");   // solar panels
        if (y == 3 && x == 0 && z == 0) return b("lightning_rod");
        return AIR;
    }

    private static BlockState rover(int x, int y, int z) {
        int ax = Math.abs(x);
        if (y == 0 && ax == 1 && (z == -1 || z == 0 || z == 1)) return b("black_concrete");          // six wheels
        if (y == 1 && ax <= 1 && z >= -1 && z <= 1) return b("iron_block");
        if (y == 2 && x == 0 && z == 1) return b("iron_bars");                 // mast
        if (y == 3 && x == 0 && z == 1) return b("observer");                  // camera head
        if (y == 2 && x == 0 && z == -1) return b("daylight_detector");        // RTG / antenna
        return AIR;
    }

    private static BlockState probe(int x, int y, int z) {
        double r = Math.hypot(x, z);
        if (y == 0 && r <= 1.5) return b("iron_block");
        if (y == 1 && r <= 0.5) return b("gold_block");
        return AIR;
    }
}
