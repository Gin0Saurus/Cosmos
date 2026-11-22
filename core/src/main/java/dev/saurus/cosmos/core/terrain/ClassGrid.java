package dev.saurus.cosmos.core.terrain;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;

/** A global byte-valued class map (e.g. Earth land cover), same layout as {@link ElevationGrid}, nearest-neighbour. */
public final class ClassGrid {
    public final int w, h;
    private final byte[] data;
    private ClassGrid(int w, int h, byte[] d) { this.w = w; this.h = h; this.data = d; }

    public static ClassGrid bundled(String name) {
        try (InputStream raw = ClassGrid.class.getResourceAsStream("/cosmos/terrain/" + name + ".bin.gz")) {
            if (raw == null) return null;
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16))) {
                byte[] hdr = new byte[8]; in.readFully(hdr);
                ByteBuffer hb = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);
                int w = hb.getInt(0), h = hb.getInt(4);
                byte[] d = in.readNBytes(w * h);
                return new ClassGrid(w, h, d);
            }
        } catch (IOException e) { throw new IllegalStateException("corrupt class grid " + name, e); }
    }

    public int sample(double lat, double lon) {
        double u = Math.IEEEremainder(lon, 2 * Math.PI); if (u < 0) u += 2 * Math.PI;
        int i = Math.floorMod((int) Math.floor(u / (2 * Math.PI) * w), w);
        int j = Math.max(0, Math.min(h - 1, (int) Math.floor((Math.PI / 2 - lat) / Math.PI * h)));
        return data[j * w + i] & 0xFF;
    }
}
