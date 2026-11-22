# Roadmap and status against the original 13-part spec

Legend: ✅ done and verified · 🟡 partial · ⬜ not started

| # | Requirement | Status | Notes |
|---|---|---|---|
| 1 | Real universe from datasets | 🟡 | Solar System (JPL/IAU), 112k stars (HYG), 5.4k exoplanets (OEC), 12k DSOs (OpenNGC), 4 black holes. Missing: Gaia DR3 depth (D2), NASA Exoplanet Archive (D1), SIMBAD cross-ids, galaxy-cluster catalogues (D3) |
| 2 | True scale, high-precision coordinates | ✅ | UniversePos, floating origin, scaled-space projection, all tested |
| 3 | Physics and orbits | 🟡 | Ephemeris-grade planets and Moon, IAU rotation/tilt, tidal locking, eccentric/inclined orbits, N-body gravity, drag, time warp. Moon accuracies per ACCURACY.md. Missing: Chebyshev moon tables (C3), J2 oblateness gravity |
| 4 | Black holes | 🟡 | Schwarzschild geodesic lensing, Einstein ring, background distortion, Kerr disc kinematics, Doppler + gravitational redshift, beaming, real parameters. Missing: full Kerr ray paths (frame dragging of light) |
| 5 | Planetary exploration / landing | ⬜ | Needs Minecraft. Design in ARCHITECTURE.md |
| 6 | Visuals | 🟡 | HDR, bloom, ACES, Rayleigh/Mie/ozone scattering, limb glow, star colours, accretion discs, lensing, volumetric galaxy, clouds, night lights, ocean glint. Missing: rings in shader, aurora, nebula volumes, gas-giant bands, dynamic shadows inside MC |
| 7 | Whole universe explorable | 🟡 | Engine supports any location, with deterministic procedural stars and planets. Missing: in-game travel (needs mod), cosmic-web generator beyond catalogued galaxies (G2) |
| 8 | Spacecraft and travel | 🟡 | Flight model and relativistic travel planner done; ship, cockpit, controls need Minecraft |
| 9 | Seamless scale transitions | 🟡 | Render transform and LOD design done; in-game implementation pending |
| 10 | UI | 🟡 | Search backend done (Sirius, Proxima, Sgr A*, Andromeda, TRAPPIST-1 …), info facts with provenance. Screens need Minecraft |
| 11 | Performance | 🟡 | SoA catalogues, spatial grid, deterministic cells, GPU instancing. Profiling in-game pending |
| 12 | Engineering quality | ✅ | Separate modules, 128 automated checks, headless shader verification |
| 13 | Final vision | ⬜ | Requires the Fabric layer |

## Next steps, in order

**F — Fabric layer (needs your computer, because the sandbox cannot reach Mojang/Fabric servers)**
1. F1: Inspect your Saurus Client environment (MC 1.21.11, Loom, Java), create `fabric/` with `core` as a subproject.
2. F2: Space dimension + sky replacement: diffuse sky + instanced stars, then the planet pass. Verify in game.
3. F3: Ship entity + FlightModel + HUD; time warp; map screen with search.
4. F4: Black hole and galaxy passes; LOD switching.
5. F5: Planet surface dimensions (Moon first: LOLA DEM, airless, simplest), then Mars (MOLA), then Earth.

**D — Data**
- D1: NASA Exoplanet Archive TAP download (run on your machine), replacing the 2023 OEC snapshot.
- D2: Gaia DR3 subset (e.g. G < 13 with good parallaxes, ~10 M stars) as streamed tiles.
- D3: galaxy groups/clusters (e.g. 2MRS / Cosmicflows-4 distances) for the local cosmic web.

**C — Accuracy**
- C3: Chebyshev position tables for major moons generated from Horizons (fixes Mimas/Titan/Phobos residuals).

**G — Generation**
- G2: deterministic cosmic web (filaments/voids) beyond catalogue depth, constrained to match catalogued clusters.

**P — Presentation**
- P2: NASA texture/DEM downloader + tile cache; Saturn ring rendering in `planet.fsh`; aurora; nebula volumes.
