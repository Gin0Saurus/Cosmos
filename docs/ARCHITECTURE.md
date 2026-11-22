# Architecture

## Principles

1. **One universe, one coordinate system.** Every object has a true `UniversePos`; nothing is ever moved or
   rescaled to make exploration easier. Only the *render transform* changes with distance.
2. **The engine knows nothing about Minecraft.** `core/` is plain Java 21 with no dependencies, so it can be tested
   exhaustively outside the game and reused by tools.
3. **Provenance everywhere.** Every value carries Measured / Estimate / Procedural, and the UI shows it.
4. **Deterministic procedural fill.** Content is a pure function of (universe seed, location); revisiting a place
   always yields the same objects. Procedural generation is suppressed wherever real catalogues are authoritative.

## Modules

```
core/
  coords/   UniversePos (64-bit sector + double offset), Frames (ecliptic, ICRS, galactic), FloatingOrigin, Units
  time/     SimTime (TDB seconds since J2000, UTC conversion, time warp)
  orbit/    Kepler (elliptic + hyperbolic), JplPlanetOrbit, MeanElementOrbit (+ Horizons calibration), LunarTheory
  body/     Body, RotationModel (IAU), SolarSystem, Provenance
  catalog/  StarCatalog (SoA + spatial grid, proper motion), StarColor, Designations, ExoplanetCatalog, StarSystem, DeepSkyCatalog
  galaxy/   MilkyWayModel, ProceduralStars, ProceduralPlanets
  blackhole/ BlackHole (Kerr horizon, ISCO, photon sphere), Geodesics (CPU reference tracer)
  physics/  FlightModel (N-body gravity, thrust, drag, entry heating)
  nav/      ObjectIndex (search), TravelPlanner (relativistic + labelled warp)
  Universe  facade that loads everything
shaders/    GLSL 3.30 core: stars, sky_hdr, planet, blackhole, galaxy, composite, common
preview/    SkyMap, SceneState (Java) + harness.py (runs the shaders headless) + shot scripts
tools/      compile_data.py
```

## Coordinates and precision

- `UniversePos` = three `long` sector indices plus three `double` offsets in [0, 2³²) m. The sector is 2³² m
  (4.3 million km). Resolution inside a sector is ≤ 1 µm; range ±4×10²⁸ m, about 90 times the radius of the
  observable universe. There are no boundaries.
- Differences are computed in integer sector space first, so relative vectors are exact anywhere (tested: an
  Earth-radius separation 10 Gly from the origin is recovered to the millimetre).
- Hierarchy: bodies store orbits relative to their parent (moon → planet → star), so precision is always local.
  Spacecraft integrate in system-centred doubles (mm precision to ~60 AU) and hand off to interstellar mode outside.
- **Floating origin + scaled space:** each frame the camera's `UniversePos` becomes the render origin. Objects
  within 50 km are drawn 1:1. Further objects are pulled in along their exact line of sight into a shell and scaled
  by the same factor, so direction and angular size are mathematically unchanged and floats never see huge numbers.

## Rendering pipeline (planned Fabric integration, shaders already working)

Per frame, outermost first, into an RGBA16F HDR target:

1. **Diffuse sky**: an equirect/cube map of unresolved starlight, integrated through the galaxy model with dust
   (`SkyMap`). Regenerated asynchronously when the observer moves far enough for parallax to matter.
2. **Galaxy volume** (`galaxy.fsh`): only when outside or high above the disc; ray-marches the same density model.
3. **Resolved stars** (`stars.vsh/fsh`): instanced point sprites from the catalogue plus procedural cells near the
   camera, magnitudes recomputed from the observer's position. Per-sector GPU buffers, frustum culled.
4. **Planets and moons** (`planet.fsh`) in scaled space: surface texture or albedo, single-scattering atmosphere,
   clouds and shadows, night lights, rings.
5. **Black holes** (`blackhole.fsh`) as a screen-space pass over everything behind them: geodesic ray tracing with
   the scene's environment map as background, plus the accretion disc.
6. **Minecraft world** (terrain, entities, the player's ship) drawn normally on top. Depth is handled by drawing the
   astronomical layers first with depth writes off.
7. **Composite**: bloom from the HDR mip chain, ACES tone curve, exposure adaptation.

The shaders are GLSL 3.30 core. The exact Minecraft 1.21.11 hook points (sky pass replacement, render pipeline /
framebuffer API, which changed in 1.21.5+) will be confirmed against the real mappings on first compile.

## Minecraft integration design (not yet implemented)

- **Space dimension** `cosmos:space`: empty world; the player's ship is a block structure attached to a moving
  frame. The ship's `UniversePos` is the true position. Minecraft coordinates are just the local ship frame.
- **Planet surfaces**: one dimension per landable body, mapped latitude/longitude → blocks at 1 block = 1 m.
  Earth's equator (40,075 km) fits inside Minecraft's ±30 million block world border. Terrain comes from real
  elevation data where available (Earth: SRTM/GMTED; Moon: LOLA; Mars: MOLA), streamed as tiles and detailed
  procedurally below the data's resolution (flagged P). Bodies without data use procedural terrain seeded from the
  body id. Gas giants have no surface: descent ends at a pressure limit.
- **Transitions**: orbit → atmosphere → surface happens at a fixed altitude (~100 km). The world loads in the
  background during descent while the scaled-space planet keeps rendering, so there is no loading screen in the
  normal case.
- **Spacecraft**: a ship entity carrying `FlightModel` state, simulated on the server with client prediction.
  Controls: throttle, RCS rotation, SAS, time warp (physics time warp limited by integration step), and a labelled
  "warp drive" for interstellar distances.
- **UI**: star map, galaxy map and system map built on `ObjectIndex`, `Frames` and `TravelPlanner`. The info panel
  shows `Body.facts()` with provenance badges.

## Performance strategy

Never load the universe at once. Catalogues are compact SoA arrays (~110k stars in a few MB). Procedural cells are
generated on worker threads only near the camera, with an LRU cache. Distant structure is drawn volumetrically
instead of star by star. Planet terrain is generated only for the dimension the player is in. GPU work uses
instancing and screen-space passes. Graphics presets scale step counts (geodesics, atmosphere samples, galaxy steps).
