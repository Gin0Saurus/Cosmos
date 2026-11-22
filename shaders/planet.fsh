#version 330 core
// Planet + atmosphere from space or from inside the atmosphere, physically based single scattering.
//
// Units: kilometres, planet centre at the origin (floating origin: the CPU sends camera-relative values in double and
// they are rebased per frame, so float precision is relative to the planet, never to a world origin).
// Atmosphere: Rayleigh (wavelength-dependent, scale height H_R), Mie (aerosols, Henyey-Greenstein phase g, H_M) and
// ozone absorption (tent profile around 25 km), integrated along the view ray with secondary sun-ray optical depths
// (Nishita 1993 / Bruneton-Neyret 2008 single-scattering model). Coefficients come from solar_system.json.
// Surface: albedo texture in body-fixed lat/lon, lit by sunlight attenuated through the atmosphere; night-side city
// lights; cloud layer that casts shadows; ocean sun glint from the specular mask. Without textures the surface falls
// back to the body's Bond albedo colour.
#include "common.glsl"
uniform vec2 uResolution;
uniform vec3 uCamPos;            // km, ICRS axes, relative to planet centre
uniform mat3 uCamBasis;          // columns right, up, -forward (ICRS)
uniform float uTanHalfFov;
uniform mat3 uIcrsToBody;        // ICRS -> body-fixed (transpose of IAU orientation)
uniform vec3 uSunDir;            // unit, ICRS, from planet to Sun
uniform float uSunIntensity;
uniform float uSunAngularRadius; // rad, as seen from the planet
uniform float uRadius;           // km (mean)
uniform float uAtmoTop;          // km above surface (0 = airless)
uniform vec3 uRayleigh;          // per km
uniform float uRayleighH;        // km
uniform float uMieScatter, uMieExtinct, uMieH, uMieG; // per km, km
uniform vec3 uOzone;             // per km at peak
uniform vec3 uTint;              // aerosol tint (Mars dust etc.)
uniform sampler2D uDay, uNight, uClouds, uSpec, uSky;
uniform int uHasTextures, uHasClouds, uHasNightLights;
uniform vec3 uAlbedo;
uniform float uSkyGain;
uniform mat3 uSkyToIcrs;
out vec4 fragColor;

const int VIEW_STEPS = 24, LIGHT_STEPS = 8;

vec2 lonlatUV(vec3 pIcrs) {
    vec3 b = normalize(uIcrsToBody * pIcrs);
    float lon = atan(b.y, b.x), lat = asin(clamp(b.z, -1.0, 1.0));
    return vec2(lon / (2.0 * PI) + 0.5, 0.5 - lat / PI);
}

float ozoneDensity(float h) { return max(0.0, 1.0 - abs(h - 25.0) / 15.0); }

// optical depth from point p towards the Sun (returns vec3 extinction integral), or large if the planet blocks it
vec3 sunOpticalDepth(vec3 p) {
    vec2 tp = raySphere(p, uSunDir, uRadius);
    if (tp.x > 0.0) return vec3(1e9);
    float tTop = raySphere(p, uSunDir, uRadius + uAtmoTop).y;
    float ds = tTop / float(LIGHT_STEPS);
    vec3 od = vec3(0.0);
    for (int i = 0; i < LIGHT_STEPS; i++) {
        vec3 q = p + uSunDir * (ds * (float(i) + 0.5));
        float h = length(q) - uRadius;
        od += (uRayleigh * exp(-h / uRayleighH) + uMieExtinct * exp(-h / uMieH) + uOzone * ozoneDensity(h)) * ds;
    }
    return od;
}

float phaseR(float mu) { return 3.0 / (16.0 * PI) * (1.0 + mu * mu); }
float phaseM(float mu) { float g = uMieG; return 3.0 / (8.0 * PI) * (1.0 - g * g) * (1.0 + mu * mu) / ((2.0 + g * g) * pow(1.0 + g * g - 2.0 * g * mu, 1.5)); }

