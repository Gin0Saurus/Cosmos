package dev.saurus.cosmos.core.nav;

import dev.saurus.cosmos.core.Universe;
import dev.saurus.cosmos.core.blackhole.BlackHole;
import dev.saurus.cosmos.core.body.Body;
import dev.saurus.cosmos.core.catalog.Designations;
import dev.saurus.cosmos.core.catalog.DeepSkyCatalog;
import dev.saurus.cosmos.core.catalog.StarCatalog;
import dev.saurus.cosmos.core.coords.UniversePos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleFunction;

/**
 * Searchable index over every named real object: Solar System bodies, ~110k stars (proper names, Bayer designations,
 * HIP/HD/HR/Gliese ids), exoplanets and their hosts, NGC/IC/Messier objects, and black holes.
 */
public final class ObjectIndex {
    public enum Kind { PLANET, MOON, STAR, EXOPLANET, GALAXY, CLUSTER, NEBULA, BLACK_HOLE, OTHER }

    public record Entry(String name, Kind kind, String detail, double prominence, DoubleFunction<UniversePos> position, Object ref) {}
    public record Hit(Entry entry, int score) {}

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, List<Integer>> exact = new HashMap<>();
    private final List<String[]> keysOf = new ArrayList<>();

    public static String norm(String s) {
        return s.toLowerCase().replace("*", " star").replaceAll("[^a-z0-9+]+", "");
    }

    private void add(Entry e, String... keys) {
        int id = entries.size();
        entries.add(e);
        Set<String> ks = new LinkedHashSet<>();
        for (String k : keys) if (k != null && !k.isBlank()) ks.add(norm(k));
        keysOf.add(ks.toArray(new String[0]));
        for (String k : ks) exact.computeIfAbsent(k, x -> new ArrayList<>()).add(id);
    }

    public static ObjectIndex build(Universe u) {
        ObjectIndex ix = new ObjectIndex();
        for (Body b : u.solarSystem.all()) {
            Kind k = b.kind == Body.Kind.MOON ? Kind.MOON : b.kind == Body.Kind.STAR ? Kind.STAR : Kind.PLANET;
            List<String> keys = new ArrayList<>(b.aliases()); keys.add(b.name);
            ix.add(new Entry(b.name, k, b.parent == null ? "Solar System" : "orbits " + b.parent.name, 100,
                    t -> UniversePos.ofMetres(b.positionInSystem(t * 1)), b), keys.toArray(new String[0]));
        }
        StarCatalog s = u.stars;
        for (int i = 0; i < s.size(); i++) {
            final int si = i;
            List<String> keys = new ArrayList<>();
            if (s.name(i) != null) keys.add(s.name(i));
            for (String b : Designations.expandBayer(s.bayerRaw(i), s.constellation(i))) keys.add(b);
            String ids = s.ids(i);
            if (ids != null) for (String id : ids.split("\\|")) { keys.add(id); if (id.startsWith("Gl ")) keys.add("Gliese " + id.substring(3)); }
            if (keys.isEmpty()) continue;
            double prom = 30 - s.appMagFromSun(i);
            ix.add(new Entry(s.displayName(i), Kind.STAR, s.spectralType(i), prom, t -> s.position(si, t), si), keys.toArray(new String[0]));
        }
        for (Map.Entry<String, Integer> h : u.exoplanetHosts().entrySet()) {
            String host = h.getKey(); int si = h.getValue();
            ix.add(new Entry(host + " system", Kind.STAR, "planetary system", 29 - s.appMagFromSun(si), t -> s.position(si, t), host), host + " system");
            for (var row : u.exoplanets.hosts().get(host))
                ix.add(new Entry(row.name(), Kind.EXOPLANET, row.status() + ", " + row.method() + " " + row.year(), 45,
                        t -> s.position(si, t), row), row.name());
        }
        for (DeepSkyCatalog.Dso d : u.deepSky.all()) {
            if (!d.hasDistance()) continue;
            Kind k = switch (d.kind()) { case GALAXY, GALAXY_GROUP -> Kind.GALAXY; case OPEN_CLUSTER, GLOBULAR_CLUSTER, ASSOCIATION -> Kind.CLUSTER;
                case NEBULA, PLANETARY_NEBULA, SUPERNOVA_REMNANT -> Kind.NEBULA; default -> Kind.OTHER; };
            List<String> keys = new ArrayList<>(d.names()); keys.add(d.id());
            if (d.id().matches("(NGC|IC)0*\\d+[A-Z]?")) keys.add(d.id().replaceAll("^(NGC|IC)0*", "$1 "));
            for (String n : d.names()) if (n.endsWith(" Galaxy")) keys.add(n.substring(0, n.length() - 7));
            double prom = 20 - (Double.isNaN(d.mag()) ? 15 : d.mag()) + (d.names().isEmpty() ? 0 : 10);
            UniversePos p = d.position();
            ix.add(new Entry(d.displayName(), k, d.type() + (d.hubble().isEmpty() ? "" : " " + d.hubble()), prom, t -> p, d), keys.toArray(new String[0]));
        }
        for (BlackHole b : u.blackHoles) {
            List<String> keys = new ArrayList<>(b.aliases()); keys.add(b.name());
            ix.add(new Entry(b.name(), Kind.BLACK_HOLE, String.format("%.4g M_sun", b.massSun()), 90, t -> b.position(), b), keys.toArray(new String[0]));
        }
        return ix;
    }

    /** Ranked search: exact key > prefix > substring; ties broken by prominence (brightness / fame). */
    public List<Hit> search(String query, int limit) {
        String q = norm(query);
        if (q.isEmpty()) return List.of();
        Map<Integer, Integer> score = new HashMap<>();
        List<Integer> ex = exact.get(q);
        if (ex != null) for (int id : ex) score.merge(id, 1000, Math::max);
        if (score.size() < limit) {
            for (int id = 0; id < keysOf.size(); id++) {
                for (String k : keysOf.get(id)) {
                    int sc = k.startsWith(q) ? 500 - Math.min(400, k.length() - q.length()) : (q.length() >= 3 && k.contains(q) ? 200 : 0);
                    if (sc > 0) score.merge(id, sc, Math::max);
                }
            }
        }
        List<Hit> hits = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : score.entrySet()) hits.add(new Hit(entries.get(e.getKey()), e.getValue()));
        hits.sort(Comparator.comparingInt((Hit h) -> -h.score()).thenComparingDouble(h -> -h.entry().prominence()));
        return hits.subList(0, Math.min(limit, hits.size()));
    }

    public int size() { return entries.size(); }
}
