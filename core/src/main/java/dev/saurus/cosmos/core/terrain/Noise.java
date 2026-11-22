package dev.saurus.cosmos.core.terrain;

/**
 * Deterministic 3D gradient noise (improved Perlin) plus fractal helpers. Terrain detail is always sampled at the
 * 3D point on the sphere, never at (lat, lon), so it has no seam at the date line and no pinch at the poles.
 * Thread-safe and allocation-free.
 */
public final class Noise {
    private final int[] p = new int[512];

    public Noise(long seed) {
        int[] perm = new int[256];
        for (int i = 0; i < 256; i++) perm[i] = i;
        long s = seed ^ 0x9E3779B97F4A7C15L;
        for (int i = 255; i > 0; i--) {
            s = mix(s + i);
            int j = (int) Long.remainderUnsigned(s, i + 1);
            int t = perm[i]; perm[i] = perm[j]; perm[j] = t;
        }
        for (int i = 0; i < 512; i++) p[i] = perm[i & 255];
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double t, double a, double b) { return a + t * (b - a); }
    private static double grad(int h, double x, double y, double z) {
        int hh = h & 15;
        double u = hh < 8 ? x : y, v = hh < 4 ? y : hh == 12 || hh == 14 ? x : z;
        return ((hh & 1) == 0 ? u : -u) + ((hh & 2) == 0 ? v : -v);
    }

    /** Gradient noise in about [-1, 1]. */
    public double noise(double x, double y, double z) {
        double fx = Math.floor(x), fy = Math.floor(y), fz = Math.floor(z);
        int X = (int) ((long) fx & 255), Y = (int) ((long) fy & 255), Z = (int) ((long) fz & 255);
        x -= fx; y -= fy; z -= fz;
        double u = fade(x), v = fade(y), w = fade(z);
        int A = p[X] + Y, AA = p[A] + Z, AB = p[A + 1] + Z, B = p[X + 1] + Y, BA = p[B] + Z, BB = p[B + 1] + Z;
        return lerp(w, lerp(v, lerp(u, grad(p[AA], x, y, z), grad(p[BA], x - 1, y, z)),
                               lerp(u, grad(p[AB], x, y - 1, z), grad(p[BB], x - 1, y - 1, z))),
                       lerp(v, lerp(u, grad(p[AA + 1], x, y, z - 1), grad(p[BA + 1], x - 1, y, z - 1)),
                               lerp(u, grad(p[AB + 1], x, y - 1, z - 1), grad(p[BB + 1], x - 1, y - 1, z - 1))));
    }

    /** Fractal Brownian motion, wavelength in the same units as the coordinates. */
    public double fbm(double x, double y, double z, double wavelength, int octaves, double gain) {
        double f = 1 / wavelength, a = 1, sum = 0, norm = 0;
        for (int o = 0; o < octaves; o++) {
            sum += a * noise(x * f + o * 17.31, y * f - o * 9.77, z * f + o * 3.13);
            norm += a; a *= gain; f *= 2.03;
        }
        return sum / norm;
    }

    /** Ridged multifractal (sharp mountain crests), output roughly [0, 1]. */
    public double ridged(double x, double y, double z, double wavelength, int octaves) {
        double f = 1 / wavelength, a = 1, sum = 0, norm = 0, weight = 1;
        for (int o = 0; o < octaves; o++) {
            double n = 1 - Math.abs(noise(x * f + o * 11.1, y * f + o * 4.7, z * f - o * 8.3));
            n *= n * weight;
            weight = Math.min(1, Math.max(0, n * 2));
            sum += a * n; norm += a; a *= 0.5; f *= 2.07;
        }
        return sum / norm;
    }

    /** Deterministic hash of integer lattice coordinates to [0, 1). */
    public static double hash01(long seed, long a, long b, long c) {
        long h = mix(seed * 0x9E3779B97F4A7C15L + a * 0xC2B2AE3D27D4EB4FL + b * 0x165667B19E3779F9L + c * 0xD6E8FEB86659FD93L);
        return (h >>> 11) * 0x1.0p-53;
    }
}
