# Cosmos v0.2 architecture: walkable worlds

## What changed from v0.1

v0.1 was a client-only visualiser: the player was frozen in the overworld while a renderer drew space. That could
never support walking on planets, because terrain, gravity and dimensions are server-side concepts. v0.2 splits the mod:

```
core (pure Java, no Minecraft)          fabric main (server + client)            fabric client
--------------------------------        ------------------------------           -------------------------
coords/  UniversePos, Frames            CosmosMod (init, codecs, networking)     CosmosClient, SurfaceLink
orbit/   JPL elements, ELP Moon         world/Planets (body <-> dimension, g)    render/ (stars, space, BH)
body/    Body, RotationModel            world/CosmosChunkGenerator               mixin/SkyRendererMixin
terrain/ PlanetProjection, SurfaceScale world/CosmosBiomeSource                  mixin/GameRendererMixin
         ElevationGrid (real DEMs)      world/ColumnSampler, SurfacePalette
         PlanetTerrain + Earth/Moon/    world/PlanetTravel (wrap, launch, land)
         Mars/Procedural terrains       mixin/EntityGravityMixin, LivingEntityFallMixin
         Craters, Noise                 net/CosmosPayloads
```

Mapping to the requested concepts:

| Concept | Implementation |
|---|---|
| CelestialBody / Star / Planet / Moon | `core.body.Body` (+ `Kind`), `SolarSystem`, `StarCatalog`, `ExoplanetCatalog` |
| BlackHole / Galaxy | `core.blackhole.BlackHole`, `Geodesics`; `core.galaxy.MilkyWayModel`, `DeepSkyCatalog` |
| Orbit | `core.orbit.*` (JPL Keplerian elements, satellite mean elements, ELP-2000/82) |
| GravityField | `Planets.surfaceGravity` (surface), `core.physics.FlightModel` (N-body in space) |
| Atmosphere | `Body.Atmosphere` (scattering, used by the space renderer); per-dimension sky/fog/clouds in `dimension_type` |
| PlanetTerrain / PlanetGenerator | `core.terrain.PlanetTerrain` (+ `EarthTerrain`, `MoonTerrain`, `MarsTerrain`, `ProceduralTerrain`), `CosmosChunkGenerator` |
| CoordinateTransform | `core.terrain.PlanetProjection` (block <-> lat/lon), `RotationModel.orientation` (body-fixed <-> ICRS) |
| SpaceRegion / UniverseDatabase | `core.Universe`, `nav.ObjectIndex` |

## LOD chain

distant point of light (star renderer) → shaded sphere with atmosphere and rings (space renderer) → entry altitude
(4% of radius, 5–100 km) → surface dimension: chunks generated on demand from the same body's terrain model.
Nothing is pre-generated or held for the whole planet: a chunk costs ~1–2 ms of terrain sampling (measured in
`TerrainTests`), and only chunks near players exist.

## Real data

| Body | Source | Bundled resolution |
|---|---|---|
| Earth | NOAA NCEI ETOPO 2022, 60″ surface elevation | 1/6° (18.5 km), 2.0 MB |
| Moon | NASA LRO LOLA LDEM_16 | 1/4° (7.6 km), 1.2 MB |
| Mars | NASA MGS MOLA MEGDR meg016 | 1/4° (14.8 km), 0.9 MB |

Fetched and packed by `tools/fetch_dem.py` + `tools/pack_dem.py` (run in CI, which has internet access).
Detail below the grid resolution is procedural and labelled as such (`/cosmos where` prints the provenance).

## Engine limitations (honest list)

1. **Minecraft worlds are flat grids.** Each planet surface is an equirectangular map: X = longitude (wraps around), Z = latitude.
   East–west distances are stretched by 1/cos(latitude) (×1.4 at 45°, ×2 at 60°); polar terrain is the most distorted.
   Walking past ±180° moves you onto identical terrain on the other side, and walking over a pole puts you on the far side,
   turned round. Blocks you place near the seam don't appear on the twin copy of the terrain across it.
2. **No visible curvature on the ground.** At 1:100, Earth's horizon drop over a 512-block view is ~1.3 blocks, which is
   physically correct and invisible. Curvature shows from space.
3. **The planet is a shell.** Below ~96 blocks under the surface there is bedrock; the core is not simulated.
4. **Entering a world from space switches dimension**, so Minecraft shows a short "loading terrain" screen. The descent
   continues from 60 blocks above the ground with fall damage suppressed until touchdown.
5. **Day/night on surfaces still follows Minecraft's 20-minute day** and vanilla's sun/moon sprites (e.g. the Moon's
   sky shows vanilla's Moon, not Earth). The star field is the real catalogue, oriented for your latitude, and stays
   visible in daylight on airless worlds.
6. **Gas giants have no surface**: you can't fly below the 1-bar level in space; there is no walkable dimension for them.
7. **Titan's methane/ethane lakes are Minecraft water with a dark tint**; there is no custom fluid yet.
8. **No caves on Cosmos worlds yet**; ores, trees, grass, animals and other decoration come from vanilla biomes on Earth.
9. Phases 3–6 (orbital flight with momentum, asteroids as objects, visiting exoplanet surfaces, black-hole physics on
   entities) are not built yet.
