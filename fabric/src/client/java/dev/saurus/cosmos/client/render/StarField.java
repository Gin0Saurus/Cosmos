package dev.saurus.cosmos.client.render;

import dev.saurus.cosmos.core.Universe;
import dev.saurus.cosmos.core.catalog.DeepSkyCatalog;
import dev.saurus.cosmos.core.catalog.StarCatalog;
import dev.saurus.cosmos.core.catalog.StarColor;
import dev.saurus.cosmos.core.galaxy.MilkyWayModel;
import dev.saurus.cosmos.core.galaxy.ProceduralStars;
import dev.saurus.cosmos.core.math.Vec3d;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * CPU-side builder of star sprite geometry as seen from an observer anywhere in the universe. Each star = 4 vertices
 * (POSITION_TEX_COLOR: 3 floats direction, 2 floats corner, RGBA8 colour) + 6 indices. Real catalogue stars always;
 * deterministic procedural stars added when the observer is far from the Sun; bright galaxies as dim fuzzy points.
 */
public final class StarField {
    public static final int VERTEX_BYTES = 24;

    public record Geometry(ByteBuffer vertices, ByteBuffer indices, int starCount) {
        public void free() { MemoryUtil.memFree(vertices); MemoryUtil.memFree(indices); }
    }

    /** Observer in heliocentric ICRS parsecs; transform rotates ICRS into the output frame (identity for space). */
    public static Geometry build(Universe u, Vec3d observerPc, double timeS, double magLimit, double[] rot3x3) {
        StarCatalog c = u.stars;
        int cap = c.size() + 64_000;
        ByteBuffer v = MemoryUtil.memAlloc(cap * 4 * VERTEX_BYTES);
        int n = 0;
        for (int i = 0; i < c.size(); i++) {
            Vec3d d = c.positionPc(i, timeS).sub(observerPc);
            double dist = d.length();
            if (dist < 1e-4) continue; // we are at (or inside) this star: it is drawn as a sphere instead
            double m = c.absMag(i) + 5 * (Math.log10(dist) - 1);
            if (m > magLimit) continue;
            n += emit(v, d.mul(1 / dist), m, c.colour(i), rot3x3) ? 1 : 0;
            if (n >= cap) break;
        }
        // procedural stars around observers far from the catalogued neighbourhood
        if (observerPc.length() > 60 && n < cap) {
            ProceduralStars ps = u.procedural;
            Vec3d g = MilkyWayModel.toGalactocentric(observerPc);
            long cx = ProceduralStars.cellIndex(g.x()), cy = ProceduralStars.cellIndex(g.y()), cz = ProceduralStars.cellIndex(g.z());
            outer:
            for (long a = cx - 2; a <= cx + 2; a++) for (long b = cy - 2; b <= cy + 2; b++) for (long z = cz - 1; z <= cz + 1; z++) {
                for (ProceduralStars.PStar s : ps.cell(a, b, z)) {
                    if (s.type() == ProceduralStars.Type.BLACK_HOLE || s.lumSun() <= 0) continue;
                    Vec3d d = s.icrsPc().sub(observerPc);
                    double dist = d.length();
                    if (dist < 1e-4) continue;
                    double m = s.absMag() + 5 * (Math.log10(dist) - 1);
                    if (m > magLimit) continue;
                    if (emit(v, d.mul(1 / dist), m, s.colour(), rot3x3)) n++;
                    if (n >= cap) break outer;
                }
            }
        }
        // named galaxies with distances, magnitude adjusted to the observer, as faint fuzzy points
        for (DeepSkyCatalog.Dso dso : u.deepSky.all()) {
            if (n >= cap) break;
            if (dso.kind() != DeepSkyCatalog.Kind.GALAXY || !dso.hasDistance() || Double.isNaN(dso.mag()) || dso.mag() > 11) continue;
            Vec3d pos = dso.direction().mul(dso.distKpc() * 1000);
            Vec3d d = pos.sub(observerPc);
            double dist = d.length();
            double m = dso.mag() + 5 * Math.log10(dist / (dso.distKpc() * 1000));
            if (m > magLimit) continue;
            if (emit(v, d.mul(1 / dist), m + 0.5, new float[]{0.95f, 0.9f, 0.85f}, rot3x3)) n++;
        }
        v.flip();
        ByteBuffer idx = MemoryUtil.memAlloc(Math.max(n, 1) * 6 * 4);
        for (int s = 0; s < n; s++) {
            int b = s * 4;
            idx.putInt(b).putInt(b + 1).putInt(b + 2).putInt(b + 2).putInt(b + 3).putInt(b);
        }
        idx.flip();
        return new Geometry(v, idx, n);
    }

    /** Photometric sprite model: flux relative to m=6.5; faint stars dim 1-px points, bright stars grow and saturate. */
    private static boolean emit(ByteBuffer v, Vec3d dir, double mag, float[] col, double[] r) {
        double flux = Math.pow(10, -0.4 * (mag - 6.5));
        double radius = Math.max(0.75, Math.min(7.5, 0.9 * Math.pow(flux, 0.22)));
        double intensity = Math.min(1.0, flux / (radius * radius) * 1.6);
        if (intensity < 0.006) return false;
        double x = dir.x(), y = dir.y(), z = dir.z();
        float dx = (float) (r[0] * x + r[1] * y + r[2] * z), dy = (float) (r[3] * x + r[4] * y + r[5] * z), dz = (float) (r[6] * x + r[7] * y + r[8] * z);
        int cr = (int) Math.min(255, col[0] * intensity * 255), cg = (int) Math.min(255, col[1] * intensity * 255), cb = (int) Math.min(255, col[2] * intensity * 255);
        int ca = (int) Math.min(255, radius / 8.0 * 255);
        float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (float[] k : corners) {
            v.putFloat(dx).putFloat(dy).putFloat(dz).putFloat(k[0]).putFloat(k[1]);
            v.put((byte) cr).put((byte) cg).put((byte) cb).put((byte) ca);
        }
        return true;
    }

    public static float[] colourOf(double teff) { return StarColor.rgbFromTemperature(teff); }

    public static final double[] IDENTITY = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    @SuppressWarnings("unused") private static final List<Object> KEEP = List.of();
}
