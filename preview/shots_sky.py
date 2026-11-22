import sys, numpy as np
sys.path.insert(0, '.')
from harness import *
ctx = context()
sky = sky_texture(ctx, '../build/preview/sky_earth_diffuse.f32')
W, H = 1280, 720
for name, ra, dec, fov in [('orion', 83.8, 0.0, 60), ('sagittarius', 266.4, -29.0, 80), ('crux_centaurus', 200.0, -60.0, 60), ('big_dipper', 180.0, 55.0, 60)]:
    B = camera_basis(radec(ra, dec), np.array([0, 0, 1.0]))
    sc = HdrScene(ctx, (W, H))
    tf = np.tan(np.radians(fov / 2))
    sc.fullscreen('sky_hdr.fsh', {'uCamToIcrs': B, 'uTanHalfFov': tf, 'uResolution': (W, H), 'uGain': 7.0}, {'uSky': sky})
    sc.stars('../build/preview/stars_earth.bin', {'uIcrsToCam': B.T, 'uTanHalfFov': tf, 'uResolution': (W, H), 'uMagLimit': 6.5, 'uPixelScale': 1.0})
    sc.save(f'../build/preview/sky_{name}.png', exposure=1.1, bloom=0.8)
print('ok')
