import sys, numpy as np
sys.path.insert(0, '.')
from harness import *
ctx = context()
W, H = 1280, 720
def shot(name, cam, target, up, fov, exposure, scale=1.0, bar=27.0, seed=0.0):
    look = np.array(target, float) - np.array(cam, float)
    sc = HdrScene(ctx, (W, H))
    sc.fullscreen('galaxy.fsh', {'uResolution': (W, H), 'uCamPos': cam, 'uCamBasis': camera_basis(look, np.array(up, float)),
        'uTanHalfFov': np.tan(np.radians(fov/2)), 'uScale': scale, 'uBarAngle': np.radians(bar), 'uSeed': seed})
    sc.save(f'../build/preview/galaxy_{name}.png', exposure=exposure, bloom=0.5)
    print(name)
# Milky Way seen from 60 kpc above the Galactic plane (Sun is at (8277, 0, 20.8) pc)
shot('milkyway_face_on_60kpc', (0, 0, 60000), (0, 0, 0), (1, 0, 0), 42, 1.6)
# Oblique view from 45 kpc
shot('milkyway_oblique', (-30000, -25000, 22000), (0, 0, 0), (0, 0, 1), 40, 1.6)
