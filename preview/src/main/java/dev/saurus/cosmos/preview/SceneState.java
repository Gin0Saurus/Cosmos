package dev.saurus.cosmos.preview;

import dev.saurus.cosmos.core.body.Body;
import dev.saurus.cosmos.core.body.SolarSystem;
import dev.saurus.cosmos.core.math.Mat3;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.time.SimTime;

import java.util.Locale;

/**
 * Prints the engine state a planet render needs, as JSON: body orientation (body-fixed -> ICRS), Sun direction from the
 * body, positions of other bodies relative to it (km), all at a UTC instant. The Fabric renderer receives the same
 * values every frame from the engine directly.
 * Usage: SceneState body yyyy mm dd hh mi [otherBody ...]
 */
public final class SceneState {
    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.ROOT);
        SolarSystem ss = SolarSystem.loadDefault();
        Body b = ss.get(a[0]);
        SimTime t = SimTime.fromUtc(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]), Integer.parseInt(a[5]), 0);
        double s = t.seconds();
        Vec3d p = ss.position(b, s);
        Vec3d sunDir = p.negate().normalize();
        Mat3 m = b.rotation.orientation(s);
        StringBuilder sb = new StringBuilder("{");
        sb.append(String.format("\"body\":\"%s\",\"jd_tdb\":%.6f,\"radii_km\":[%.4f,%.4f,%.4f],", b.name, t.julianDate(), b.radii[0] / 1e3, b.radii[1] / 1e3, b.radii[2] / 1e3));
        sb.append(String.format("\"sun_dir\":[%.9f,%.9f,%.9f],\"sun_dist_km\":%.1f,", sunDir.x(), sunDir.y(), sunDir.z(), p.length() / 1e3));
        sb.append(String.format("\"body_to_icrs\":[[%.9f,%.9f,%.9f],[%.9f,%.9f,%.9f],[%.9f,%.9f,%.9f]],", m.m00(), m.m01(), m.m02(), m.m10(), m.m11(), m.m12(), m.m20(), m.m21(), m.m22()));
        sb.append("\"others\":{");
        for (int i = 6; i < a.length; i++) {
            Body o = ss.get(a[i]);
            Vec3d r = ss.position(o, s).sub(p).mul(1e-3);
            sb.append(String.format("\"%s\":{\"pos_km\":[%.3f,%.3f,%.3f],\"radius_km\":%.3f}%s", o.name, r.x(), r.y(), r.z(), o.meanRadius() / 1e3, i < a.length - 1 ? "," : ""));
        }
        sb.append("}}");
        System.out.println(sb);
    }
}
