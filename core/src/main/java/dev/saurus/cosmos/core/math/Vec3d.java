package dev.saurus.cosmos.core.math;

/** Immutable double-precision 3-vector. */
public record Vec3d(double x, double y, double z) {
    public static final Vec3d ZERO = new Vec3d(0, 0, 0);
    public static final Vec3d X = new Vec3d(1, 0, 0), Y = new Vec3d(0, 1, 0), Z = new Vec3d(0, 0, 1);

    public Vec3d add(Vec3d o) { return new Vec3d(x + o.x, y + o.y, z + o.z); }
    public Vec3d sub(Vec3d o) { return new Vec3d(x - o.x, y - o.y, z - o.z); }
    public Vec3d mul(double s) { return new Vec3d(x * s, y * s, z * s); }
    public Vec3d addScaled(Vec3d o, double s) { return new Vec3d(x + o.x * s, y + o.y * s, z + o.z * s); }
    public double dot(Vec3d o) { return x * o.x + y * o.y + z * o.z; }
    public Vec3d cross(Vec3d o) { return new Vec3d(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
    public double lengthSq() { return x * x + y * y + z * z; }
    public double length() { return Math.sqrt(lengthSq()); }
    public Vec3d normalize() { double l = length(); return l == 0 ? this : mul(1.0 / l); }
    public double distance(Vec3d o) { return sub(o).length(); }
    public Vec3d negate() { return new Vec3d(-x, -y, -z); }

    /** Unit vector from right ascension / declination (radians), ICRS axes. */
    public static Vec3d fromRaDec(double ra, double dec) {
        double c = Math.cos(dec);
        return new Vec3d(c * Math.cos(ra), c * Math.sin(ra), Math.sin(dec));
    }

    @Override public String toString() { return String.format("(%.6g, %.6g, %.6g)", x, y, z); }
}
