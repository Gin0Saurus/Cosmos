package dev.saurus.cosmos.core.coords;

import dev.saurus.cosmos.core.math.Vec3d;

/**
 * Converts true universe positions into GPU-safe float render coordinates around the camera.
 *
 * <p>The simulation never changes scale: every object keeps its true UniversePos and true radius. Only the
 * <i>render transform</i> differs. Objects nearer than {@link #nearLimit} are drawn 1:1 in camera-relative metres
 * (rebased every frame, so float precision is relative to the camera, not to some world origin). Objects further
 * away are pulled in along their exact line of sight to a shell of radius {@code nearLimit .. farShell} and scaled
 * by the same factor, so their direction and angular size are mathematically identical to the true scene.
 * Depth ordering is preserved because the compression is monotonic in true distance.
 */
public final class FloatingOrigin {
    private UniversePos camera = UniversePos.ORIGIN;
    public final double nearLimit;
    public final double farShell;

    public FloatingOrigin(double nearLimit, double farShell) {
        if (!(farShell > nearLimit)) throw new IllegalArgumentException("farShell must exceed nearLimit");
        this.nearLimit = nearLimit; this.farShell = farShell;
    }

    public void rebase(UniversePos newCamera) { camera = newCamera; }
    public UniversePos camera() { return camera; }

    /** Result of projecting one object: render-space centre (camera at 0) and the uniform scale to apply. */
    public record Projected(float x, float y, float z, float scale, double trueDistance) {}

    public Projected project(UniversePos p) {
        Vec3d rel = camera.to(p);
        double d = rel.length();
        double s = scaleFor(d);
        Vec3d r = rel.mul(s);
        return new Projected((float) r.x(), (float) r.y(), (float) r.z(), (float) s, d);
    }

    /**
     * Monotonic compression: identity inside nearLimit; beyond, rendered distance = nearLimit +
     * (farShell - nearLimit) * (1 - nearLimit / d)  -> approaches farShell as d -> infinity.
     */
    public double scaleFor(double d) {
        if (d <= nearLimit) return 1.0;
        double rd = nearLimit + (farShell - nearLimit) * (1.0 - nearLimit / d);
        return rd / d;
    }
}
