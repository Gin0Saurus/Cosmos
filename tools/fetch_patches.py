#!/usr/bin/env python3
"""High-resolution elevation patches around real landmarks, cut from the full-resolution datasets:
Earth ETOPO 2022 60" (1.85 km), Moon LOLA LDEM_16 (1.9 km), Mars MOLA MEGDR 16 ppd (3.7 km).
Output: <body>_patches.bin.gz = int32 count, then per patch: name[32], float32 lat0 lat1 lon0 lon1 (deg; lat0 north edge),
int32 w h, float32 metresPerUnit, int16[w*h] (north row first, west sample first)."""
import gzip, os, struct, sys, urllib.request
import numpy as np
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
TMP = "/tmp/patch"; os.makedirs(TMP, exist_ok=True)

# name, lat, lon(E), half-height deg, half-width deg
EARTH = [("Grand Canyon", 36.25, -112.6, 0.8, 1.6), ("Mount Everest", 27.99, 86.93, 1.0, 1.2), ("Mount Fuji", 35.36, 138.73, 0.6, 0.7),
         ("Kilimanjaro", -3.07, 37.35, 0.6, 0.6), ("Matterhorn and Mont Blanc", 46.0, 7.3, 0.8, 1.2), ("Yosemite Valley", 37.75, -119.55, 0.5, 0.6),
         ("Dead Sea", 31.5, 35.45, 0.7, 0.5), ("Aconcagua", -32.65, -70.0, 0.6, 0.7), ("Denali", 63.07, -151.0, 0.6, 1.2),
         ("Table Mountain", -34.0, 18.45, 0.35, 0.4), ("Mount Kilauea and Mauna Loa", 19.5, -155.5, 0.8, 0.9), ("Uluru", -25.34, 131.04, 0.3, 0.35),
         ("Iceland", 64.9, -18.6, 2.6, 6.0), ("Hawaii Big Island", 19.6, -155.5, 1.0, 1.2), ("Great Rift Valley Ngorongoro", -3.2, 35.6, 0.5, 0.6),
         ("Tenerife Teide", 28.27, -16.64, 0.35, 0.4), ("Vesuvius and Naples", 40.82, 14.43, 0.4, 0.5), ("Snowdonia", 53.07, -4.08, 0.4, 0.6),
         ("Scottish Highlands Ben Nevis", 56.8, -5.0, 0.6, 1.0), ("Lake District", 54.45, -3.1, 0.35, 0.5)]
MOON = [("Apollo 11 Tranquility", 0.67, 23.47, 1.5, 1.5), ("Apollo 15 Hadley Rille", 26.13, 3.63, 1.5, 1.5), ("Apollo 17 Taurus-Littrow", 20.19, 30.77, 1.5, 1.5),
        ("Tycho", -43.31, -11.36, 2.5, 3.0), ("Copernicus", 9.62, -20.08, 2.5, 2.5), ("Shackleton (south pole)", -89.9, 0, 2.0, 180.0),
        ("Aristarchus", 23.73, -47.49, 1.5, 1.5), ("Apollo 16 Descartes", -8.97, 15.50, 1.5, 1.5), ("Apollo 12 and 14", -3.3, -20.5, 1.6, 3.5),
        ("Chang'e 4 von Karman", -45.44, 177.60, 2.0, 2.5)]
MARS = [("Olympus Mons", 18.65, -133.8, 5.0, 6.0), ("Jezero crater", 18.44, 77.45, 1.5, 1.5), ("Gale crater Mount Sharp", -5.4, 137.8, 2.0, 2.0),
        ("Valles Marineris Candor", -6.5, -71.0, 4.0, 8.0), ("Valles Marineris Coprates", -13.5, -60.0, 3.0, 6.0), ("Hellas rim", -42.4, 70.5, 6.0, 8.0),
        ("Pavonis and Arsia Mons", -4.0, -115.0, 6.0, 4.0), ("Viking 1 Chryse", 22.27, -47.95, 1.0, 1.0), ("Meridiani Opportunity", -1.95, -5.53, 1.0, 1.0)]

def fetch(url, dest):
    if os.path.exists(dest): return True
    try:
        print("GET", url); req = urllib.request.Request(url, headers={"User-Agent": "cosmos-mod-build/1.0"})
        with urllib.request.urlopen(req, timeout=900) as r, open(dest, "wb") as f:
            while True:
                b = r.read(1 << 20)
                if not b: break
                f.write(b)
        return True
    except Exception as e:
        print("  failed", e); return False

def cut(grid, ppd, lat_top_first, lon0_global, spec, q):
    """grid: 2D array north-first rows, columns from lon0_global eastward at ppd samples/deg."""
    name, lat, lon, hh, hw = spec
    h, w = grid.shape
    la0, la1 = min(89.99, lat + hh), max(-89.99, lat - hh)
    j0 = int(np.floor((90 - la0) * ppd)); j1 = int(np.ceil((90 - la1) * ppd))
    i0 = int(np.floor((lon - hw - lon0_global) * ppd)); n = int(round(2 * hw * ppd))
    cols = [(i0 + k) % w for k in range(n)]
    sub = grid[j0:j1, :][:, cols]
    lat_n = 90 - j0 / ppd; lat_s = 90 - j1 / ppd; lon_w = lon0_global + i0 / ppd; lon_e = lon_w + n / ppd
    qd = np.clip(np.round(sub / q), -32768, 32767).astype("<i2")
    return name, lat_n, lat_s, lon_w, lon_e, qd, q

def write(body, patches):
    with gzip.open(f"{OUT}/{body}_patches.bin.gz", "wb", 9) as f:
        f.write(struct.pack("<i", len(patches)))
        for name, la0, la1, lo0, lo1, d, q in patches:
            f.write(name.encode()[:32].ljust(32, b"\0")); f.write(struct.pack("<ffffiif", la0, la1, lo0, lo1, d.shape[1], d.shape[0], q)); f.write(d.tobytes())
    print(body, len(patches), "patches", os.path.getsize(f"{OUT}/{body}_patches.bin.gz"), "bytes")

if fetch("https://pds-geosciences.wustl.edu/lro/lro-l-lola-3-rdr-v1/lrolol_1xxx/data/lola_gdr/cylindrical/img/ldem_16.img", TMP + "/moon.img"):
    a = np.fromfile(TMP + "/moon.img", dtype="<i2").reshape(2880, 5760).astype(np.float64) * 0.5
    write("moon", [cut(a, 16, True, 0.0, s, 4.0) for s in MOON])
if fetch("https://pds-geosciences.wustl.edu/mgs/mgs-m-mola-5-megdr-l3-v1/mgsl_300x/meg016/megt90n000eb.img", TMP + "/mars.img"):
    a = np.fromfile(TMP + "/mars.img", dtype=">i2").reshape(2880, 5760).astype(np.float64)
    write("mars", [cut(a, 16, True, 0.0, s, 4.0) for s in MARS])
if fetch("https://www.ngdc.noaa.gov/thredds/fileServer/global/ETOPO2022/60s/60s_surface_elev_netcdf/ETOPO_2022_v1_60s_N90W180_surface.nc", TMP + "/etopo.nc"):
    import netCDF4
    z = np.asarray(netCDF4.Dataset(TMP + "/etopo.nc").variables["z"][:], dtype=np.float64)[::-1, :]
    write("earth", [cut(z, 60, True, -180.0, s, 2.0) for s in EARTH])
