# Measured accuracy

All numbers below come from the automated test suite (`./build.sh`). Reference values are JPL Horizons state vectors
(DE441 and the JPL satellite ephemerides), ICRF, fetched for this project.

## Planets (heliocentric, 2026-11-22 TDB, JPL approximate elements 1800–2050)

| Body | Direction error | Distance error |
|---|---|---|
| Earth | 0.08′ | 0.002% |
| Mars | 0.18′ | 0.005% |
| Jupiter | 1.48′ | 0.04% |
| Neptune | 0.52′ | 0.001% |

## Moon (ELP-2000/82 main terms, no fitting)

| Epoch | Direction error | Distance error |
|---|---|---|
| 2000-01-01 | 0.13′ | 3.8 km |
| 2013-06-01 | 0.04′ | 1.5 km |
| 2026-11-22 | 0.02′ | 6.7 km |

## Planetary satellites

JPL mean elements, with phase and mean motion calibrated to Horizons at 2000 and 2026, checked at an **independent**
epoch (2013-06-01) that is not used in the fit:

| Moon | Error | Moon | Error |
|---|---|---|---|
| Io | 3.2′ | Ariel | 4.1′ |
| Europa | 22′ (dist 1.2%) | Umbriel | 4.2′ |
| Ganymede | 3.4′ | Titania | 9.0′ |
| Callisto | 3.2′ | Oberon | 16.7′ |
| Deimos | 17′ | Miranda | 4.9′ |
| Tethys | 44′ | Charon | 1.4′ |
| Dione | 16′ | Rhea | 40′ |
| Phobos | 2.2° | Iapetus | 2.0° |
| Titan | 2.9° | Triton | 3.5° |
| Mimas | 18.8° | Enceladus | uncalibrated (Horizons fetch failed) |

The large ones are real dynamics that mean elements cannot represent: Mimas's ±43° libration from the Mimas–Tethys
resonance, Phobos's tidal acceleration and fast apsidal precession. Fix path: Chebyshev tables generated from
Horizons (ROADMAP item C3).

## Other checks

- Earth perihelion 2026-01-03: 0.983274 AU; aphelion 2026-07-06: 1.016692 AU.
- Earth sidereal day from the IAU model: 23.934471 h. Earth obliquity from the IAU pole: 23.4393°. Uranus obliquity: 97.77°.
- Moon, Io and Titan tidally locked (IAU rotation = mean motion); Venus retrograde.
- Coordinates: a 1 mm offset is preserved exactly at 2.5 Mly and at 10 Gly; resolution ≤ 1 µm inside a 4.3 million km sector.
- Scaled-space rendering preserves the Sun's angular size to 2×10⁻⁸.
- Black hole: weak-field deflection 4M/b reproduced to 0.3%; capture boundary between b = 5.19 M and 5.21 M
  (theory 3√3 M = 5.196 M); rendered shadow at 30 M is ~103 px vs 98 px predicted.
- 500 km circular orbit in the full N-body field of the Solar System: altitude 499.99 km after 5 orbits.
- Relativistic 1 g brachistochrone to Proxima Centauri: 5.85 yr Earth time, 3.53 yr ship time.
