package dev.saurus.cosmos.core.body;

/**
 * Where a number came from. Shown in every info panel so the player can always distinguish
 * real data from estimates and from procedural approximations.
 */
public enum Provenance {
    /** Catalogued / measured value, copied from a named dataset. */
    MEASURED("Measured"),
    /** Derived from measured values with a stated physical relation (e.g. radius from L and Teff). */
    ESTIMATE("Scientific estimate"),
    /** Deterministic procedural approximation: plausible, not real. */
    PROCEDURAL("Procedural");

    public final String label;
    Provenance(String label) { this.label = label; }

    public record Fact(String field, String value, Provenance provenance, String source) {}
}