void main() {
    vec2 p = (gl_FragCoord.xy / uResolution * 2.0 - 1.0) * vec2(uResolution.x / uResolution.y, 1.0) * uTanHalfFov;
    vec3 rd = normalize(uCamBasis * vec3(p, -1.0));
    vec3 ro = uCamPos;
    float mu = dot(rd, uSunDir);

    // background: real sky map + the Sun's disc (limb-darkened)
    vec3 bg = uSkyGain * texture(uSky, equirectUV(normalize(uSkyToIcrs * rd))).rgb;
    float sunCos = cos(uSunAngularRadius);
    if (mu > sunCos) {
        float x = clamp((1.0 - mu) / (1.0 - sunCos), 0.0, 1.0);
        bg += uSunIntensity * 4e3 * (1.0 - 0.6 * (1.0 - sqrt(1.0 - x)));
    }

    vec2 tPlanet = raySphere(ro, rd, uRadius);
    float tSurf = tPlanet.x > 0.0 ? tPlanet.x : (tPlanet.y > 0.0 ? 0.0 : -1.0);
    vec3 col;
    vec3 transView = vec3(1.0);
    vec3 inscatter = vec3(0.0);

    if (uAtmoTop > 0.0) {
        vec2 tA = raySphere(ro, rd, uRadius + uAtmoTop);
        if (tA.y > 0.0) {
            float t0 = max(tA.x, 0.0), t1 = tSurf > 0.0 ? tSurf : tA.y;
            float ds = (t1 - t0) / float(VIEW_STEPS);
            vec3 odView = vec3(0.0);
            for (int i = 0; i < VIEW_STEPS; i++) {
                vec3 q = ro + rd * (t0 + ds * (float(i) + 0.5));
                float h = length(q) - uRadius;
                float dR = exp(-h / uRayleighH), dM = exp(-h / uMieH);
                odView += (uRayleigh * dR + uMieExtinct * dM + uOzone * ozoneDensity(h)) * ds * 0.5;
                vec3 odSun = sunOpticalDepth(q);
                vec3 T = exp(-(odView + odSun));
                inscatter += T * (uRayleigh * dR * phaseR(mu) + uTint * uMieScatter * dM * phaseM(mu)) * ds;
                odView += (uRayleigh * dR + uMieExtinct * dM + uOzone * ozoneDensity(h)) * ds * 0.5;
            }
            transView = exp(-odView);
            inscatter *= uSunIntensity;
        }
    }

    if (tSurf > 0.0) {
        vec3 hit = ro + rd * tSurf;
        vec3 n = normalize(hit);
        float ndl = dot(n, uSunDir);
        vec3 sunAtSurface = uAtmoTop > 0.0 ? exp(-sunOpticalDepth(hit + n * 0.01)) : vec3(ndl > 0.0 ? 1.0 : 0.0);
        vec2 uv = lonlatUV(hit);
        vec3 albedo = uHasTextures == 1 ? pow(texture(uDay, uv).rgb, vec3(2.2)) : uAlbedo;
        float cloud = 0.0;
        if (uHasClouds == 1) {
            cloud = texture(uClouds, uv).r;
            // cloud shadow: sample the cloud layer offset towards the Sun (clouds at ~8 km)
            vec2 uvs = lonlatUV(hit + uSunDir * 8.0 / max(ndl, 0.08));
            sunAtSurface *= 1.0 - 0.6 * texture(uClouds, uvs).r;
        }
        vec3 lit = albedo / PI * max(ndl, 0.0) * sunAtSurface * uSunIntensity;
        // ocean glint (GGX-ish lobe), only where the specular mask says water
        if (uHasTextures == 1) {
            float water = texture(uSpec, uv).r;
            vec3 hvec = normalize(uSunDir - rd);
            float spec = pow(max(dot(n, hvec), 0.0), 400.0) * 8.0 * water * step(0.0, ndl);
            lit += spec * sunAtSurface * uSunIntensity * 0.04 * (1.0 - cloud);
        }
        vec3 cloudLit = vec3(0.9) / PI * max(ndl, 0.0) * sunAtSurface * uSunIntensity;
        vec3 surface = mix(lit, cloudLit, cloud);
        // city lights on the night side (fade across the terminator)
        if (uHasNightLights == 1) {
            vec3 lights = pow(texture(uNight, uv).rgb, vec3(2.2)) * vec3(1.0, 0.85, 0.6);
            surface += lights * 0.25 * smoothstep(0.05, -0.15, ndl) * (1.0 - 0.8 * cloud);
        }
        col = surface * transView + inscatter;
    } else {
        col = bg * transView + inscatter;
    }
    fragColor = vec4(col, 1.0);
}
