package dev.saurus.cosmos.core.body;

import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.orbit.OrbitModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Any physical body: star, planet, moon, dwarf planet, black hole, exoplanet.
 * Positions are produced hierarchically: a body's orbit is relative to its parent, so precision is always local.
 */
public final class Body {
    public enum Kind { STAR, ROCKY, GAS_GIANT, ICE_GIANT, MOON, DWARF_PLANET, BLACK_HOLE, EXOPLANET }

    public record Atmosphere(double topM, double[] rayleigh, double rayleighH, double mieScatter, double mieExtinct,
                             double mieH, double mieG, double[] ozone, double[] tint, double surfacePressureBar) {}
    public record Ring(String name, double innerM, double outerM, double opticalDepth, double albedo) {}

    public final String id, name;
    public final Kind kind;
    public final Body parent;
    public final double gm;           // m^3 s^-2
    public final double[] radii;      // m (a, b, c)
    public final OrbitModel orbit;    // relative to parent; null for a system root
    public final RotationModel rotation;
    public final Atmosphere atmosphere;
    public final List<Ring> rings;
    public final double teffK;        // stars only (0 otherwise)
    public final double albedo;
    private final List<Body> children = new ArrayList<>();
    private final List<Provenance.Fact> facts = new ArrayList<>();
    private final List<String> aliases = new ArrayList<>();

    public Body(String id, String name, Kind kind, Body parent, double gm, double[] radii, OrbitModel orbit,
                RotationModel rotation, Atmosphere atmosphere, List<Ring> rings, double teffK, double albedo) {
        this.id = id; this.name = name; this.kind = kind; this.parent = parent; this.gm = gm; this.radii = radii;
        this.orbit = orbit; this.rotation = rotation; this.atmosphere = atmosphere;
        this.rings = rings == null ? List.of() : List.copyOf(rings); this.teffK = teffK; this.albedo = albedo;
        if (parent != null) parent.children.add(this);
    }

    public double meanRadius() { return Math.cbrt(radii[0] * radii[1] * radii[2]); }
    public double equatorialRadius() { return radii[0]; }
    public List<Body> children() { return Collections.unmodifiableList(children); }
    public List<Provenance.Fact> facts() { return Collections.unmodifiableList(facts); }
    public Body fact(String field, String value, Provenance p, String source) { facts.add(new Provenance.Fact(field, value, p, source)); return this; }
    public List<String> aliases() { return Collections.unmodifiableList(aliases); }
    public Body alias(String a) { aliases.add(a); return this; }

    /** Position relative to the system root (e.g. the Sun), ICRS metres. */
    public Vec3d positionInSystem(double t) {
        Vec3d p = Vec3d.ZERO;
        for (Body b = this; b != null && b.orbit != null; b = b.parent) p = p.add(b.orbit.position(t));
        return p;
    }

    public Vec3d velocityInSystem(double t) {
        Vec3d v = Vec3d.ZERO;
        for (Body b = this; b != null && b.orbit != null; b = b.parent) v = v.add(b.orbit.velocity(t));
        return v;
    }

    /** Hill-sphere radius around the parent (gravitational sphere of influence for navigation LOD). */
    public double hillRadius(double t) {
        if (parent == null || orbit == null || parent.gm <= 0) return Double.POSITIVE_INFINITY;
        double a = orbit.position(t).length();
        return a * Math.cbrt(gm / (3 * parent.gm));
    }

    /** True if the IAU rotation rate equals the mean orbital rate within 0.5% (synchronous rotation). */
    public boolean isTidallyLocked() {
        if (rotation == null || orbit == null || Double.isNaN(orbit.period())) return false;
        double rot = Math.abs(rotation.siderealPeriodSeconds()), orb = orbit.period();
        return Math.abs(rot - orb) / orb < 0.005;
    }

    @Override public String toString() { return name + " (" + kind + ")"; }
}
