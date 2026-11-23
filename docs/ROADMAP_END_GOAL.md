# End goal: the whole universe, accurately, explorable on foot

> Every planet and moon is a real place you can travel to, land on and explore — caves, life where life exists,
> real landmarks — inside a universe where every star, galaxy and black hole is where astronomers have measured it.

## Where it stands (v0.3)

| Part | Status |
|---|---|
| Real sky: 112k stars, 12k galaxies/nebulae, 5,393 exoplanets, JPL orbits | Done (v0.1) |
| Black holes at their measured positions | 9: Sgr A*, M87*, M31*, Cygnus X-1, V404 Cyg, A0620-00, Gaia BH1/BH2/BH3 |
| Walkable worlds with real gravity | 26 (Earth, Moon, Mars from measured elevation; 23 procedural, labelled) |
| Launch / land / walk round the planet | Done (v0.2) |
| Caves | Earth caves + deep lava; lava tubes on the Moon, Mars, Mercury, Io; ice caves on icy moons (v0.3) |
| Life | Earth: biome-appropriate animals spawn with the terrain (v0.3). Elsewhere: none — no life is known anywhere else |
| Landmarks | 6 Earth monuments life-size, all crewed Moon landings, Mars landers/rovers, Venera 13, Huygens (v0.3) |
| Landmark terrain | Sharper measured elevation around 39 famous places (Grand Canyon, Everest, Fuji, Apollo sites, Olympus Mons…) |

## Next, in order

1. **Phase 3 — real flight.** Replace "speed scales with distance" with a ship that has momentum: thrust, coasting,
   gravity from every body (the N-body `FlightModel` already exists in core), orbits you can actually enter and hold,
   atmospheric drag on the way down, escape velocity. Warp stays as a labelled gameplay option.
2. **Phase 4 — the rest of the Solar System.** Real asteroids and comets from the JPL Small-Body Database (orbits for
   the largest ~1,000, Ceres and Vesta walkable), Saturn's moons in their real orbits around it in space, eclipses
   and real day lengths on each world (replace Minecraft's 20-minute day with the body's actual rotation).
3. **Phase 5 — exoplanet surfaces.** Minecraft can't create dimensions at runtime, so one "exoplanets" dimension will be
   tiled: each walkable exoplanet gets its own region of it (wrap-around inside its tile), generated from its
   catalogue radius, mass, equilibrium temperature and star — TRAPPIST-1e, Proxima b, Kepler-452b… labelled procedural.
4. **Phase 6 — black holes you can feel.** Tidal forces, time dilation readouts, orbiting/infalling objects, accretion
   discs as matter, not just light.
5. **More landmarks and data.** Higher-resolution elevation for more of Earth; more monuments; Mercury (MESSENGER)
   and Venus (Magellan) measured topography; Mars albedo map.

## Things that can't be literally true (and what we do instead)

- **Flat chunk grid**: planets are equirectangular maps that wrap; east–west stretch toward the poles.
- **Scale**: 1:100 horizontally for surfaces; life-size buildings therefore cover 100× too much map.
- **Life elsewhere**: none is known, so other worlds are lifeless. Fictional life could be added only as a clearly
  labelled option.
- **Every star's planets**: only ~5,400 exoplanets are known; the rest of the galaxy's planets are procedural and labelled.
