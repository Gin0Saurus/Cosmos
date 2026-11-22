# Data sources

Every compiled resource keeps a provenance tag per value: **M** measured/catalogued, **E** estimate derived by a stated
relation, **P** procedural, **U** unknown (direction only). The info panel shows these tags to the player.

| Data | Source | Notes |
|---|---|---|
| Planet orbits | JPL SSD, *Keplerian Elements for Approximate Positions of the Major Planets*, Table 1 (1800–2050) | Pluto row from the earlier edition of the table (copy in github.com/mayakraft/Kepler; its other rows match JPL exactly) |
| Satellite orbits | JPL SSD, *Planetary Satellite Mean Elements* (epoch 2000-01-01.5 TDB) | Calibrated to Horizons (see ACCURACY.md) |
| Moon | ELP-2000/82, main terms as truncated in Meeus, *Astronomical Algorithms* 2nd ed., ch. 47 | |
| Reference ephemeris (tests, calibration) | JPL Horizons API (DE441, jup365, sat441l, ura182/184, nep098 …) | |
| Rotation, poles, radii | NAIF `pck00011.tpc` = IAU WGCCRE 2015 (Archinal et al. 2018) | Nutation/precession periodic terms not applied |
| Planet masses | JPL SSD *Planetary Physical Parameters* | |
| Satellite GM | JPL SSD *Planetary Satellite Physical Parameters* | |
| Obliquity, surface pressure, mean temperature | NASA NSSDCA Planetary Fact Sheet | |
| Saturn's rings | NASA NSSDCA Saturnian Rings Fact Sheet | optical depth / albedo use midpoints of the given ranges (E) |
| Stars (112,470 incl. 3,070 added exoplanet hosts) | HYG Database v4.1 (Hipparcos + Yale BSC + Gliese), CC BY-SA 4.0, github.com/astronexus/HYG-Database | 10,225 HYG rows without a valid parallax are excluded |
| Exoplanets (5,393) | Open Exoplanet Catalogue tables, github.com/OpenExoplanetCatalogue/oec_tables (snapshot of Oct 2023) | NASA Exoplanet Archive was not reachable from the build sandbox; switching to it is ROADMAP D1 |
| Deep-sky objects (12,101) | OpenNGC (NGC + IC), CC BY-SA 4.0, github.com/mattiaverga/OpenNGC | LMC position = centroid of the OpenNGC LMC outline |
| Galaxy distances | Measured where listed below, else Hubble law v/H0 (H0 = 70) for v > 1500 km/s (E), else size-based guess (P) | |
| M31 | Li et al. 2021, ApJ 920, 84 — 761 ± 11 kpc | |
| M33 | Brunthaler et al. 2005, Science 307 — 730 ± 168 kpc | |
| LMC / SMC | Pietrzyński et al. 2019 (49.59 kpc) / Graczyk et al. 2020 (62.43 kpc) | |
| M87 / M87* | EHT Collaboration 2019, Paper VI — 16.8 Mpc, 6.5×10⁹ M☉ | |
| Orion Nebula | Kounkel et al. 2017 — 388 ± 5 pc | |
| Sgr A* | GRAVITY Collaboration 2022 — 4.297×10⁶ M☉, R0 = 8277 pc | spin 0.9 is a model-dependent estimate (E) |
| Cygnus X-1 | Miller-Jones et al. 2021 — 21.2 M☉, 2.22 kpc | overrides Hipparcos distance |
| Gaia BH3 | Gaia Collaboration 2024, A&A 686, L2 — 32.70 M☉, 590 pc | |
| Galaxy structure | Bland-Hawthorn & Gerhard 2016 (disc scales, bar angle); Bennett & Bovy 2019 (Sun's height) | arm geometry is P |
| Star colours | Ballesteros 2012 (B−V → Teff); CIE 1931 CMF fit of Wyman, Sloan & Shirley 2013 | |
| Exoplanet radius/mass gaps | Chen & Kipping 2017 mass–radius relation (E) | |
| IMF for procedural stars | Kroupa 2001 | |
| Atmosphere scattering coefficients | Bruneton & Neyret 2008 / Hillaire 2020 Earth parameter set (E); Mars, Venus, Titan values are tuned approximations (E) | |

**Preview-only assets (not shipped):** the Earth and Moon textures used for `previews/planet_*` come from the three.js
example textures (derived from NASA Blue Marble / LRO imagery). The mod will download NASA originals instead
(ROADMAP P2).
