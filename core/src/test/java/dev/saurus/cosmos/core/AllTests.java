package dev.saurus.cosmos.core;

import dev.saurus.cosmos.core.body.SolarSystem;

public final class AllTests {
    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        SolarSystem ss = SolarSystem.loadDefault();
        CoordinateTests.run();
        SolarSystemTests.run(ss);
        if (!Boolean.getBoolean("cosmos.quick")) ExtraTests.run(ss);
        TerrainTests.run(ss);
        System.out.printf("%n(%.1f s)%n", (System.nanoTime() - t0) / 1e9);
        System.exit(Check.finish());
    }
}
