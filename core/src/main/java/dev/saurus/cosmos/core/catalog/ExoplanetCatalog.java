package dev.saurus.cosmos.core.catalog;

import dev.saurus.cosmos.core.body.Body;
import dev.saurus.cosmos.core.body.Provenance;
import dev.saurus.cosmos.core.body.RotationModel;
import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.math.Mat3;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.orbit.Kepler;
import dev.saurus.cosmos.core.orbit.OrbitModel;
import dev.saurus.cosmos.core.time.SimTime;
import dev.saurus.cosmos.core.util.Csv;
import dev.saurus.cosmos.core.util.Hash;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Known exoplanets (Open Exoplanet Catalogue snapshot). Each host star becomes a {@link StarSystem}; planets get
 * Keplerian orbits. Every field missing from the catalogue is filled by a stated rule and flagged:
 * <ul>
 *   <li>a from P and M* via Kepler's third law (ESTIMATE); e unknown -> 0 (ESTIMATE, "assumed circular");</li>
 *   <li>radius from mass via Chen & Kipping 2017 mass-radius relation (ESTIMATE);</li>
 *   <li>orbital phase, node and, where missing, inclination -> deterministic from the planet name (PROCEDURAL);</li>
 *   <li>rotation: assumed tidally locked inside 0.1 AU, else 24 h (ESTIMATE / PROCEDURAL).</li>
 * </ul>
 */
public final class ExoplanetCatalog {
    public record Row(String name, String host, String hostHyg, String status, double massMj, double radiusRj, double periodD,
                      double aAu, double e, double periastronDeg, double inclDeg, double nodeDeg, double teqK, String method, String year,
                      double raDeg, double decDeg, double distPc, double starMass, double starRadius, double starTeff) {}

    private final Map<String, List<Row>> byHost = new LinkedHashMap<>();

    public static ExoplanetCatalog load(InputStream in) throws IOException {
        ExoplanetCatalog c = new ExoplanetCatalog();
        Csv.read(in, r -> {
            Row row = new Row(r.s("name"), r.s("host"), r.s("host_hyg"), r.s("status"), r.d("mass_mj", Double.NaN), r.d("radius_rj", Double.NaN),
                    r.d("period_d", Double.NaN), r.d("a_au", Double.NaN), r.d("e", Double.NaN), r.d("periastron_deg", Double.NaN),
                    r.d("incl_deg", Double.NaN), r.d("node_deg", Double.NaN), r.d("teq_k", Double.NaN), r.s("method"), r.s("year"),
                    r.d("ra_deg", Double.NaN), r.d("dec_deg", Double.NaN), r.d("dist_pc", Double.NaN), r.d("star_mass_msun", Double.NaN),
                    r.d("star_radius_rsun", Double.NaN), r.d("star_teff_k", Double.NaN));
            c.byHost.computeIfAbsent(row.host(), k -> new ArrayList<>()).add(row);
        });
        return c;
    }
    public static ExoplanetCatalog loadDefault() throws IOException {
        try (InputStream in = ExoplanetCatalog.class.getResourceAsStream("/cosmos/data/exoplanets.csv")) { return load(in); }
    }

    public Map<String, List<Row>> hosts() { return byHost; }
    public int planetCount() { return byHost.values().stream().mapToInt(List::size).sum(); }

