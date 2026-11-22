#!/usr/bin/env python3
"""Download real global elevation models and compress them into Cosmos terrain grids.

Outputs (all: lines from 90N southward, samples from 0E eastward, int16 little-endian, gzip):
  earth.dem.gz  ETOPO 2022 (NOAA NCEI) 60-arcsec surface elevation, block-averaged to 0.1 deg   (metres)
  moon.dem.gz   LRO LOLA LDEM_16 (NASA PDS)  16 ppd, averaged to 8 ppd   (metres rel. 1737.4 km sphere)
  mars.dem.gz   MGS MOLA MEGDR meg016 (NASA PDS) 16 ppd, averaged to 8 ppd (metres rel. areoid)
  earth_cover.bin.gz  land-cover class per earth.dem cell from NASA Blue Marble (Aug 2004) true colour
Header (32 bytes): b'CDEM', int32 w, int32 h, float32 metresPerUnit, char[16] source tag.
"""
import gzip, io, os, struct, sys, urllib.request
import numpy as np

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
os.makedirs(OUT, exist_ok=True)
log = open(os.path.join(OUT, "dem_log.txt"), "w")
def say(*a):
    print(*a); print(*a, file=log); log.flush()

def fetch(urls, dest):
    for u in urls:
        try:
            say("GET", u)
            req = urllib.request.Request(u, headers={"User-Agent": "cosmos-mod-build/1.0"})
            with urllib.request.urlopen(req, timeout=600) as r, open(dest, "wb") as f:
                while True:
                    b = r.read(1 << 20)
                    if not b: break
                    f.write(b)
            say("  ok", os.path.getsize(dest), "bytes")
            return u
        except Exception as e:
            say("  failed:", e)
    return None

def block_mean(a, k):
    h, w = a.shape
    return a[: h // k * k, : w // k * k].reshape(h // k, k, w // k, k).astype(np.float64).mean(axis=(1, 3))

def write(name, grid_m, mpu, tag):
    q = np.clip(np.round(grid_m / mpu), -32768, 32767).astype("<i2")
    h, w = q.shape
    hdr = b"CDEM" + struct.pack("<iif", w, h, mpu) + tag.encode()[:16].ljust(16, b"\0")
    with gzip.open(os.path.join(OUT, name), "wb", 9) as f:
        f.write(hdr); f.write(q.tobytes())
    say(f"wrote {name}: {w}x{h} min {grid_m.min():.0f} m max {grid_m.max():.0f} m  ({os.path.getsize(os.path.join(OUT, name))} bytes)")

tmp = "/tmp/dem"; os.makedirs(tmp, exist_ok=True)

# ---------------- Moon: LOLA LDEM_16 (LSB int16, 0.5 m/DN, rel 1737.4 km), 5760x2880, lon 0..360 E
src = fetch(["https://pds-geosciences.wustl.edu/lro/lro-l-lola-3-rdr-v1/lrolol_1xxx/data/lola_gdr/cylindrical/img/ldem_16.img"], tmp + "/moon.img")
if src:
    a = np.fromfile(tmp + "/moon.img", dtype="<i2").reshape(2880, 5760).astype(np.float64) * 0.5
    write("moon.dem.gz", block_mean(a, 2), 1.0, "LOLA LDEM_16")

# ---------------- Mars: MOLA MEGDR 16 ppd topography (MSB int16 m rel areoid), 5760x2880, lon 0..360 E
src = fetch(["https://pds-geosciences.wustl.edu/mgs/mgs-m-mola-5-megdr-l3-v1/mgsl_300x/meg016/megt90n000eb.img"], tmp + "/mars.img")
if src:
    a = np.fromfile(tmp + "/mars.img", dtype=">i2").reshape(2880, 5760).astype(np.float64)
    write("mars.dem.gz", block_mean(a, 2), 1.0, "MOLA MEGDR 16")

# ---------------- Earth: ETOPO 2022 60s surface (netCDF, lat south->north, lon -180..180)
earth = None
src = fetch(["https://www.ngdc.noaa.gov/thredds/fileServer/global/ETOPO2022/60s/60s_surface_elev_netcdf/ETOPO_2022_v1_60s_N90W180_surface.nc"], tmp + "/etopo.nc")
if src:
    try:
        import netCDF4
        ds = netCDF4.Dataset(tmp + "/etopo.nc")
        z = np.asarray(ds.variables["z"][:], dtype=np.float32)
        say("etopo shape", z.shape)
        z = block_mean(z, 6)[::-1, :]          # 0.1 deg, north first
        z = np.roll(z, -z.shape[1] // 2, axis=1)  # start at 0E
        earth = z
        write("earth.dem.gz", z, 1.0, "ETOPO 2022 60s")
    except Exception as e:
        say("etopo parse failed:", e)

# ---------------- Earth land cover from Blue Marble true colour (5400x2700, lon -180..180)
src = fetch(["https://eoimages.gsfc.nasa.gov/images/imagerecords/74000/74042/world.200408.3x5400x2700.jpg",
             "https://eoimages.gsfc.nasa.gov/images/imagerecords/73000/73751/world.topo.bathy.200407.3x5400x2700.jpg"], tmp + "/bm.jpg")
if src and earth is not None:
    from PIL import Image
    im = Image.open(tmp + "/bm.jpg").convert("RGB").resize((earth.shape[1], earth.shape[0]), Image.BILINEAR)
    c = np.asarray(im).astype(np.float64)
    c = np.roll(c, -c.shape[1] // 2, axis=1)
    r, g, b = c[..., 0], c[..., 1], c[..., 2]
    lum = (r + g + b) / 3
    # classes: 0 ocean, 1 ice/snow, 2 desert/sand, 3 dry grass/savanna, 4 grassland, 5 forest, 6 dense tropical forest, 7 tundra/bare rock
    cls = np.zeros(earth.shape, np.uint8)
    land = earth > 0
    lat = 90 - (np.arange(earth.shape[0]) + 0.5) * 180 / earth.shape[0]
    LAT = np.repeat(lat[:, None], earth.shape[1], axis=1)
    greenness = g - (r + b) / 2
    cls[land] = 4
    cls[land & (greenness > 4) & (lum < 70)] = 5
    cls[land & (greenness > 4) & (lum < 55) & (np.abs(LAT) < 23)] = 6
    cls[land & (r > g + 8) & (lum > 95)] = 2
    cls[land & (r > g) & (lum > 70) & (lum <= 95) & (greenness < 0)] = 3
    cls[land & (lum < 85) & (np.abs(LAT) > 55) & (greenness < 4)] = 7
    cls[(lum > 185) & ((np.abs(LAT) > 55) | (earth > 3500))] = 1
    cls[~land & (lum > 170) & (np.abs(LAT) > 60)] = 1  # sea ice / ice shelves
    with gzip.open(os.path.join(OUT, "earth_cover.bin.gz"), "wb", 9) as f:
        f.write(struct.pack("<ii", cls.shape[1], cls.shape[0])); f.write(cls.tobytes())
    say("land cover histogram", np.bincount(cls.ravel(), minlength=8).tolist(), "source", src)
    Image.fromarray((cls * 32).astype(np.uint8)).resize((900, 450)).save(os.path.join(OUT, "earth_cover_preview.png"))
say("done")
