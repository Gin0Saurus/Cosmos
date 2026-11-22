#!/usr/bin/env python3
"""
Compile real astronomical catalogues into the compact resource files the Cosmos engine loads.

Every output row carries a provenance tag so the game can always tell the player whether a number is
  M  = measured / catalogued value (source named in sources.md)
  E  = scientific estimate derived from measured values by a stated formula
  P  = procedural approximation (deterministic, not a real measurement)

Inputs (all cloned from public GitHub repositories, see sources.md):
  astronexus/hyg-database      hyg/CURRENT/hygdata_v41.csv      (HYG v4.1: Hipparcos + Yale BSC + Gliese)
  mattiaverga/openngc          database_files/NGC.csv            (OpenNGC: NGC/IC deep-sky objects)
  openexoplanetcatalogue/oec_tables  comma_separated/open_exoplanet_catalogue.txt
"""
import csv, json, math, os, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "/home/claude"
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "cosmos/core/src/main/resources/cosmos/data")
os.makedirs(OUT, exist_ok=True)

HYG = os.path.join(ROOT, "astronexus/hyg-database/hyg/CURRENT/hygdata_v41.csv")
NGC = os.path.join(ROOT, "mattiaverga/openngc/database_files/NGC.csv")
LMC_OUTLINE = os.path.join(ROOT, "mattiaverga/openngc/outlines/objects/LMC_lv1.txt")
OEC = os.path.join(ROOT, "openexoplanetcatalogue/oec_tables/comma_separated/open_exoplanet_catalogue.txt")

def f(x, default=None):
    try:
        return float(x) if x not in ("", None) else default
    except ValueError:
        return default

# ---------------------------------------------------------------- stars
def compile_stars():
    rows, skipped = [], 0
    with open(HYG, newline="") as fh:
        for r in csv.DictReader(fh):
            if r["id"] == "0":
                continue  # the Sun is defined in solar_system.json
            dist = f(r["dist"])
            # HYG uses 100000 pc as "no valid parallax" marker
            if dist is None or dist <= 0 or dist >= 100000:
                skipped += 1
                continue
            name = r["proper"].strip()
            bayer = r["bayer"].strip()
            con = r["con"].strip()
            desig = bayer  # raw HYG Bayer code, e.g. "Alp-1"; expanded to "Alpha Centauri" by the engine
            ids = []
            for k, pre in (("hip", "HIP "), ("hd", "HD "), ("hr", "HR "), ("gl", "")):
                v = r[k].strip()
                if v:
                    ids.append(pre + v)
            rows.append([
                r["id"], name, desig, con, "|".join(ids),
                "%.6f" % f(r["x"]), "%.6f" % f(r["y"]), "%.6f" % f(r["z"]),  # parsecs, ICRS equatorial
                "%.3f" % f(r["mag"], 99), "%.3f" % f(r["absmag"], 99),
                "" if r["ci"] == "" else "%.3f" % f(r["ci"]),
                r["spect"].strip(), "%.6g" % f(r["lum"], 0),
                "%.6g" % f(r["vx"], 0), "%.6g" % f(r["vy"], 0), "%.6g" % f(r["vz"], 0),  # pc/yr
                r["comp_primary"], r["base"].strip(),
            ])
    with open(os.path.join(OUT, "stars.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["hyg", "name", "bayer", "con", "ids", "x_pc", "y_pc", "z_pc", "mag", "absmag",
                    "bv", "spect", "lum_sun", "vx", "vy", "vz", "comp_primary", "base"])
        w.writerows(rows)
    print("stars:", len(rows), "skipped (no parallax):", skipped)
    return rows

# ---------------------------------------------------------------- exoplanets
def hms(s):
    h, m, sec = (float(x) for x in s.split())
    return (h + m / 60 + sec / 3600) * 15.0

def dms(s):
    parts = s.split()
    sign = -1 if parts[0].startswith("-") else 1
    d, m, sec = abs(float(parts[0])), float(parts[1]), float(parts[2])
    return sign * (d + m / 60 + sec / 3600)