    /**
     * Ensure every host exists in the star catalogue (adding hosts that Hipparcos does not contain, from the
     * exoplanet catalogue's own coordinates) and return host name -> star index.
     */
    public Map<String, Integer> attachHosts(StarCatalog stars) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Row>> e : byHost.entrySet()) {
            Row r = e.getValue().get(0);
            Integer idx = r.hostHyg().isEmpty() ? null : stars.indexOfHyg(r.hostHyg());
            if (idx == null) {
                if (Double.isNaN(r.raDeg()) || Double.isNaN(r.distPc())) continue; // no position: cannot place (e.g. microlensing w/o distance)
                Vec3d p = Vec3d.fromRaDec(Math.toRadians(r.raDeg()), Math.toRadians(r.decDeg())).mul(r.distPc());
                double teff = Double.isNaN(r.starTeff()) ? 5772 : r.starTeff();
                double rad = Double.isNaN(r.starRadius()) ? 1 : r.starRadius();
                double lum = rad * rad * Math.pow(teff / 5772.0, 4);
                double absMag = 4.83 - 2.5 * Math.log10(Math.max(lum, 1e-8)); // bolometric approximation (ESTIMATE)
                double app = absMag + 5 * (Math.log10(r.distPc()) - 1);
                idx = stars.add("", e.getKey(), "", "", "", p.x(), p.y(), p.z(), (float) app, (float) absMag,
                        Float.NaN, "", (float) lum, 0, 0, 0, (byte) 1);
            }
            out.put(e.getKey(), idx);
        }
        return out;
    }

    /** Build the planetary system of a host (planets orbit a star body of the given mass). */
    public StarSystem buildSystem(String host, StarCatalog stars, int starIndex) {
        List<Row> rows = byHost.get(host);
        Row h = rows.get(0);
        double mStar = Double.isNaN(h.starMass()) ? 1.0 : h.starMass();
        double teff = Double.isNaN(h.starTeff()) ? stars.teff(starIndex) : h.starTeff();
        double rStar = Double.isNaN(h.starRadius()) ? stars.radiusEstimateRsun(starIndex) : h.starRadius();
        Body star = new Body("star:" + host, stars.displayName(starIndex), Body.Kind.STAR, null, mStar * Units.GM_SUN,
                new double[]{rStar * Units.R_SUN, rStar * Units.R_SUN, rStar * Units.R_SUN}, null,
                RotationModel.fixedPeriod(Vec3d.Z, 25 * SimTime.DAY, 0), null, null, teff, 0);
        star.fact("Mass", fmt(mStar) + " M_sun", Double.isNaN(h.starMass()) ? Provenance.ESTIMATE : Provenance.MEASURED, "Open Exoplanet Catalogue")
            .fact("Radius", fmt(rStar) + " R_sun", Double.isNaN(h.starRadius()) ? Provenance.ESTIMATE : Provenance.MEASURED,
                    Double.isNaN(h.starRadius()) ? "Stefan-Boltzmann from HYG luminosity and B-V" : "Open Exoplanet Catalogue")
            .fact("Teff", Math.round(teff) + " K", Double.isNaN(h.starTeff()) ? Provenance.ESTIMATE : Provenance.MEASURED, "Open Exoplanet Catalogue / Ballesteros 2012");
        // Sky-plane frame: z points from the star towards the Solar System, x towards celestial north projected on the
        // sky. Catalogue inclinations are relative to the sky plane, so i = 90 deg is edge-on and transiting planets
        // really pass in front of their star as seen from Earth.
        Vec3d toSun = stars.positionPc(starIndex, 0).normalize().negate();
        Vec3d north = Vec3d.Z.sub(toSun.mul(Vec3d.Z.dot(toSun)));
        north = north.length() < 1e-9 ? Vec3d.X : north.normalize();
        Mat3 sky = Mat3.fromColumns(north, toSun.cross(north), toSun);
        List<Body> planets = new ArrayList<>();
        for (Row r : rows) planets.add(planet(r, star, mStar, sky));
        return new StarSystem(host, starIndex, star, planets);
    }

    private static Body planet(Row r, Body star, double mStar, Mat3 sky) {
        Hash.Rng rng = new Hash.Rng(Hash.of(r.name()));
        double mMj = r.massMj();
        double rRj = r.radiusRj();
        boolean radiusEst = Double.isNaN(rRj), massEst = Double.isNaN(mMj);
        if (massEst && !radiusEst) mMj = massFromRadius(rRj * 11.2089) / 317.83;
        if (massEst && radiusEst) mMj = 0.03; // ~10 Earth masses, procedural
        if (radiusEst) rRj = radiusFromMass(mMj * 317.83) / 11.2089;
        double aAu = r.aAu(), P = r.periodD();
        boolean aEst = Double.isNaN(aAu);
        double mu = mStar * Units.GM_SUN + mMj * Units.JUPITER_MASS * Units.G;
        if (aEst && !Double.isNaN(P)) aAu = Math.cbrt(mu * Math.pow(P * SimTime.DAY / (2 * Math.PI), 2)) / Units.AU;
        if (Double.isNaN(aAu)) aAu = rng.range(0.05, 5); // neither a nor P known (direct imaging rows): procedural
        double e = Double.isNaN(r.e()) ? 0 : Math.min(r.e(), 0.97);
        double inc = Double.isNaN(r.inclDeg()) ? rng.range(0, 180) : r.inclDeg();
        double node = Double.isNaN(r.nodeDeg()) ? rng.range(0, 360) : r.nodeDeg();
        double argp = Double.isNaN(r.periastronDeg()) ? rng.range(0, 360) : r.periastronDeg();
        double m0 = rng.range(0, 360);
        final double a = aAu * Units.AU, ee = e, ii = Math.toRadians(inc), nn = Math.toRadians(node), ww = Math.toRadians(argp), mm0 = Math.toRadians(m0);
        // Mean motion from the measured period when available (more precise than Kepler's law with an uncertain M*)
        final double n = !Double.isNaN(P) && P > 0 ? 2 * Math.PI / (P * SimTime.DAY) : Math.sqrt(mu / (a * a * a));
        OrbitModel orbit = new OrbitModel() {
            @Override public Vec3d position(double t) { return sky.apply(Kepler.position(new Kepler.Elements(a, ee, ii, nn, ww, mm0 + n * t))); }
            @Override public double period() { return 2 * Math.PI / n; }
        };
        double rM = rRj * Units.JUPITER_RADIUS;
        double rEarth = rM / Units.EARTH_RADIUS;
        Body.Kind kind = rEarth < 1.6 ? Body.Kind.ROCKY : rEarth < 6 ? Body.Kind.ICE_GIANT : Body.Kind.GAS_GIANT;
        boolean locked = aAu < 0.1;
        RotationModel rot = RotationModel.fixedPeriod(Vec3d.Z, locked ? orbit.period() : 86400, rng.range(0, 360));
        Body b = new Body("exo:" + r.name(), r.name(), kind, star, mMj * Units.JUPITER_MASS * Units.G,
                new double[]{rM, rM, rM}, orbit, rot, null, null, 0, 0.3);
        b.fact("Status", r.status(), Provenance.MEASURED, "Open Exoplanet Catalogue")
         .fact("Discovery", r.method() + " " + r.year(), Provenance.MEASURED, "Open Exoplanet Catalogue")
         .fact("Mass", fmt(mMj * 317.83) + " M_earth", massEst ? (Double.isNaN(r.radiusRj()) ? Provenance.PROCEDURAL : Provenance.ESTIMATE) : Provenance.MEASURED,
                 massEst ? "Chen & Kipping 2017 mass-radius relation" : "Open Exoplanet Catalogue (RV planets: minimum mass m sin i)")
         .fact("Radius", fmt(rEarth) + " R_earth", radiusEst ? Provenance.ESTIMATE : Provenance.MEASURED,
                 radiusEst ? "Chen & Kipping 2017 mass-radius relation" : "Open Exoplanet Catalogue")
         .fact("Semi-major axis", fmt(aAu) + " AU", aEst ? (Double.isNaN(P) ? Provenance.PROCEDURAL : Provenance.ESTIMATE) : Provenance.MEASURED,
                 aEst ? "Kepler's third law from period and stellar mass" : "Open Exoplanet Catalogue")
         .fact("Eccentricity", fmt(e), Double.isNaN(r.e()) ? Provenance.ESTIMATE : Provenance.MEASURED, Double.isNaN(r.e()) ? "unknown: assumed circular" : "Open Exoplanet Catalogue")
         .fact("Orbital phase / node", "deterministic", Provenance.PROCEDURAL, "not measured; seeded from planet name")
         .fact("Rotation", locked ? "assumed tidally locked" : "unknown (24 h placeholder)", locked ? Provenance.ESTIMATE : Provenance.PROCEDURAL,
                 locked ? "tidal locking timescale short inside ~0.1 AU" : "no measurement");
        if (!Double.isNaN(r.teqK())) b.fact("Equilibrium temperature", Math.round(r.teqK()) + " K", Provenance.MEASURED, "Open Exoplanet Catalogue");
        return b;
    }

    /** Chen & Kipping (2017, ApJ 834, 17) piecewise power law, Earth units, continuous at the breakpoints. */
    public static double radiusFromMass(double mEarth) {
        if (mEarth < 2.04) return 1.008 * Math.pow(mEarth, 0.279);
        double r204 = 1.008 * Math.pow(2.04, 0.279);
        if (mEarth < 131.6) return r204 * Math.pow(mEarth / 2.04, 0.589);
        double r132 = r204 * Math.pow(131.6 / 2.04, 0.589);
        return r132 * Math.pow(mEarth / 131.6, -0.044);
    }
    public static double massFromRadius(double rEarth) {
        double lo = 0.01, hi = 26000; // bisection on the monotone part (radii < ~14 R_earth)
        for (int i = 0; i < 80; i++) { double mid = Math.sqrt(lo * hi); if (radiusFromMass(mid) < rEarth) lo = mid; else hi = mid; }
        return Math.sqrt(lo * hi);
    }
    private static String fmt(double v) { return String.format("%.4g", v); }
}
