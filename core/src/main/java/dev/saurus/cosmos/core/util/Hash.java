package dev.saurus.cosmos.core.util;

/**
 * Deterministic hashing / random streams for procedural generation.
 * Same inputs -> same outputs on every machine and every run (pure integer arithmetic, no platform RNG),
 * which is what makes a procedurally generated location identical when the player returns years later.
 */
public final class Hash {
    private Hash() {}

    /** SplitMix64 finaliser (Steele, Lea, Flood 2014). */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public static long of(long seed, long a, long b, long c) {
        long h = mix(seed ^ 0x9E3779B97F4A7C15L);
        h = mix(h ^ a); h = mix(h ^ (b * 0x632BE59BD9B4E019L)); h = mix(h ^ (c * 0x85157AF5L));
        return h;
    }

    public static long of(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) { h ^= s.charAt(i); h *= 0x100000001b3L; }
        return mix(h);
    }

    /** Small deterministic PRNG stream seeded from a hash. */
    public static final class Rng {
        private long state;
        public Rng(long seed) { state = seed; }
        public long nextLong() { state += 0x9E3779B97F4A7C15L; return mix(state); }
        public double nextDouble() { return (nextLong() >>> 11) * 0x1.0p-53; }
        public double range(double lo, double hi) { return lo + (hi - lo) * nextDouble(); }
        public int nextInt(int bound) { return (int) ((nextLong() >>> 33) % bound); }
        public double gaussian() { // Box-Muller
            double u = Math.max(1e-300, nextDouble()), v = nextDouble();
            return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
        }
        /** Poisson sample (Knuth for small mean, normal approximation for large). */
        public int poisson(double mean) {
            if (mean <= 0) return 0;
            if (mean > 40) return Math.max(0, (int) Math.round(mean + Math.sqrt(mean) * gaussian()));
            double L = Math.exp(-mean), p = 1; int k = 0;
            do { k++; p *= nextDouble(); } while (p > L);
            return k - 1;
        }
    }
}
