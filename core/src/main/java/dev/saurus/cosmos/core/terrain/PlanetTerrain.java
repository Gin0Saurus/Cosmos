package dev.saurus.cosmos.core.terrain;

/** Surface model of one solid body. Implementations are deterministic, thread-safe and allocation-light. */
public interface PlanetTerrain {
    String bodyId();
    double radiusM();
    /** Elevation in metres at latitude/longitude (radians). */
    double elevation(double lat, double lon);
    /** Surface type at a point given its elevation and local slope (rise over run, both in metres). */
    TerrainColumn column(double lat, double lon, double elevationM, double slope);
    /** Where the large-scale relief comes from, e.g. "NASA LRO LOLA LDEM (measured) + procedural craters < 25 km". */
    String provenance();
    /** True when the large-scale shape is measured data rather than procedural. */
    boolean measured();

    default TerrainColumn column(double lat, double lon) { return column(lat, lon, elevation(lat, lon), 0); }

    static double[] unit(double lat, double lon) { return PlanetProjection.unit(lat, lon); }
}
