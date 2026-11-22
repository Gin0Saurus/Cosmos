#!/usr/bin/env python3
"""Repack fetch_dem.py grids into the compact bundled format (CDE2): resampled, quantised to Q metres,
row-delta encoded int16, gzip. Usage: pack_dem.py SRC_DIR DST_DIR"""
import gzip, struct, sys, numpy as np
from PIL import Image
SRC, DST = sys.argv[1], sys.argv[2]
PLAN = {"earth": (6, 8.0), "moon": (4, 8.0), "mars": (4, 8.0)}   # pixels per degree, metres per unit
for name, (ppd, q) in PLAN.items():
    raw = gzip.open(f"{SRC}/{name}.dem.gz").read()
    w, h, mpu = struct.unpack("<iif", raw[4:16]); tag = raw[16:32]
    a = np.frombuffer(raw[32:], "<i2").reshape(h, w).astype(np.float32) * mpu
    b = np.asarray(Image.fromarray(a).resize((360 * ppd, 180 * ppd), Image.BOX), dtype=np.float64)
    c = np.clip(np.round(b / q), -32768, 32767).astype(np.int32)
    d = np.diff(c, axis=1, prepend=0).astype("<i2")
    with gzip.open(f"{DST}/{name}.dem.gz", "wb", 9) as f:
        f.write(b"CDE2" + struct.pack("<iif", 360 * ppd, 180 * ppd, q) + tag); f.write(d.tobytes())
    print(name, b.shape, "min", b.min(), "max", b.max())
