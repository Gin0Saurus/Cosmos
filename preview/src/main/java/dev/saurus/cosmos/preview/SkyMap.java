package dev.saurus.cosmos.preview;

import dev.saurus.cosmos.core.Universe;
import dev.saurus.cosmos.core.catalog.StarCatalog;
import dev.saurus.cosmos.core.galaxy.MilkyWayModel;
import dev.saurus.cosmos.core.math.Vec3d;

import java.io.DataOutputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Renders the sky as seen from any point in the universe into an HDR equirectangular map (ICRS axes): every real
 * catalogue star re-projected from the observer's position (so constellations distort correctly as you travel), plus
 * the diffuse Milky Way glow integrated through the galaxy density model with dust extinction.
 *
 * <p>This is the same data path the in-game sky renderer uses; here it writes a float32 RGB file
 * (header: int width, int height, little-endian floats) that the GLSL preview harness loads as a texture.
 * Usage: SkyMap out.f32 width observerX_pc observerY_pc observerZ_pc [diffuseGain]
 */
public final class SkyMap {
    public static void main(String[] args) throws IOException {
        String out = args[0];
        int w = Integer.parseInt(args[1]), h = w / 2;
        Vec3d obs = new Vec3d(Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4]));
        double diffuseGain = args.length > 5 ? Double.parseDouble(args[5]) : 1.0;
        boolean drawStars = true; String starList = null;
        for (int i = 6; i < args.length; i++) {
            if (args[i].equals("--no-stars")) drawStars = false;
            if (args[i].equals("--starlist")) starList = args[++i];
        }
        Universe u = Universe.load();
        float[] img = new float[w * h * 3];
        diffuse(img, w, h, obs, diffuseGain);
        if (drawStars) stars(img, w, h, obs, u.stars);
        if (starList != null) writeStarList(starList, obs, u.stars);
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(out), 1 << 20))) {
            ByteBuffer bb = ByteBuffer.allocate(8 + img.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(w).putInt(h);
            for (float f : img) bb.putFloat(f);
            o.write(bb.array());
        }
        System.out.println("wrote " + out + " (" + w + "x" + h + ") from observer " + obs + " pc");
    }

    /** Per-star records for the GPU point-sprite renderer: dir (3f), apparent mag (1f), linear sRGB (3f). */
    static void writeStarList(String path, Vec3d obs, StarCatalog c) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(4 + c.size() * 28).order(ByteOrder.LITTLE_ENDIAN);
        int count = 0; bb.putInt(0);
        for (int i = 0; i < c.size(); i++) {
            Vec3d d = c.positionPc(i, 0).sub(obs);
            double dist = d.length();
            if (dist < 1e-6) continue;
            double m = c.absMag(i) + 5 * (Math.log10(dist) - 1);
            if (m > 14) continue;
            Vec3d n = d.mul(1 / dist); float[] col = c.colour(i);
            bb.putFloat((float) n.x()).putFloat((float) n.y()).putFloat((float) n.z()).putFloat((float) m)
              .putFloat(col[0]).putFloat(col[1]).putFloat(col[2]);
            count++;
        }
        bb.putInt(0, count);
        try (FileOutputStream o = new FileOutputStream(path)) { o.write(bb.array(), 0, 4 + count * 28); }
        System.out.println("wrote " + count + " stars to " + path);
    }

    /** Deterministic 3D value-noise fBm (PROCEDURAL dust turbulence), period-free, from the integer hash. */
    static double fbm(double x, double y, double z) {
        double sum = 0, amp = 0.5, f = 1.0 / 180.0;
        for (int o = 0; o < 5; o++) { sum += amp * vnoise(x * f, y * f, z * f, o); amp *= 0.5; f *= 2.03; }
        return sum; // ~[0,1)
    }
    static double vnoise(double x, double y, double z, int oct) {
        long ix = (long) Math.floor(x), iy = (long) Math.floor(y), iz = (long) Math.floor(z);
        double fx = x - ix, fy = y - iy, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz);
        double v = 0;
        for (int c = 0; c < 8; c++) {
            int dx = c & 1, dy = (c >> 1) & 1, dz = (c >> 2) & 1;
            double r = (dev.saurus.cosmos.core.util.Hash.of(0xD057 + oct, ix + dx, iy + dy, iz + dz) >>> 11) * 0x1.0p-53;
            v += r * (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
        }
        return v;
    }

    /** Equirect pixel -> ICRS direction. u: RA from 0 at the centre column increasing to the left (sky view). */
    static Vec3d dir(int x, int y, int w, int h) {
        double ra = Math.PI - (x + 0.5) / w * 2 * Math.PI;
        double dec = Math.PI / 2 - (y + 0.5) / h * Math.PI;
        return Vec3d.fromRaDec(ra, dec);
    }

    static void stars(float[] img, int w, int h, Vec3d obs, StarCatalog c) {
        double sigma = 0.6; // pixels
        for (int i = 0; i < c.size(); i++) {
            Vec3d d = c.positionPc(i, 0).sub(obs);
            double dist = d.length();
            if (dist < 1e-6) continue;
            double m = c.absMag(i) + 5 * (Math.log10(dist) - 1);
            if (m > 13) continue;
            double flux = Math.pow(10, -0.4 * m);
            Vec3d n = d.mul(1 / dist);
            double ra = Math.atan2(n.y(), n.x()), dec = Math.asin(n.z());
            double px = (Math.PI - ra) / (2 * Math.PI) * w, py = (Math.PI / 2 - dec) / Math.PI * h;
            px = ((px % w) + w) % w;
            float[] col = c.colour(i);
            double cosDec = Math.max(Math.cos(dec), 0.02);
            int rx = (int) Math.ceil(3 * sigma / cosDec), ry = 3;
            double norm = 1 / (2 * Math.PI * sigma * sigma);
            for (int yy = (int) py - ry; yy <= (int) py + ry; yy++) {
                if (yy < 0 || yy >= h) continue;
                for (int xx = (int) px - rx; xx <= (int) px + rx; xx++) {
                    double dx = ((xx + 0.5) - px) * cosDec, dy = (yy + 0.5) - py;
                    double wgt = norm * Math.exp(-(dx * dx + dy * dy) / (2 * sigma * sigma));
                    int xi = ((xx % w) + w) % w, k = (yy * w + xi) * 3;
                    img[k] += (float) (flux * wgt * col[0]); img[k + 1] += (float) (flux * wgt * col[1]); img[k + 2] += (float) (flux * wgt * col[2]);
                }
            }
        }
    }

    /**
     * Diffuse light: I = integral rho_lum(s) exp(-tau(s)) ds along the ray (surface brightness is independent of
     * distance). Dust: thin exponential layer (scale length 3 kpc, height 100 pc), PROCEDURAL approximation.
     * Unresolved light uses the same density model as procedural star generation, so it is consistent with it.
     */
    static void diffuse(float[] img, int w, int h, Vec3d obs, double gain) {
        int dw = Math.max(64, w / 4), dh = dw / 2;
        float[] low = new float[dw * dh * 3];
        Vec3d o = MilkyWayModel.toGalactocentric(obs);
        java.util.stream.IntStream.range(0, dh).parallel().forEach(y -> {
            for (int x = 0; x < dw; x++) {
                Vec3d d = dir(x, y, dw, dh);
                Vec3d g = MilkyWayModel.toGalactocentric(obs.add(d)).sub(o); // galactocentric direction
                double tau = 0, ir = 0, ig = 0, ib = 0, s = 1;
                for (int k = 0; k < 400 && s < 60000; k++) {
                    double ds = Math.max(5, s * 0.04);
                    double px = o.x() + g.x() * s, py = o.y() + g.y() * s, pz = o.z() + g.z() * s;
                    double rho = MilkyWayModel.density(px, py, pz);
                    double R = Math.hypot(px, py);
                    double dust = 0.45e-3 * Math.exp(-(R - MilkyWayModel.R0_PC) / 3000 - Math.abs(pz) / 100)
                            * MilkyWayModel.armFactor(px, py, R) * MilkyWayModel.armFactor(px, py, R);
                    if (dust > 1e-7) { double n = fbm(px, py, pz * 3.0); dust *= 0.02 + 9.0 * n * n * n * n; } // clumpy molecular-cloud complexes
                    double att = Math.exp(-tau);
                    // older bulge light is redder, disc/arms bluer (population colour, PROCEDURAL)
                    double bulgeFrac = Math.min(1, Math.exp(-R / 2500));
                    ir += rho * att * ds * (0.9 + 0.35 * bulgeFrac); ig += rho * att * ds * (0.85 + 0.1 * bulgeFrac); ib += rho * att * ds * (0.85 - 0.25 * bulgeFrac);
                    tau += dust * ds;
                    s += ds;
                }
                int k = (y * dw + x) * 3;
                // calibration: a line integral of ~3000 pc of local-density disc ~ Milky Way surface brightness ~21 mag/arcsec^2
                double pix = Math.pow(2 * Math.PI / w, 2);
                double scale = 0.057 * pix * gain;
                low[k] = (float) (ir * scale); low[k + 1] = (float) (ig * scale); low[k + 2] = (float) (ib * scale);
            }
        });
        // bilinear upsample
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double fx = (x + 0.5) / w * dw - 0.5, fy = (y + 0.5) / h * dh - 0.5;
            int x0 = (int) Math.floor(fx), y0 = Math.max(0, Math.min(dh - 2, (int) Math.floor(fy)));
            double ax = fx - x0, ay = Math.max(0, Math.min(1, fy - y0));
            for (int c = 0; c < 3; c++) {
                double v00 = low[(y0 * dw + Math.floorMod(x0, dw)) * 3 + c], v10 = low[(y0 * dw + Math.floorMod(x0 + 1, dw)) * 3 + c];
                double v01 = low[((y0 + 1) * dw + Math.floorMod(x0, dw)) * 3 + c], v11 = low[((y0 + 1) * dw + Math.floorMod(x0 + 1, dw)) * 3 + c];
                img[(y * w + x) * 3 + c] += (float) ((v00 * (1 - ax) + v10 * ax) * (1 - ay) + (v01 * (1 - ax) + v11 * ax) * ay);
            }
        }
    }
}
