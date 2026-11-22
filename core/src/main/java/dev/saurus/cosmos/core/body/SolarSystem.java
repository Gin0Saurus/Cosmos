package dev.saurus.cosmos.core.body;

import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.orbit.JplPlanetOrbit;
import dev.saurus.cosmos.core.orbit.MeanElementOrbit;
import dev.saurus.cosmos.core.orbit.OrbitModel;
import dev.saurus.cosmos.core.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The real Solar System, built from solar_system.json (see that file's "sources" block). Heliocentric ICRS. */
public final class SolarSystem {
    public final Body sun;
    private final Map<String, Body> byName = new LinkedHashMap<>();
    private final Map<Integer, Body> byNaif = new LinkedHashMap<>();
    public final Map<String, Object> raw;

    private SolarSystem(Map<String, Object> raw) {
        this.raw = raw;
        String srcOrbitP = src("orbit_planets"), srcOrbitM = src("orbit_moons"), srcRot = src("rotation"), srcRad = src("radii");
        Map<String, Object> s = Json.obj(raw.get("sun"));
        sun = new Body("sol", "Sun", Body.Kind.STAR, null, Json.num(s, "gm_m3s2"), km(s.get("radii_km")), null,
                rot(s.get("rotation")), null, null, Json.num(s, "teff_k"), 0);
        sun.alias("Sol").fact("Radius", "695,700 km", Provenance.MEASURED, srcRad).fact("GM", "1.3271244e20 m^3/s^2", Provenance.MEASURED, src("gm_sun"));
        register(10, sun);

        Map<String, Object> atm = Json.obj(raw.get("atmospheres"));
        for (Object o : Json.arr(raw.get("planets"))) {
            Map<String, Object> p = Json.obj(o);
            double gm = Json.num(p, "mass_kg") * Units.G;
            Body.Kind kind = switch (Json.str(p, "kind")) { case "gas_giant" -> Body.Kind.GAS_GIANT; case "ice_giant" -> Body.Kind.ICE_GIANT; default -> Body.Kind.ROCKY; };
            OrbitModel orbit = new JplPlanetOrbit(Json.nums(p.get("elements")), Json.nums(p.get("rates")), sun.gm + gm);
            if ("EMB".equals(Json.str(p, "elements_body"))) orbit = earthOrbit = new EarthFromBarycentre(orbit);
            Map<String, Object> surf = Json.obj(p.get("surface"));
            Body b = new Body(Json.str(p, "name").toLowerCase(), Json.str(p, "name"), kind, sun, gm, km(p.get("radii_km")), orbit,
                    rot(p.get("rotation")), atmosphere(atm, Json.str(p, "atmosphere"), surf.get("pressure_bar")), rings(p.get("rings")),
                    0, Json.num(p, "albedo", 0.3));
            b.fact("Orbit", "JPL approximate elements (1800-2050)", Provenance.MEASURED, srcOrbitP)
             .fact("Radii", fmtKm(b.radii), Provenance.MEASURED, srcRad)
             .fact("Mass", String.format("%.6g kg", Json.num(p, "mass_kg")), Provenance.MEASURED, src("mass_planets"))
             .fact("Rotation", String.format("%.5f h sidereal", b.rotation.siderealPeriodSeconds() / 3600), Provenance.MEASURED, srcRot);
            if (surf.get("pressure_bar") instanceof Number n) b.fact("Surface pressure", n + " bar", Provenance.MEASURED, src("surface"));
            b.fact("Mean temperature", surf.get("mean_temp_c") + " C", Provenance.MEASURED, src("surface"));
            register((int) Json.num(p, "id"), b);
        }
        // Earth's JPL elements are those of the Earth-Moon barycentre; corrected after the Moon exists (below).
        List<Map<String, Object>> deferred = new ArrayList<>();
        for (Object o : Json.arr(raw.get("moons"))) deferred.add(Json.obj(o));
        for (int pass = 0; pass < 2; pass++) {
            for (Map<String, Object> m : deferred) {
                int id = (int) Json.num(m, "id");
                if (byNaif.containsKey(id)) continue;
                Body parent = byNaif.get((int) Json.num(m, "parent"));
                if (parent == null) continue;
                buildMoon(m, parent, atm, srcOrbitM, srcRot, srcRad, srcOrbitP);
            }
        }
        // Replace Earth's EMB orbit with Earth = EMB - Moon * GM_moon/(GM_earth+GM_moon)
        Body earth = byNaif.get(399), moon = byNaif.get(301);
        if (earth != null && moon != null) earthFromBarycentre(earth, moon);
    }

    /** Reference vectors (JPL Horizons) used for calibration and validation; null if not loaded. */
    public Map<String, Object> horizons;

    /** Calibrate satellite mean-element phases/mean motions against JPL Horizons (fit epochs only). */
    public void calibrateMoons(Map<String, Object> h) {
        this.horizons = h;
        double[] jd = Json.nums(h.get("epochs_jd_tdb"));
        double[] fit = Json.nums(h.get("fit_epochs"));
        Map<String, Object> moons = Json.obj(h.get("moons"));
        for (Map.Entry<String, Object> en : moons.entrySet()) {
            Body b = byNaif.get(Integer.parseInt(en.getKey()));
            if (b == null || !(b.orbit instanceof MeanElementOrbit meo)) continue;
            List<Object> vecs = Json.arr(en.getValue());
            int i0 = (int) fit[0], i1 = (int) fit[1];
            double t0 = (jd[i0] - dev.saurus.cosmos.core.time.SimTime.J2000_JD) * 86400, t1 = (jd[i1] - dev.saurus.cosmos.core.time.SimTime.J2000_JD) * 86400;
            Vec3d r0 = vec(vecs.get(i0)), r1 = vec(vecs.get(i1));
            double prior = Math.toRadians(Math.abs(b.rotation.pm()[1])); // synchronous rotators: spin rate == mean motion
            meo.calibrate(t0, r0, t1, r1, prior, "phase + mean motion fitted to JPL Horizons at JD " + jd[i0] + " and " + jd[i1]);
            b.fact("Orbit calibration", meo.calibration(), Provenance.MEASURED, "JPL Horizons (ssd.jpl.nasa.gov/api/horizons.api)");
        }
    }

    public static Vec3d vec(Object o) { double[] d = Json.nums(o); return new Vec3d(d[0], d[1], d[2]).mul(1000); }

    private void buildMoon(Map<String, Object> m, Body parent, Map<String, Object> atm, String srcOrbitM, String srcRot, String srcRad, String srcOrbitP) {
        int id = (int) Json.num(m, "id");
        String name = Json.str(m, "name");
        double gm = m.containsKey("gm_km3s2") ? Json.num(m, "gm_km3s2") * 1e9 : Json.num(m, "mass_kg") * Units.G;
        RotationModel rot = rot(m.get("rotation"));
        OrbitModel orbit;
        Body.Kind kind = Body.Kind.MOON;
        if (m.containsKey("elements")) { // Pluto: heliocentric JPL elements
            orbit = new JplPlanetOrbit(Json.nums(m.get("elements")), Json.nums(m.get("rates")), sun.gm + gm);
            kind = Body.Kind.DWARF_PLANET;
        } else if (id == 301) {
            orbit = new dev.saurus.cosmos.core.orbit.LunarTheory();
        } else {
            Map<String, Object> el = Json.obj(m.get("el"));
            String frame = Json.str(m, "frame");
            MeanElementOrbit.Plane plane; Vec3d pole;
            if ("ecliptic".equals(frame)) { plane = MeanElementOrbit.Plane.ECLIPTIC; pole = null; }
            else if ("laplace".equals(frame)) { plane = MeanElementOrbit.Plane.LAPLACE; pole = Vec3d.fromRaDec(Math.toRadians(Json.num(el, "lra")), Math.toRadians(Json.num(el, "ldec"))); }
            else { plane = MeanElementOrbit.Plane.EQUATORIAL; pole = parent.rotation.spinAxis(0); }
            orbit = new MeanElementOrbit(Json.num(el, "a"), Json.num(el, "e"), Json.num(el, "w"), Json.num(el, "M"), Json.num(el, "i"),
                    Json.num(el, "node"), Json.num(el, "P"), Json.num(el, "Papsis", 0), Json.num(el, "Pnode", 0), plane, pole);
        }
        Body b = new Body(name.toLowerCase(), name, kind, parent, gm, km(m.get("radii_km")), orbit, rot,
                atmosphere(atm, Json.str(m, "atmosphere"), null), null, 0, 0.3);
        String orbitDesc = kind == Body.Kind.DWARF_PLANET ? "JPL approximate elements"
                : id == 301 ? "ELP-2000/82 main terms (Meeus ch. 47)" : "JPL mean elements (no short-period terms)";
        b.fact("Orbit", orbitDesc, Provenance.MEASURED, kind == Body.Kind.DWARF_PLANET ? srcOrbitP
                : id == 301 ? "Chapront-Touze & Chapront ELP-2000/82, truncated per Meeus, Astronomical Algorithms 2nd ed." : srcOrbitM)
         .fact("Radii", fmtKm(b.radii), Provenance.MEASURED, srcRad)
         .fact("GM", String.format("%.6g km^3/s^2", gm / 1e9), Provenance.MEASURED, src("gm_moons"));
        if (b.isTidallyLocked()) b.fact("Rotation", "synchronous (tidally locked)", Provenance.MEASURED, srcRot);
        register(id, b);
    }

    /** JPL Table 1 gives the Earth-Moon barycentre; Earth = EMB - r_moon * GM_moon / (GM_earth + GM_moon). */
    private static final class EarthFromBarycentre implements OrbitModel {
        final OrbitModel emb; Body earth, moon;
        EarthFromBarycentre(OrbitModel emb) { this.emb = emb; }
        @Override public Vec3d position(double t) {
            if (moon == null) return emb.position(t);
            return emb.position(t).sub(moon.orbit.position(t).mul(moon.gm / (earth.gm + moon.gm)));
        }
        @Override public double period() { return emb.period(); }
    }
    private EarthFromBarycentre earthOrbit;

    private void earthFromBarycentre(Body earth, Body moon) { earthOrbit.earth = earth; earthOrbit.moon = moon; }

    /** Heliocentric ICRS position (m). */
    public Vec3d position(Body b, double t) { return b.positionInSystem(t); }

    public Vec3d velocity(Body b, double t) {
        double h = 30.0;
        return position(b, t + h).sub(position(b, t - h)).mul(1 / (2 * h));
    }

    public Body get(String name) { return byName.get(name.toLowerCase()); }
    public Body naif(int id) { return byNaif.get(id); }
    public List<Body> all() { return new ArrayList<>(byName.values()); }

    private void register(int naif, Body b) { byName.put(b.name.toLowerCase(), b); byNaif.put(naif, b); }

    private String src(String k) { return Json.str(Json.obj(raw.get("sources")), k); }

    private static double[] km(Object o) { double[] r = Json.nums(o); for (int i = 0; i < r.length; i++) r[i] *= 1000; return r; }
    private static String fmtKm(double[] r) { return String.format("%.1f x %.1f x %.1f km", r[0] / 1e3, r[1] / 1e3, r[2] / 1e3); }
    private static RotationModel rot(Object o) {
        Map<String, Object> r = Json.obj(o);
        return new RotationModel(Json.nums(r.get("ra")), Json.nums(r.get("dec")), Json.nums(r.get("pm")));
    }
    private static Body.Atmosphere atmosphere(Map<String, Object> atm, String key, Object pressure) {
        if (key == null || !atm.containsKey(key)) return null;
        Map<String, Object> a = Json.obj(atm.get(key));
        return new Body.Atmosphere(Json.num(a, "top_km") * 1e3, Json.nums(a.get("rayleigh_per_m")), Json.num(a, "rayleigh_h_km") * 1e3,
                Json.num(a, "mie_scatter_per_m"), Json.num(a, "mie_extinct_per_m"), Json.num(a, "mie_h_km") * 1e3, Json.num(a, "mie_g"),
                a.containsKey("ozone_absorb_per_m") ? Json.nums(a.get("ozone_absorb_per_m")) : new double[3],
                a.containsKey("dust_tint") ? Json.nums(a.get("dust_tint")) : new double[]{1, 1, 1},
                pressure instanceof Number n ? n.doubleValue() : Double.NaN);
    }
    private static List<Body.Ring> rings(Object o) {
        if (o == null) return null;
        List<Body.Ring> out = new ArrayList<>();
        for (Object x : Json.arr(o)) {
            Map<String, Object> r = Json.obj(x);
            out.add(new Body.Ring(Json.str(r, "name"), Json.num(r, "inner_km") * 1e3, Json.num(r, "outer_km") * 1e3,
                    Json.num(r, "tau"), Json.num(r, "albedo", 0.3)));
        }
        return out;
    }

    public static SolarSystem load(InputStream in) throws IOException {
        return new SolarSystem(Json.obj(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))));
    }
    public static SolarSystem loadDefault() throws IOException {
        SolarSystem ss;
        try (InputStream in = SolarSystem.class.getResourceAsStream("/cosmos/data/solar_system.json")) {
            if (in == null) throw new IOException("solar_system.json missing from classpath");
            ss = load(in);
        }
        try (InputStream in = SolarSystem.class.getResourceAsStream("/cosmos/data/horizons_moons.json")) {
            if (in != null) ss.calibrateMoons(Json.obj(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))));
        }
        return ss;
    }
}
