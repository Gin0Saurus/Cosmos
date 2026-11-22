# Cosmos scale system

One scale is used for every walkable world, so relative sizes are always exact.

## Surface (walking) scale

| Quantity | Scale | Why |
|---|---|---|
| Horizontal distance on a planet's surface | **1 block = 100 m** | Earth becomes 400,320 blocks around: you *can* walk or fly all the way round, and Minecraft's ±30 M block limit fits even Jupiter-sized worlds. |
| Vertical relief (mountains, craters, ocean depth) | **1 block = 50 m** (2× vertical exaggeration, same on every body) | At a uniform 1:100, Everest would be 88 blocks and most hills under a block. 2× keeps real landforms recognisable while staying inside the build height. |
| Local physics (you, mobs, blocks, items) | **1 block = 1 m** | You are 1.8 blocks tall, and gravity is the body's real surface gravity in m/s² applied per block. |
| Sea level / reference surface | y = 63 | Earth: sea level. Moon: mean radius 1737.4 km. Mars: MOLA areoid. |
| World height on every body | y = −256 … 767 | Olympus Mons summit ≈ y 487; Mariana Trench floor ≈ y −157. |
| Top of the atmosphere column | y ≈ 743 (`LAUNCH_Y`) | Flying above this launches you into space mode. |

Resulting sizes (blocks around the equator, `PlanetProjection.forRadius`):

| Body | Mean radius | Circumference (blocks) |
|---|---|---|
| Earth | 6,371 km | 400,320 |
| Mars | 3,389.5 km | 212,992 |
| Moon | 1,737.4 km | 109,184 |
| Pluto | 1,188 km | ~74,600 |
| Phobos | 11.1 km | 704 |

### Real-world heights in blocks

| Feature | Real | Blocks above/below datum |
|---|---|---|
| Everest | 8,849 m | +177 (data at 1/6° gives the Himalaya ~6.7 km; procedural ridges add peaks) |
| Mariana Trench | −10,994 m | −220 (the 1/6° grid averages it to ~−9.7 km, −195) |
| Olympus Mons | +21.2 km (MOLA) | +424 |
| Hellas floor | −8.2 km | −164 |
| Valles Marineris | ~7 km deep | ~140 below its rim |
| Moon, South Pole–Aitken | ~−8.6 km | −172 |

## Gravity

`g = GM / R²` from each body's measured GM and mean radius. Minecraft's per-tick gravity for **every** entity (player, mobs, items, arrows, falling sand) is multiplied by `g / 9.80665`. Fall damage scales with impact energy (`m g h`), so a fall of h blocks on the Moon hurts like h × 0.165 blocks on Earth.

| Body | g (m/s²) | × Earth |
|---|---|---|
| Earth | 9.80 | 1.00 |
| Mars | 3.72 | 0.38 |
| Moon | 1.62 | 0.165 |
| Pluto | 0.62 | 0.063 |
| Phobos | 0.0057 | 0.0006 |

## Space scale

In space there is no scaling at all: positions are true metres (hierarchical 64-bit sectors + double offsets), planets are their real sizes at their real JPL positions. Travel speed scales with distance to the nearest surface, and anything faster than light is labelled "warp — gameplay, not physics".

## Time

`/cosmos time <x>` warps the orbital/rotation simulation in space. On planetary surfaces the day/night cycle currently still follows Minecraft's 20-minute day (see limitations).
