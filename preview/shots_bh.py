import sys, time, numpy as np
sys.path.insert(0, '.')
from harness import *
ctx = context()
sky = sky_texture(ctx, '../build/preview/sky_gaiabh3.f32')
W, H = int(sys.argv[1]) if len(sys.argv) > 1 else 960, int(sys.argv[2]) if len(sys.argv) > 2 else 540
def isco(a):
    z1 = 1 + np.cbrt(1 - a*a) * (np.cbrt(1 + a) + np.cbrt(1 - a)); z2 = np.sqrt(3*a*a + z1*z1)
    return 3 + z2 - np.sign(a) * np.sqrt((3 - z1) * (3 + z1 + 2*z2))
# BH frame: z = disc normal. Map BH frame to ICRS so the background is the real sky around Gaia BH3:
# camera looks from BH3 towards the Galactic centre direction, so the Milky Way bulge sits behind the hole.
gc = np.array([-0.0548755604, -0.8734370902, -0.4838350155])  # ICRS unit vector to l=0,b=0 (row 0 of eq->gal)
ngp = np.array([-0.8676661490, -0.1980763734, 0.4559837762])
bx = gc; bz = ngp; by = np.cross(bz, bx)
BH2ICRS = np.stack([bx, by, bz], axis=1)
def shot(name, cam_r, incl_deg, disc, fov, temp=9000, inner=None, spin=0.0, steps=500, exposure=1.0, bright=1.0, skygain=60.0):
    inc = np.radians(incl_deg)
    cam = np.array([-cam_r * np.sin(inc), 0.0, cam_r * np.cos(inc)])   # camera on the -x side, looking towards +x (to GC)
    B = camera_basis(-cam / np.linalg.norm(cam), np.array([0, 0, 1.0]) if incl_deg > 1 else np.array([1.0, 0, 0]))
    sc = HdrScene(ctx, (W, H))
    t0 = time.time()
    sc.fullscreen('blackhole.fsh', {'uResolution': (W, H), 'uCamPos': cam, 'uCamBasis': B, 'uBhToIcrs': BH2ICRS,
        'uTanHalfFov': np.tan(np.radians(fov / 2)), 'uDiscNormal': (0, 0, 1), 'uDiscInner': inner or isco(spin), 'uDiscOuter': 18.0,
        'uDiscTemp': temp, 'uDiscBrightness': bright, 'uDiscOn': 1 if disc else 0, 'uMaxSteps': steps, 'uSkyGain': skygain, 'uSpin': spin, 'uTime': 0.0}, {'uSky': sky})
    sc.save(f'../build/preview/bh_{name}.png', exposure=exposure, bloom=0.9)
    print(name, f'{time.time() - t0:.1f}s')
which = sys.argv[3] if len(sys.argv) > 3 else 'all'
if which in ('all', 'lens'): shot('gaiabh3_lensing', 30, 80, False, 50, exposure=1.2)
if which in ('all', 'disc'): shot('disc_edge', 34, 84, True, 36, temp=9000, spin=0.0, exposure=0.9)
if which in ('all', 'disc2'): shot('disc_spin09_incl60', 30, 60, True, 42, temp=11000, spin=0.9, exposure=0.9)
