package dev.saurus.cosmos.core.terrain;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;

/**
 * A real global elevation model (equirectangular, lines from 90N southward, samples from 0E eastward) produced by
 * tools/fetch_dem.py from NASA/NOAA data. Sampled with Catmull-Rom bicubic interpolation so slopes are smooth between
 * grid posts.
 */
public final class ElevationGrid {
    public final int w, h;
    public final float metresPerUnit;
    public final String source;
    private final short[] data;

    private ElevationGrid(int w, int h, float mpu, String source, short[] data) {
        this.w = w; this.h = h; this.metresPerUnit = mpu; this.source = source; this.data = data;
    }

    public static ElevationGrid load(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16))) {
            byte[] hdr = new byte[32];
            in.readFully(hdr);
            ByteBuffer hb = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);
            boolean v1 = hb.get(0) == 'C' && hb.get(1) == 'D' && hb.get(2) == 'E' && hb.get(3) == 'M';
            boolean v2 = hb.get(0) == 'C' && hb.get(1) == 'D' && hb.get(2) == 'E' && hb.get(3) == '2';
            if (!v1 && !v2) throw new IOException("not a Cosmos elevation grid");
            int w = hb.getInt(4), h = hb.getInt(8);
            float mpu = hb.getFloat(12);
            String src = new String(hdr, 16, 16, java.nio.charset.StandardCharsets.US_ASCII).replace("\0", "").trim();
            byte[] body = in.readAllBytes();
            if (body.length < (long) w * h * 2) throw new IOException("truncated grid");
            short[] d = new short[w * h];
            ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(d);
            if (v2) for (int j = 0; j < h; j++) { int acc = 0; for (int i = 0; i < w; i++) { acc += d[j * w + i]; d[j * w + i] = (short) acc; } }
            return new ElevationGrid(w, h, mpu, src, d);
        }
    }

    /** Loads a bundled grid from the classpath (cosmos/terrain/NAME.dem.gz), or null if it isn't bundled. */
    public static ElevationGrid bundled(String name) {
        try (InputStream in = ElevationGrid.class.getResourceAsStream("/cosmos/terrain/" + name + ".dem.gz")) {
            return in == null ? null : load(in);
        } catch (IOException e) {
            throw new IllegalStateException("corrupt bundled elevation grid " + name, e);
        }
    }

    private double at(int i, int j) {
        i = Math.floorMod(i, w);
        if (j < 0) { j = -1 - j; i = Math.floorMod(i + w / 2, w); }        // across the north pole
        else if (j >= h) { j = 2 * h - 1 - j; i = Math.floorMod(i + w / 2, w); }
        return data[j * w + i] * (double) metresPerUnit;
    }

    private static double cr(double p0, double p1, double p2, double p3, double t) {
        return p1 + 0.5 * t * (p2 - p0 + t * (2 * p0 - 5 * p1 + 4 * p2 - p3 + t * (3 * (p1 - p2) + p3 - p0)));
    }

    /** Elevation in metres at latitude/longitude (radians). */
    public double sample(double lat, double lon) {
        double u = Math.IEEEremainder(lon, 2 * Math.PI); if (u < 0) u += 2 * Math.PI;
        double fx = u / (2 * Math.PI) * w - 0.5, fy = (Math.PI / 2 - lat) / Math.PI * h - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fy);
        double tx = fx - i, ty = fy - j;
        double c0 = cr(at(i - 1, j - 1), at(i, j - 1), at(i + 1, j - 1), at(i + 2, j - 1), tx);
        double c1 = cr(at(i - 1, j), at(i, j), at(i + 1, j), at(i + 2, j), tx);
        double c2 = cr(at(i - 1, j + 1), at(i, j + 1), at(i + 1, j + 1), at(i + 2, j + 1), tx);
        double c3 = cr(at(i - 1, j + 2), at(i, j + 2), at(i + 1, j + 2), at(i + 2, j + 2), tx);
        return cr(c0, c1, c2, c3, ty);
    }

    /** Grid spacing in metres along a meridian on a body of the given radius. */
    public double spacingM(double radiusM) { return Math.PI * radiusM / h; }
}
