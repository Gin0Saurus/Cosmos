package dev.saurus.cosmos.core.terrain;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Sharper measured elevation around landmarks (Grand Canyon, Everest, Fuji, Apollo sites, Tycho, Olympus Mons, Gale,
 * Valles Marineris...), cut by tools/fetch_patches.py from the full-resolution datasets: ETOPO 2022 60" for Earth,
 * LOLA LDEM_16 for the Moon, MOLA MEGDR 16 ppd for Mars. Blended into the global grid with feathered edges.
 */
public final class ElevationPatches {
    public record Patch(String name, double latN, double latS, double lonW, double lonE, int w, int h, float q, short[] d) {
        double at(int i, int j) { i = Math.max(0, Math.min(w - 1, i)); j = Math.max(0, Math.min(h - 1, j)); return d[j * w + i] * (double) q; }
        double widthDeg() { return lonE - lonW; }
    }
    private final List<Patch> patches;
    private ElevationPatches(List<Patch> p) { patches = p; }
    public List<Patch> list() { return patches; }

    public static ElevationPatches bundled(String body) {
        try (InputStream raw = ElevationPatches.class.getResourceAsStream("/cosmos/terrain/" + body + "_patches.bin.gz")) {
            if (raw == null) return new ElevationPatches(List.of());
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16))) {
                byte[] all = in.readAllBytes();
                ByteBuffer b = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
                int n = b.getInt();
                List<Patch> out = new ArrayList<>();
                for (int k = 0; k < n; k++) {
                    byte[] nm = new byte[32]; b.get(nm);
                    String name = new String(nm, java.nio.charset.StandardCharsets.UTF_8).replace("\0", "").trim();
                    float la0 = b.getFloat(), la1 = b.getFloat(), lo0 = b.getFloat(), lo1 = b.getFloat();
                    int w = b.getInt(), h = b.getInt(); float q = b.getFloat();
                    short[] d = new short[w * h]; b.asShortBuffer().get(d); b.position(b.position() + w * h * 2);
                    out.add(new Patch(name, la0, la1, lo0, lo1, w, h, q, d));
                }
                return new ElevationPatches(out);
            }
        } catch (IOException e) { throw new IllegalStateException("corrupt elevation patches for " + body, e); }
    }

    private static double cr(double p0, double p1, double p2, double p3, double t) {
        return p1 + 0.5 * t * (p2 - p0 + t * (2 * p0 - 5 * p1 + 4 * p2 - p3 + t * (3 * (p1 - p2) + p3 - p0)));
    }

    /**
     * Blends patches into a base elevation. Returns {elevation, weight} where weight (0..1) says how much measured
     * high-resolution data was used (callers reduce procedural detail by it).
     */
    public double[] blend(double latRad, double lonRad, double base) {
        if (patches.isEmpty()) return new double[] { base, 0 };
        double lat = Math.toDegrees(latRad), lon = Math.toDegrees(lonRad);
        double e = base, wsum = 0;
        for (Patch p : patches) {
            if (lat > p.latN() || lat < p.latS()) continue;
            double dl = lon - p.lonW(); dl -= 360 * Math.floor(dl / 360);
            if (dl > p.widthDeg()) continue;
            double fy = (p.latN() - lat) / (p.latN() - p.latS()), fx = dl / p.widthDeg();
            double edge = Math.min(Math.min(fx, 1 - fx) * (p.widthDeg() >= 359 ? 100 : 1), Math.min(fy, 1 - fy));
            double w = EarthTerrain.smooth(edge / 0.18);
            if (w <= 0) continue;
            double px = fx * p.w() - 0.5, py = fy * p.h() - 0.5;
            int i = (int) Math.floor(px), j = (int) Math.floor(py);
            double tx = px - i, ty = py - j;
            double c0 = cr(p.at(i - 1, j - 1), p.at(i, j - 1), p.at(i + 1, j - 1), p.at(i + 2, j - 1), tx);
            double c1 = cr(p.at(i - 1, j), p.at(i, j), p.at(i + 1, j), p.at(i + 2, j), tx);
            double c2 = cr(p.at(i - 1, j + 1), p.at(i, j + 1), p.at(i + 1, j + 1), p.at(i + 2, j + 1), tx);
            double c3 = cr(p.at(i - 1, j + 2), p.at(i, j + 2), p.at(i + 1, j + 2), p.at(i + 2, j + 2), tx);
            double v = cr(c0, c1, c2, c3, ty);
            e = e * (1 - w) + v * w;
            wsum = Math.max(wsum, w);
        }
        return new double[] { e, wsum };
    }
}
