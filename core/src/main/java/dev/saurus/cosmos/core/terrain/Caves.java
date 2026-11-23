package dev.saurus.cosmos.core.terrain;

/**
 * Underground voids, at the local 1 block = 1 m scale. Sampled at the true 3D point inside the planet
 * (unit direction x (planet radius in blocks + y)), so tunnels continue seamlessly across the date line.
 * <ul>
 *   <li>EARTH - karst-style winding tunnels and caverns, lava pools in the deepest crust (vanilla-like, procedural).</li>
 *   <li>LAVA_TUBES - the Moon and Mars: long, sinuous, horizontal tubes 6-14 blocks wide just under volcanic plains,
 *       with occasional skylights where the roof is thin. Real: lunar pits (Marius Hills, Mare Tranquillitatis) and
 *       Martian pit chains on Tharsis are collapsed tube roofs.</li>
 *   <li>ICE - big caverns in icy crusts (procedural).</li>
 *   <li>SPARSE - occasional voids in other rocky bodies.</li>
 * </ul>
 * {@link #carve} returns a field that is negative inside a void; it is smooth so the generator can interpolate it.
 */
public final class Caves {
    public enum Style { EARTH, LAVA_TUBES, ICE, SPARSE, NONE }

    public final Style style;
    private final Noise a, b, c, d;
    private final double rb;

    public Caves(String body, long seed, int circumferenceBlocks) {
        this.style = styleFor(body);
        this.a = new Noise(seed ^ 0xCA7E1L); this.b = new Noise(seed ^ 0xCA7E2L); this.c = new Noise(seed ^ 0xCA7E3L); this.d = new Noise(seed ^ 0xCA7E4L);
        this.rb = circumferenceBlocks / (2 * Math.PI);
    }

    public static Style styleFor(String body) {
        return switch (body) {
            case "earth" -> Style.EARTH;
            case "moon", "mars", "mercury", "io" -> Style.LAVA_TUBES;
            case "europa", "ganymede", "callisto", "enceladus", "tethys", "dione", "rhea", "titan", "iapetus", "mimas",
                 "miranda", "ariel", "umbriel", "titania", "oberon", "triton", "pluto", "charon" -> Style.ICE;
            case "phobos", "deimos" -> Style.NONE;
            default -> Style.SPARSE;
        };
    }

    /** Whether tubes may run under this surface (volcanic plains). */
    static boolean tubeTerrain(Surface s) {
        return s == Surface.LUNAR_MARE || s == Surface.MARS_VOLCANIC || s == Surface.MARS_DUST || s == Surface.MARS_DARK_SAND
                || s == Surface.BASALT_PLAINS || s == Surface.SULFUR_PLAINS || s == Surface.DARK_REGOLITH || s == Surface.GREY_REGOLITH;
    }

    /**
     * @param u unit direction of the column (body-fixed)
     * @param y block Y
     * @param depth blocks below the local surface (positive underground)
     * @param s the column's surface type
     */
    public double carve(double[] u, double y, double depth, Surface s) {
        if (style == Style.NONE || depth < 1) return 1;
        double r = rb + y;
        double x = u[0] * r, yy = u[1] * r, z = u[2] * r;
        switch (style) {
            case EARTH: {
                double t1 = a.noise(x / 42, yy / 42, z / 42), t2 = b.noise(x / 42 + 31.7, yy / 42, z / 42 - 9.1);
                double tunnel = Math.max(Math.abs(t1), Math.abs(t2)) - 0.075;                 // spaghetti tunnels
                double cav = 0.52 - c.fbm(x, yy, z, 75, 2, 0.5) - 0.06 * Math.max(0, 20 - depth) / 20;  // caverns, rarer near the top
                double v = Math.min(tunnel * 4, cav * 3);
                if (depth < 6) v += (6 - depth) * 0.08 * (d.noise(x / 60, yy / 60, z / 60) > 0.35 ? 0.2 : 1);  // only some reach daylight
                return v;
            }
            case LAVA_TUBES: {
                double v = 0.44 - c.fbm(x, yy, z, 60, 2, 0.5);                                  // small voids anywhere
                v = v * 3 + Math.max(0, 8 - depth) * 0.12;
                if (tubeTerrain(s)) {
                    // the tube follows a contour of a smooth 2D field over the surface, at a depth that itself meanders
                    double sx = u[0] * rb, sy = u[1] * rb, sz = u[2] * rb;
                    double q = a.noise(sx / 220, sy / 220, sz / 220);
                    double gate = b.noise(sx / 700, sy / 700, sz / 700);
                    if (gate > -0.1) {
                        double width = 0.022 + 0.012 * (d.noise(sx / 90, sy / 90, sz / 90) + 1) / 2;
                        double centre = 13 + 9 * c.noise(sx / 300 + 5, sy / 300, sz / 300);   // 4..22 blocks below the surface
                        double radius = 3.5 + 2.5 * (d.noise(sx / 120 - 3, sy / 120, sz / 120) + 1) / 2;
                        double e = (q / width) * (q / width) + ((depth - centre) / radius) * ((depth - centre) / radius) - 1;
                        v = Math.min(v, e);
                    }
                }
                return v;
            }
            case ICE: {
                double cav = 0.48 - c.fbm(x, yy, z, 55, 3, 0.5);
                double t1 = a.noise(x / 50, yy / 50, z / 50), t2 = b.noise(x / 50, yy / 50 + 7, z / 50);
                double tunnel = Math.max(Math.abs(t1), Math.abs(t2)) - 0.06;
                return Math.min(cav * 3, tunnel * 4) + Math.max(0, 5 - depth) * 0.15;
            }
            case SPARSE: {
                return (0.62 - c.fbm(x, yy, z, 60, 2, 0.5)) * 3 + Math.max(0, 6 - depth) * 0.15;
            }
            default: return 1;
        }
    }
}
