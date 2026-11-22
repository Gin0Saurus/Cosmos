package dev.saurus.cosmos.core.math;

/** Immutable row-major 3x3 rotation/linear matrix. */
public record Mat3(double m00, double m01, double m02,
                   double m10, double m11, double m12,
                   double m20, double m21, double m22) {

    public static final Mat3 IDENTITY = new Mat3(1, 0, 0, 0, 1, 0, 0, 0, 1);

    public Vec3d apply(Vec3d v) {
        return new Vec3d(m00 * v.x() + m01 * v.y() + m02 * v.z(),
                         m10 * v.x() + m11 * v.y() + m12 * v.z(),
                         m20 * v.x() + m21 * v.y() + m22 * v.z());
    }

    public Mat3 mul(Mat3 o) {
        return new Mat3(
            m00 * o.m00 + m01 * o.m10 + m02 * o.m20, m00 * o.m01 + m01 * o.m11 + m02 * o.m21, m00 * o.m02 + m01 * o.m12 + m02 * o.m22,
            m10 * o.m00 + m11 * o.m10 + m12 * o.m20, m10 * o.m01 + m11 * o.m11 + m12 * o.m21, m10 * o.m02 + m11 * o.m12 + m12 * o.m22,
            m20 * o.m00 + m21 * o.m10 + m22 * o.m20, m20 * o.m01 + m21 * o.m11 + m22 * o.m21, m20 * o.m02 + m21 * o.m12 + m22 * o.m22);
    }

    public Mat3 transpose() { return new Mat3(m00, m10, m20, m01, m11, m21, m02, m12, m22); }

    /** Active right-handed rotation of a vector about X by angle a (radians). */
    public static Mat3 rotX(double a) { double c = Math.cos(a), s = Math.sin(a); return new Mat3(1, 0, 0, 0, c, -s, 0, s, c); }
    public static Mat3 rotY(double a) { double c = Math.cos(a), s = Math.sin(a); return new Mat3(c, 0, s, 0, 1, 0, -s, 0, c); }
    public static Mat3 rotZ(double a) { double c = Math.cos(a), s = Math.sin(a); return new Mat3(c, -s, 0, s, c, 0, 0, 0, 1); }

    public static Mat3 fromColumns(Vec3d a, Vec3d b, Vec3d c) {
        return new Mat3(a.x(), b.x(), c.x(), a.y(), b.y(), c.y(), a.z(), b.z(), c.z());
    }
    public Vec3d col(int i) { return i == 0 ? new Vec3d(m00, m10, m20) : i == 1 ? new Vec3d(m01, m11, m21) : new Vec3d(m02, m12, m22); }
}