def compile_exoplanets(stars):
    # spatial index of HYG stars by direction for host matching
    idx = {}
    by_name = {}
    for s in stars:
        for key in [s[1]] + s[4].split("|"):
            if key:
                by_name.setdefault(key.lower(), s[0])
        x, y, z = float(s[5]), float(s[6]), float(s[7])
        d = math.sqrt(x * x + y * y + z * z)
        ra = math.degrees(math.atan2(y, x)) % 360
        dec = math.degrees(math.asin(z / d))
        idx.setdefault((int(ra), int(dec + 90)), []).append((ra, dec, d, s[0]))
    out, matched = [], 0
    with open(OEC, newline="") as fh:
        rd = csv.reader(fh)
        header = None
        for r in rd:
            if r and r[0].startswith("#"):
                continue
            if header is None:
                header = r
                continue
            if not r or len(r) < len(header):
                continue
            d = dict(zip(header, r))
            lists = d["list"]
            if "Solar System" in lists or "Retracted" in lists:
                continue
            status = "confirmed" if "Confirmed" in lists else ("controversial" if "Controversial" in lists else "candidate")
            name = d["name"].strip()
            host = name.rsplit(" ", 1)[0] if " " in name else name
            ra = dec = None
            host_id = ""
            if d["system_rightascension"] and d["system_declination"]:
                try:
                    ra, dec = hms(d["system_rightascension"]), dms(d["system_declination"])
                except Exception:
                    ra = dec = None
            dist = f(d["system_distance"])
            # 1) exact name match against HYG proper names / HD / HIP / Gliese ids (handles hosts like
            #    Proxima Centauri whose OEC *system* coordinates are those of Alpha Centauri)
            host_id = by_name.get(host.lower(), "")
            if host_id:
                matched += 1
            elif ra is not None:
                best = None
                for dra in (-1, 0, 1):
                    for dde in (-1, 0, 1):
                        for (sra, sdec, sd, sid) in idx.get(((int(ra) + dra) % 360, int(dec + 90) + dde), []):
                            sep = math.hypot((sra - ra) * math.cos(math.radians(dec)), sdec - dec)
                            if sep < 0.01 and (best is None or sep < best[0]):
                                if dist is None or abs(sd - dist) / max(dist, 1e-9) < 0.25:
                                    best = (sep, sid)
                if best:
                    host_id = best[1]
                    matched += 1
            out.append([name, host, host_id, status,
                        d["mass"], d["radius"], d["period"], d["semimajoraxis"], d["eccentricity"],
                        d["periastron"], d["longitude"], d["ascendingnode"], d["inclination"],
                        d["temperature"], d["discoverymethod"], d["discoveryyear"],
                        "" if ra is None else "%.6f" % ra, "" if dec is None else "%.6f" % dec,
                        d["system_distance"], d["hoststar_mass"], d["hoststar_radius"],
                        d["hoststar_temperature"], d["hoststar_metallicity"], d["binaryflag"]])
    with open(os.path.join(OUT, "exoplanets.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["name", "host", "host_hyg", "status", "mass_mj", "radius_rj", "period_d", "a_au", "e",
                    "periastron_deg", "mean_longitude_deg", "node_deg", "incl_deg", "teq_k", "method", "year",
                    "ra_deg", "dec_deg", "dist_pc", "star_mass_msun", "star_radius_rsun", "star_teff_k",
                    "star_feh", "binary_flag"])
        w.writerows(out)
    print("exoplanets:", len(out), "host matched to HYG star:", matched)

# ---------------------------------------------------------------- deep sky
# Measured distances (kpc) for named objects, each with its source. Everything else gets an estimate.
MEASURED_DIST_KPC = {
    "NGC0224": (761.0, "Li et al. 2021, ApJ 920, 84 (Cepheids, 761+/-11 kpc)"),
    "NGC0598": (730.0, "Brunthaler et al. 2005, Science 307 (H2O maser geometry, 730+/-168 kpc)"),
    "NGC0292": (62.43, "Graczyk et al. 2020 (eclipsing binaries, SMC 62.43 kpc)"),
    "NGC4486": (16800.0, "EHT Collaboration 2019, Paper VI (adopted 16.8 Mpc)"),
    "NGC1976": (0.388, "Kounkel et al. 2017, ApJ 834, 142 (VLBA, ONC 388+/-5 pc)"),
}
H0 = 70.0  # km/s/Mpc, round value; the Hubble tension (67-73) is noted in sources.md
C_KMS = 299792.458
TYPE_NAMES = {"G": "galaxy", "GPair": "galaxy pair", "GTrpl": "galaxy triplet", "GGroup": "galaxy group",
              "OCl": "open cluster", "GCl": "globular cluster", "Cl+N": "cluster with nebula", "PN": "planetary nebula",
              "Neb": "nebula", "HII": "HII region", "RfN": "reflection nebula", "EmN": "emission nebula",
              "SNR": "supernova remnant", "*Ass": "stellar association", "Nova": "nova"}

