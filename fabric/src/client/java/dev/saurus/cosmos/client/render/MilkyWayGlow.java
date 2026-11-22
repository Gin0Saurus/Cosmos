package dev.saurus.cosmos.client.render;

import dev.saurus.cosmos.core.galaxy.MilkyWayModel;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.util.Hash;

/**
 * Unresolved starlight of the Milky Way as seen from any point: I = integral rho(s) exp(-tau(s)) ds through the
 * galaxy density model with clumpy dust (same model as procedural star generation). Output: equirect RGBA8 pixels
 * (gamma-encoded, ICRS axes, RA increasing to the left), ready for a GPU texture.
 */
public final class MilkyWayGlow {
    private MilkyWayGlow() {}

    public static int[] render(int w, int h, Vec3d observerPc) {
        float[] img = new float[w * h * 3];
        Vec3d o = MilkyWayModel.toGalactocentric(observerPc);
        java.util.stream.IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                double ra = Math.PI - (x + 0.5) / w * 2 * Math.PI, dec = Math.PI / 2 - (y + 0.5) / h * Math.PI;
                Vec3d d = Vec3d.fromRaDec(ra, dec);
                Vec3d g = MilkyWayModel.toGalactocentric(observerPc.add(d)).sub(o);
                double tau = 0, ir = 0, ig = 0, ib = 0, s = 1;
                for (int k = 0; k < 260 && s < 60000; k++) {
                    double ds = Math.max(5, s * 0.05);
                    double px = o.x() + g.x() * s, py = o.y() + g.y() * s, pz = o.z() + g.z() * s;
                    double rho = MilkyWayModel.density(px, py, pz);
                    double R = Math.hypot(px, py);
                    double arm = MilkyWayModel.armFactor(px, py, R);
                    double dust = 0.45e-3 * Math.exp(-(R - MilkyWayModel.R0_PC) / 3000 - Math.abs(pz) / 100) * arm * arm;
                    if (dust > 1e-7) { double n = fbm(px, py, pz * 3.0); dust *= 0.02 + 9.0 * n * n * n * n; }
                    double att = Math.exp(-tau);
                    double bf = Math.min(1, Math.exp(-R / 2500));
                    ir += rho * att * ds * (0.9 + 0.35 * bf); ig += rho * att * ds * (0.85 + 0.1 * bf); ib += rho * att * ds * (0.85 - 0.25 * bf);
                    tau += dust * ds;
                    s += ds;
                }
                int k = (y * w + x) * 3;
                double scale = 0.057 * Math.pow(2 * Math.PI / 2048, 2) * 7.0; // calibrated in the preview tools
                img[k] = (float) (ir * scale); img[k + 1] = (float) (ig * scale); img[k + 2] = (float) (ib * scale);
            }
        });
        int[] abgr = new int[w * h];
        for (int i = 0; i < w * h; i++) {
            int r = enc(img[i * 3]), g = enc(img[i * 3 + 1]), b = enc(img[i * 3 + 2]);
            abgr[i] = 0xFF000000 | (b << 16) | (g << 8) | r;
        }
        return abgr;
    }

    /** Linear radiance -> 8-bit with a soft curve that keeps faint glow visible; the shader applies a gain. */
    private static int enc(float v) { return (int) Math.min(255, Math.round(255 * Math.pow(Math.max(v, 0) * 40.0, 1 / 2.2))); }

    static double fbm(double x, double y, double z) {
        double sum = 0, amp = 0.5, f = 1.0 / 180.0;
        for (int o = 0; o < 4; o++) { sum += amp * vnoise(x * f, y * f, z * f, o); amp *= 0.5; f *= 2.03; }
        return sum;
    }
    static double vnoise(double x, double y, double z, int oct) {
        long ix = (long) Math.floor(x), iy = (long) Math.floor(y), iz = (long) Math.floor(z);
        double fx = x - ix, fy = y - iy, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz);
        double v = 0;
        for (int c = 0; c < 8; c++) {
            int dx = c & 1, dy = (c >> 1) & 1, dz = (c >> 2) & 1;
            double r = (Hash.of(0xD057 + oct, ix + dx, iy + dy, iz + dz) >>> 11) * 0x1.0p-53;
            v += r * (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
        }
        return v;
    }
}
