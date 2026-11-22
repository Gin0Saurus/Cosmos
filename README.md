# Cosmos — a true-scale universe for Minecraft

A Fabric mod project that puts the real, measured universe into Minecraft at its actual scale: real orbits from
JPL data, 112,470 real stars, 5,393 known exoplanets, 12,101 galaxies, clusters and nebulae, and real black holes,
filled in with deterministic procedural generation everywhere catalogues run out. Every number shown to the player
is tagged **Measured**, **Scientific estimate** or **Procedural**.

## Status (honest)

| Layer | State |
|---|---|
| `core/` engine (coordinates, time, orbits, catalogues, search, galaxy model, procedural generation, flight physics, black-hole geodesics) | **Built and tested**: 128 automated checks pass, including positions vs NASA JPL Horizons |
| `shaders/` (stars, sky, planets with atmospheric scattering, black hole ray tracer, volumetric galaxy, HDR composite) | **Written and executed** on a real OpenGL driver (headless Mesa); outputs inspected (see `previews/`) |
| `preview/` tools (sky map generator, scene state, GLSL harness) | Working |
| `fabric/` Minecraft integration | **Not started yet.** The build sandbox cannot reach Mojang/Fabric servers, so no Minecraft-side code was written that could not be compiled. See ROADMAP.md |

## Build and test the engine

```
./build.sh            # javac only, no dependencies; builds build/core/cosmos-core.jar and runs all tests
```

Requires Java 21. Tests take ~12 s.

## Re-render the previews

```
pip install moderngl pillow numpy
java -cp build/core/main:build/core/preview dev.saurus.cosmos.preview.SkyMap build/preview/sky_earth_diffuse.f32 2048 0 0 0 1.0 --no-stars --starlist build/preview/stars_earth.bin
cd preview && python3 shots_sky.py && python3 shots_bh.py 1280 720 && python3 shots_galaxy.py
```

Planet previews additionally need the NASA-derived Earth/Moon textures (`shots_planets.py` documents where they come from).

## Layout

```
core/       engine, pure Java 21, no Minecraft dependency (becomes a Gradle subproject of the mod)
shaders/    GLSL 3.30 core sources shared by the mod and the preview harness
preview/    offline renderer + tools used to check visuals without Minecraft
tools/      data compiler (catalogues -> compact resources with provenance)
previews/   rendered screenshots from the actual shaders
docs/       ARCHITECTURE.md, DATA_SOURCES.md, ROADMAP.md, ACCURACY.md
```