def sexa(s, hours):
    sign = -1 if s.strip().startswith("-") else 1
    a, b, c = (abs(float(x)) for x in s.replace("+", "").replace("-", "").split(":"))
    v = a + b / 60 + c / 3600
    return sign * v * (15 if hours else 1)

def compile_dso():
    out = []
    with open(NGC, newline="") as fh:
        for r in csv.DictReader(fh, delimiter=";"):
            t = r["Type"]
            if t not in TYPE_NAMES or not r["RA"]:
                continue
            ra, dec = sexa(r["RA"], True), sexa(r["Dec"], False)
            maj = f(r["MajAx"])  # arcmin
            dist_kpc, kind, src = None, "", ""
            if r["Name"] in MEASURED_DIST_KPC:
                dist_kpc, src = MEASURED_DIST_KPC[r["Name"]]
                kind = "M"
            elif t.startswith("G") and t != "GCl":
                rv = f(r["RadVel"])
                z = f(r["Redshift"])
                v = rv if rv is not None else (z * C_KMS if z is not None else None)
                if v is not None and v > 1500:
                    # low-redshift Hubble law; peculiar velocities (~300 km/s) dominate below ~1500 km/s
                    dist_kpc, kind, src = v / H0 * 1000.0, "E", "Hubble law v/H0, H0=70"
                elif maj:
                    # no usable distance: procedural guess from angular size assuming a typical diameter
                    typ_kpc = 10.0 if (r["Hubble"] or "").startswith("I") else 25.0
                    dist_kpc, kind, src = typ_kpc / math.radians(maj / 60.0), "P", "size-based guess"
            elif t in ("GCl", "OCl", "PN", "Cl+N", "Neb", "HII", "RfN", "EmN", "SNR", "*Ass"):
                pax = f(r["Pax"])  # mas
                if pax and pax > 0.05:
                    dist_kpc, kind, src = 1.0 / pax, "M", "OpenNGC parallax (Gaia)"
            if dist_kpc is None:
                kind = "U"  # direction known, distance unknown -> sky-sphere only, procedural placement in engine
            names = [n for n in (r["Common names"] or "").split(",") if n]
            m = r["M"].strip()
            if m:
                names.insert(0, "M" + str(int(m)))
                names.insert(1, "Messier " + str(int(m)))
            out.append([r["Name"], t, "%.6f" % ra, "%.6f" % dec, r["MajAx"], r["MinAx"], r["PosAng"],
                        r["V-Mag"] or r["B-Mag"], r["Hubble"], r["RadVel"], r["Redshift"],
                        "" if dist_kpc is None else "%.6g" % dist_kpc, kind, src, "|".join(names)])
    # LMC is not an NGC object: position from the centroid of the OpenNGC LMC outline (level 1)
    xs = ys = zs = 0.0
    with open(LMC_OUTLINE) as fh:
        next(fh)
        for line in fh:
            p = line.split("\t")
            a, d = math.radians(float(p[2])), math.radians(float(p[3]))
            xs += math.cos(d) * math.cos(a); ys += math.cos(d) * math.sin(a); zs += math.sin(d)
    ra = math.degrees(math.atan2(ys, xs)) % 360
    dec = math.degrees(math.atan2(zs, math.hypot(xs, ys)))
    out.append(["LMC", "G", "%.6f" % ra, "%.6f" % dec, "", "", "", "0.9", "SBm", "262", "", "49.59", "M",
                "Pietrzynski et al. 2019, Nature 567 (eclipsing binaries); position = OpenNGC outline centroid",
                "Large Magellanic Cloud"])
    with open(os.path.join(OUT, "deepsky.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["id", "type", "ra_deg", "dec_deg", "maj_arcmin", "min_arcmin", "pa_deg", "mag", "hubble",
                    "radvel_kms", "redshift", "dist_kpc", "dist_kind", "dist_source", "names"])
        w.writerows(out)
    kinds = {}
    for o in out:
        kinds[o[12]] = kinds.get(o[12], 0) + 1
    print("deep sky objects:", len(out), "distance kinds:", kinds)

if __name__ == "__main__":
    stars = compile_stars()
    compile_exoplanets(stars)
    compile_dso()
