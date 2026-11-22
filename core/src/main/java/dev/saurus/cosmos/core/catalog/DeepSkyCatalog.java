package dev.saurus.cosmos.core.catalog;

import dev.saurus.cosmos.core.body.Provenance;
import dev.saurus.cosmos.core.coords.UniversePos;
import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.util.Csv;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Galaxies, clusters and nebulae from OpenNGC (NGC + IC + LMC), with distance provenance per object. */
public final class DeepSkyCatalog {
    public enum Kind { GALAXY, GALAXY_GROUP, OPEN_CLUSTER, GLOBULAR_CLUSTER, NEBULA, PLANETARY_NEBULA, SUPERNOVA_REMNANT, ASSOCIATION, OTHER }

    public record Dso(String id, String type, Kind kind, double raDeg, double decDeg, double majArcmin, double minArcmin, double paDeg,
                      double mag, String hubble, double distKpc, Provenance distProvenance, String distSource, List<String> names) {
        public Vec3d direction() { return Vec3d.fromRaDec(Math.toRadians(raDeg), Math.toRadians(decDeg)); }
        public boolean hasDistance() { return !Double.isNaN(distKpc); }
        public UniversePos position() {
            Vec3d p = direction().mul(distKpc * 1000.0);
            return UniversePos.ofParsecs(p.x(), p.y(), p.z());
        }
        /** Physical major diameter in parsecs (from angular size and distance). */
        public double diameterPc() { return Double.isNaN(majArcmin) || !hasDistance() ? Double.NaN : Math.toRadians(majArcmin / 60) * distKpc * 1000; }
        public String displayName() { return names.isEmpty() ? id : names.get(names.size() > 1 && names.get(0).startsWith("M") && names.get(1).startsWith("Messier") && names.size() > 2 ? 2 : 0); }
    }

    private final List<Dso> all = new ArrayList<>();

    public static DeepSkyCatalog load(InputStream in) throws IOException {
        DeepSkyCatalog c = new DeepSkyCatalog();
        Csv.read(in, r -> {
            String k = r.s("dist_kind");
            Provenance p = switch (k) { case "M" -> Provenance.MEASURED; case "E" -> Provenance.ESTIMATE; case "P" -> Provenance.PROCEDURAL; default -> null; };
            List<String> names = new ArrayList<>();
            for (String s : r.s("names").split("\\|")) if (!s.isEmpty()) names.add(s);
            c.all.add(new Dso(r.s("id"), r.s("type"), kind(r.s("type")), r.d("ra_deg", 0), r.d("dec_deg", 0), r.d("maj_arcmin", Double.NaN),
                    r.d("min_arcmin", Double.NaN), r.d("pa_deg", 0), r.d("mag", Double.NaN), r.s("hubble"),
                    p == null ? Double.NaN : r.d("dist_kpc", Double.NaN), p, r.s("dist_source"), names));
        });
        return c;
    }
    public static DeepSkyCatalog loadDefault() throws IOException {
        try (InputStream in = DeepSkyCatalog.class.getResourceAsStream("/cosmos/data/deepsky.csv")) { return load(in); }
    }

    private static Kind kind(String t) {
        return switch (t) {
            case "G" -> Kind.GALAXY; case "GPair", "GTrpl", "GGroup" -> Kind.GALAXY_GROUP; case "OCl" -> Kind.OPEN_CLUSTER;
            case "GCl" -> Kind.GLOBULAR_CLUSTER; case "Neb", "HII", "RfN", "EmN", "Cl+N" -> Kind.NEBULA; case "PN" -> Kind.PLANETARY_NEBULA;
            case "SNR" -> Kind.SUPERNOVA_REMNANT; case "*Ass" -> Kind.ASSOCIATION; default -> Kind.OTHER;
        };
    }

    public List<Dso> all() { return all; }
    public Dso byId(String id) { for (Dso d : all) if (d.id().equalsIgnoreCase(id)) return d; return null; }

    public static double kpcToMetres(double kpc) { return kpc * Units.KPC; }
}
