# Cosmos — a true-scale universe for Minecraft

A Fabric mod project that puts the real, measured universe into Minecraft at its actual scale: real orbits from
JPL data, 112,470 real stars, 5,393 known exoplanets, 12,101 galaxies, clusters and nebulae, and real black holes,
filled in with deterministic procedural generation everywhere catalogues run out. Every number shown to the player
is tagged **Measured**, **Scientific estimate** or **Procedural**.

## Status (honest)

| Layer | State |
|---|---|
| `core/` engine (coordinates, orbits, catalogues, terrain models, real elevation data, flight physics, black-hole geodesics) | **Built and tested**: 168 automated checks, incl. JPL Horizons positions and real landmarks (Everest, Mariana Trench, Olympus Mons, Hellas, Valles Marineris, South Pole–Aitken, Sahara, Amazon…) |
| `fabric/` mod for Minecraft 26.3 | **Built by CI and run in a real Minecraft client**: lands on Earth/Moon/Mars/Europa/Io/Titan/Pluto at real coordinates, measures gravity by jumping, wraps round a planet, launches to space and lands again; screenshots inspected every build |
| Phases 1–2 (walkable spherical worlds, launch/land) | Working, with the engine limitations listed in `docs/ARCHITECTURE_V2.md` |
| v0.3: caves & lava tubes, resources, Earth wildlife, life-size landmarks, landmark terrain, 9 real black holes | Working — see `docs/ROADMAP_END_GOAL.md` |
| Phases 3–6 (momentum flight & orbits in space, asteroids, exoplanet surfaces, black-hole effects on entities) | Not built yet |

## Playing it

1. Install Fabric Loader for Minecraft 26.3 and put **Fabric API** and the **Cosmos** jar in `.minecraft/mods`.
2. **Create a new world** → *More* / *World Type* → **Cosmos Universe (real Earth)**. The overworld is then the real
   Earth (NOAA ETOPO 2022 elevation): you spawn on it and can build, mine and play survival normally. In any other
   world type the planets still exist as their own dimensions; use `/cosmos land earth` to reach Earth.
3. Fly straight up (creative flight, elytra, or very high jumps on small moons) past y ≈ 743 to **launch into space**.
4. In space: WASD / Space / Shift to fly (sprint = ×20). Speed grows with distance from the nearest surface.
   `/cosmos goto <name>` warps to anything (labelled as gameplay, not physics).
5. **Fly down into a planet or moon to land** where you're pointing. `/cosmos land <world> [lat lon]` is the shortcut
   (e.g. `/cosmos land moon` = Apollo 11 site, `/cosmos land mars` = Jezero crater).
6. `/cosmos where` shows latitude, longitude, elevation, gravity and the data source of the ground under you.
   `/cosmos worlds` lists all 26 walkable worlds. `/cosmos landmarks` lists real places; `/cosmos visit stonehenge`
   (or `great_pyramid`, `eiffel_tower`, `apollo_11`, `perseverance`…) lands you next to one. `/cosmos help` lists everything.

Scale: 1 block = 100 m across a planet's surface, relief ×2, you and gravity at 1 block = 1 m (see `docs/SCALE.md`).

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
