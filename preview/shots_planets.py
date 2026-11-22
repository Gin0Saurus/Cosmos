import sys, json, numpy as np
sys.path.insert(0, '.')
from harness import *
ctx = context()
T = '/tmp/threejs/examples/textures/planets/'
def tex(path, comps=3, alpha=False):
    im = Image.open(path)
    im = im.convert('RGBA').getchannel('A') if alpha else im.convert('RGB' if comps == 3 else 'L')
    t = ctx.texture(im.size, comps, im.tobytes()); t.build_mipmaps()
    t.filter = (moderngl.LINEAR_MIPMAP_LINEAR, moderngl.LINEAR); t.repeat_x = True; t.repeat_y = False
    return t
sky = sky_texture(ctx, '../build/preview/sky_earth_diffuse.f32')
solar = json.load(open('../core/src/main/resources/cosmos/data/solar_system.json'))
atm = solar['atmospheres']
W, H = 1280, 720
def rot_about(v, axis, ang):
    axis = axis / np.linalg.norm(axis); return v*np.cos(ang) + np.cross(axis, v)*np.sin(ang) + axis*np.dot(axis, v)*(1-np.cos(ang))
def shot(name, state, cam, look, up, fov, a=None, textures=None, albedo=(0.3, 0.3, 0.3), exposure=1.0, clouds=False, night=True):
    st = json.load(open(state)); R = sum(st['radii_km']) / 3
    sun = np.array(st['sun_dir']); M = np.array(st['body_to_icrs'])
    u = {'uResolution': (W, H), 'uCamPos': cam, 'uCamBasis': camera_basis(look, up), 'uTanHalfFov': np.tan(np.radians(fov/2)),
         'uIcrsToBody': M.T, 'uSunDir': sun, 'uSunIntensity': 20.0 * (149597870.7 / st['sun_dist_km'])**2, 'uHasNightLights': 1 if (textures and 'uNight' in textures and night) else 0, 'uSunAngularRadius': 695700/st['sun_dist_km'], 'uRadius': R,
         'uAtmoTop': 0.0, 'uRayleigh': (0,0,0), 'uRayleighH': 1.0, 'uMieScatter': 0.0, 'uMieExtinct': 0.0, 'uMieH': 1.0, 'uMieG': 0.8,
         'uOzone': (0,0,0), 'uTint': (1,1,1), 'uHasTextures': 1 if textures else 0, 'uHasClouds': 1 if clouds else 0, 'uAlbedo': albedo,
         'uSkyGain': 7.0, 'uSkyToIcrs': np.eye(3)}
    if a:
        u.update({'uAtmoTop': a['top_km'], 'uRayleigh': tuple(x*1e3 for x in a['rayleigh_per_m']), 'uRayleighH': a['rayleigh_h_km'],
                  'uMieScatter': a['mie_scatter_per_m']*1e3, 'uMieExtinct': a['mie_extinct_per_m']*1e3, 'uMieH': a['mie_h_km'], 'uMieG': a['mie_g'],
                  'uOzone': tuple(x*1e3 for x in a.get('ozone_absorb_per_m', [0,0,0])), 'uTint': tuple(a.get('dust_tint', [1,1,1]))})
    texs = {'uSky': sky}
    if textures: texs.update(textures)
    sc = HdrScene(ctx, (W, H))
    sc.fullscreen('planet.fsh', u, texs)
    sc.save(f'../build/preview/planet_{name}.png', exposure=exposure, bloom=0.4)
    print(name)

which = sys.argv[1] if len(sys.argv) > 1 else 'all'
st = json.load(open('../build/preview/state_earth.json'))
sun = np.array(st['sun_dir']); M = np.array(st['body_to_icrs']); pole = M[:, 2]
if which in ('all', 'earth'):
    earth_tex = {'uDay': tex(T+'earth_day_4096.jpg'), 'uNight': tex(T+'earth_night_4096.jpg'), 'uClouds': tex(T+'earth_clouds_1024.png', 1, alpha=True), 'uSpec': tex(T+'earth_specular_2048.jpg', 1)}
    def geo(lat, lon):  # body-fixed lat/lon (deg) -> ICRS unit vector
        la, lo = np.radians(lat), np.radians(lon)
        return M @ np.array([np.cos(la)*np.cos(lo), np.cos(la)*np.sin(lo), np.sin(la)])
    d = geo(-12, -55)
    shot('earth_south_america_12000km', '../build/preview/state_earth.json', d * (6371 + 12000), -d, pole, 52, atm['earth'], earth_tex, exposure=0.9, clouds=True)
    d = geo(42, 8)
    shot('earth_europe_dusk_7000km', '../build/preview/state_earth.json', d * (6371 + 7000), -d, pole, 70, atm['earth'], earth_tex, exposure=1.4, clouds=True)
    d2 = geo(-20, -70); cam2 = d2 * (6371 + 420)
    horizon = np.cross(np.cross(d2, sun), d2); horizon /= np.linalg.norm(horizon)
    look = horizon * np.cos(np.radians(16)) - d2 * np.sin(np.radians(16))
    shot('earth_leo_420km_andes', '../build/preview/state_earth.json', cam2, look, d2, 70, atm['earth'], earth_tex, exposure=0.9, clouds=True)
if which in ('all', 'moon'):
    stm = json.load(open('../build/preview/state_moon.json')); sunm = np.array(stm['sun_dir']); Mm = np.array(stm['body_to_icrs'])
    d = rot_about(sunm, Mm[:, 2], np.radians(-60)); d /= np.linalg.norm(d)
    shot('moon_3000km', '../build/preview/state_moon.json', d * (1737.4 + 3000), -d, Mm[:, 2], 55, None,
         {'uDay': tex(T+'moon_1024.jpg'), 'uNight': tex(T+'moon_1024.jpg'), 'uClouds': tex(T+'earth_clouds_1024.png', 1, alpha=True), 'uSpec': tex(T+'earth_clouds_1024.png', 1)}, exposure=1.0, night=False)
if which in ('all', 'mars'):
    stm = json.load(open('../build/preview/state_mars.json')); sunm = np.array(stm['sun_dir']); Mm = np.array(stm['body_to_icrs'])
    d = rot_about(sunm, Mm[:, 2], np.radians(-70)); d /= np.linalg.norm(d)
    shot('mars_8000km_atmosphere_only', '../build/preview/state_mars.json', d * (3389.5 + 8000), -d, Mm[:, 2], 55, atm['mars'], None, albedo=(0.25, 0.12, 0.06), exposure=1.0)
