// Shared GLSL for Cosmos (GLSL 3.30 core, the profile Minecraft Java uses). Included by the other shaders.
#ifndef COSMOS_COMMON
#define COSMOS_COMMON
const float PI = 3.14159265358979;

// ACES filmic tone curve (Narkowicz 2015 fit), input linear HDR, output display-referred [0,1].
vec3 tonemapACES(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}
vec3 linearToSrgb(vec3 c) {
    return mix(12.92 * c, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}

// Planckian locus in CIE 1931 xy (Kim et al. 2002 cubic spline, 1667-25000 K) -> linear sRGB chromaticity (max = 1).
vec3 blackbody(float T) {
    T = clamp(T, 1667.0, 25000.0);
    float t = 1e3 / T, t2 = t * t, t3 = t2 * t;
    float x = T < 4000.0 ? -0.2661239 * t3 - 0.2343589 * t2 + 0.8776956 * t + 0.179910
                         : -3.0258469 * t3 + 2.1070379 * t2 + 0.2226347 * t + 0.240390;
    float x2 = x * x, x3 = x2 * x, y;
    if (T < 2222.0) y = -1.1063814 * x3 - 1.34811020 * x2 + 2.18555832 * x - 0.20219683;
    else if (T < 4000.0) y = -0.9549476 * x3 - 1.37418593 * x2 + 2.09137015 * x - 0.16748867;
    else y = 3.0817580 * x3 - 5.87338670 * x2 + 3.75112997 * x - 0.37001483;
    vec3 XYZ = vec3(x / y, 1.0, (1.0 - x - y) / y);
    vec3 rgb = mat3(3.2406, -0.9689, 0.0557, -1.5372, 1.8758, -0.2040, -0.4986, 0.0415, 1.0570) * XYZ;
    rgb = max(rgb, vec3(0.0));
    return rgb / max(rgb.r, max(rgb.g, rgb.b));
}

// Equirectangular lookup for an ICRS direction (same convention as SkyMap.java: RA increases to the left).
vec2 equirectUV(vec3 d) {
    float ra = atan(d.y, d.x), dec = asin(clamp(d.z, -1.0, 1.0));
    return vec2((PI - ra) / (2.0 * PI), (PI / 2.0 - dec) / PI);
}

// Ray-sphere intersection: returns (t_near, t_far), or (-1,-1) if no hit. ro relative to sphere centre.
vec2 raySphere(vec3 ro, vec3 rd, float r) {
    float b = dot(ro, rd), c = dot(ro, ro) - r * r, h = b * b - c;
    if (h < 0.0) return vec2(-1.0);
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}
#endif
