#!/usr/bin/env python3
"""Generates fabric/src/main/resources/data/** for every walkable body: dimension types (sky/fog per atmosphere),
dimensions (cosmos:planet generator), custom surface biomes, the 'Cosmos Universe' world preset, lang strings."""
import json, os, shutil
ROOT = os.path.join(os.path.dirname(__file__), "..", "fabric", "src", "main", "resources")
D = os.path.join(ROOT, "data")
MIN_Y, HEIGHT = -256, 1024

# body: (display name, sky, fog, cloud colour or None, cloud height, ambient)
VAC = ("#000000", "#05060a", None)
BODIES = {
    "earth": ("Earth", "#78a7ff", "#c0d8ff", "#ccffffff", 160.33),
    "moon": ("Moon",) + VAC + (0,), "mercury": ("Mercury",) + VAC + (0,), "phobos": ("Phobos",) + VAC + (0,), "deimos": ("Deimos",) + VAC + (0,),
    "mars": ("Mars", "#c9a27e", "#b98a62", "#33f0d8c0", 300.33),
    "venus": ("Venus", "#d9b064", "#c89a4a", "#ffe8c070", 520.33),
    "io": ("Io",) + VAC + (0,), "europa": ("Europa",) + VAC + (0,), "ganymede": ("Ganymede",) + VAC + (0,), "callisto": ("Callisto",) + VAC + (0,),
    "mimas": ("Mimas",) + VAC + (0,), "enceladus": ("Enceladus",) + VAC + (0,), "tethys": ("Tethys",) + VAC + (0,), "dione": ("Dione",) + VAC + (0,),
    "rhea": ("Rhea",) + VAC + (0,), "titan": ("Titan", "#c48a3a", "#a8742c", "#66d0a060", 400.33), "iapetus": ("Iapetus",) + VAC + (0,),
    "miranda": ("Miranda",) + VAC + (0,), "ariel": ("Ariel",) + VAC + (0,), "umbriel": ("Umbriel",) + VAC + (0,), "titania": ("Titania",) + VAC + (0,),
    "oberon": ("Oberon",) + VAC + (0,), "triton": ("Triton",) + VAC + (0,), "pluto": ("Pluto",) + VAC + (0,), "charon": ("Charon",) + VAC + (0,),
}
# custom surface biomes (Earth uses vanilla biomes): id -> water colour
BIOMES = ["lunar_highland", "lunar_mare", "lunar_ejecta", "lunar_polar_ice", "mars_dust", "mars_dark_sand", "mars_rock", "mars_volcanic",
          "mars_layered", "mars_polar_ice", "grey_regolith", "dark_regolith", "basalt_plains", "sulfur_plains", "volcanic_vent", "clean_ice",
          "dirty_ice", "dark_ice", "nitrogen_ice", "tholin", "organic_dunes", "organic_shore"]

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f: json.dump(obj, f, indent=2)

for sub in ["cosmos/dimension", "cosmos/dimension_type", "cosmos/worldgen/biome", "cosmos/worldgen/world_preset", "minecraft/tags/worldgen/world_preset"]:
    shutil.rmtree(os.path.join(D, sub), ignore_errors=True)

def dim_type(body, name, sky, fog, cloud, cloud_h):
    attrs = {
        "minecraft:gameplay/bed_rule": {"can_set_spawn": "always", "can_sleep": "when_dark", "error_message": {"translate": "block.minecraft.bed.no_sleep"}},
        "minecraft:gameplay/respawn_anchor_works": False,
        "minecraft:visual/sky_color": sky, "minecraft:visual/fog_color": fog,
        "minecraft:visual/ambient_light_color": "#0a0a0a",
    }
    if body == "earth":
        attrs["minecraft:audio/background_music"] = {"default": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.game"},
                                                     "creative": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.creative"}}
        attrs["minecraft:gameplay/nether_portal_spawns_piglin"] = True
    if cloud: attrs["minecraft:visual/cloud_color"] = cloud; attrs["minecraft:visual/cloud_height"] = cloud_h
    else: attrs["minecraft:visual/cloud_color"] = "#00ffffff"
    return {"ambient_light": 0.0, "attributes": attrs, "coordinate_scale": 1.0, "default_clock": "minecraft:overworld",
            "has_ceiling": False, "has_ender_dragon_fight": False, "has_skylight": True, "height": HEIGHT,
            "infiniburn": "#minecraft:infiniburn_overworld", "logical_height": HEIGHT, "min_y": MIN_Y,
            "monster_spawn_block_light_limit": 0, "monster_spawn_light_level": {"type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 0},
            "timelines": "#minecraft:in_overworld"}

def generator(body):
    return {"type": "cosmos:planet", "body": body, "biome_source": {"type": "cosmos:planet", "body": body}}

for body, (name, sky, fog, cloud, ch) in BODIES.items():
    write(f"{D}/cosmos/dimension_type/{body}.json", dim_type(body, name, sky, fog, cloud, ch))
    write(f"{D}/cosmos/dimension/{body}.json", {"type": f"cosmos:{body}", "generator": generator(body)})

for b in BIOMES:
    water = "#3a2a14" if b.startswith("organic") else "#3f76e4"
    write(f"{D}/cosmos/worldgen/biome/{b}.json", {"carvers": [], "downfall": 0.0, "effects": {"water_color": water},
        "features": [[] for _ in range(11)], "has_precipitation": False, "temperature": 0.5})

write(f"{D}/cosmos/worldgen/world_preset/universe.json", {"dimensions": {
    "minecraft:overworld": {"type": "cosmos:earth", "generator": generator("earth")},
    "minecraft:the_nether": {"type": "minecraft:the_nether", "generator": {"type": "minecraft:noise", "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:nether"}, "settings": "minecraft:nether"}},
    "minecraft:the_end": {"type": "minecraft:the_end", "generator": {"type": "minecraft:noise", "biome_source": {"type": "minecraft:the_end"}, "settings": "minecraft:end"}}}})
write(f"{D}/minecraft/tags/worldgen/world_preset/normal.json", {"replace": False, "values": ["cosmos:universe"]})

lang = {"generator.cosmos.universe": "Cosmos Universe (real Earth)"}
for body, v in BODIES.items(): lang[f"dimension.cosmos.{body}"] = v[0]
for b in BIOMES: lang[f"biome.cosmos.{b}"] = b.replace("_", " ").title()
os.makedirs(f"{ROOT}/assets/cosmos/lang", exist_ok=True)
write(f"{ROOT}/assets/cosmos/lang/en_us.json", lang)
print("bodies", len(BODIES), "biomes", len(BIOMES))
