#version 330 core
// Black hole renderer: per-pixel null-geodesic integration in Schwarzschild spacetime (geometric units, M = 1).
//
// Physics (same equations as Geodesics.java, which the unit tests validate against 4M/b deflection and the 3*sqrt(3) M
// capture radius):
//   photon path:  x'' = -3 h^2 x / r^5,  h = |x cross x'|  (exactly u'' + u = 3 u^2 for light)
//   horizon r = 2 (rays that reach it are captured -> black shadow, radius 3*sqrt(3) as seen from afar)
//   thin accretion disc in the plane perpendicular to uDiscNormal, r in [uDiscInner, uDiscOuter], Keplerian geodesic
//   orbits; emitted temperature T(r) ~ r^-3/4 (1 - sqrt(r_in / r))^1/4 (Shakura-Sunyaev / Page-Thorne shape)
//   redshift  g = 1 / (u^t (1 - Omega L_z)),  u^t = 1/sqrt(1 - 3/r), Omega = r^-3/2
//     -> gravitational redshift + transverse Doppler + line-of-sight Doppler in one exact factor
//   observed: colour = blackbody(g T), bolometric intensity ~ g^4 T^4 (relativistic beaming)
// Every disc crossing is accumulated (the primary image, the lensed image of the far side bent over the top, and the
// thin higher-order photon-ring images), weighted by the remaining transmittance.
// Escaped rays sample the HDR sky map in their final direction: Einstein ring, background distortion, secondary images.
// Rotation: the disc uses exact Kerr equatorial circular-orbit kinematics for spin a (Bardeen, Press & Teukolsky 1972):
//   Omega = 1/(r^3/2 + a),  u^t = (r^3/2 + a) / (r^3/4 sqrt(r^3/2 - 3 r^1/2 + 2a)),  inner edge at the prograde ISCO.
// The light paths themselves are Schwarzschild (frame-dragging of rays is not modelled; flagged in the UI).
// Disc turbulence (spiral streaks sheared by differential rotation) is PROCEDURAL texture, not simulated MHD.
#include "common.glsl"
uniform sampler2D uSky;
uniform vec2 uResolution;
uniform vec3 uCamPos;        // camera position in BH frame, units of M
uniform mat3 uCamBasis;      // columns: right, up, -forward (BH frame)
uniform mat3 uBhToIcrs;      // BH frame -> ICRS for background lookup
uniform float uTanHalfFov;
uniform vec3 uDiscNormal;
uniform float uDiscInner, uDiscOuter;
uniform float uDiscTemp;     // temperature scale (K) of the T(r) profile
uniform float uDiscBrightness;
uniform int uDiscOn;
uniform int uMaxSteps;
uniform float uSkyGain;
uniform float uSpin;          // dimensionless spin a* (0..0.998)
uniform float uTime;          // seconds of animation (disc pattern rotates at the local orbital frequency)

float hash12(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), f.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), f.x), f.y);
}
float discPattern(float r, float phi) {
    // log-radius x sheared angle: Keplerian shear winds features into trailing spirals
    float omega = pow(r, -1.5);
    float a = phi + omega * uTime * 0.02;
    // seam-free in phi: sample the plane on a circle whose radius grows with log r (trailing spiral via the offset)
    float lr = log(r);
    vec2 q = vec2(cos(a + lr * 0.7), sin(a + lr * 0.7)) * (4.0 + lr * 6.0) + vec2(lr * 9.0, 0.0);
    float n = 0.55 * vnoise(q) + 0.3 * vnoise(q * 2.1 + 7.0) + 0.15 * vnoise(q * 4.3 + 3.0);
    return 0.55 + 0.9 * n;
}     // exposure of the background sky map relative to disc emission
out vec4 fragColor;

vec3 accel(vec3 x, float h2) {
    float r2 = dot(x, x);
    return -3.0 * h2 * x / (r2 * r2 * sqrt(r2));
}

float discT(float r) {
    float x = uDiscInner / r;
    return uDiscTemp * pow(x, 0.75) * pow(max(1.0 - sqrt(x), 0.0), 0.25);
}

void main() {
    vec2 p = (gl_FragCoord.xy / uResolution * 2.0 - 1.0) * vec2(uResolution.x / uResolution.y, 1.0) * uTanHalfFov;
    vec3 v = normalize(uCamBasis * vec3(p, -1.0));
    vec3 x = uCamPos;
    float h2 = dot(cross(x, v), cross(x, v));
    vec3 col = vec3(0.0);
    float trans = 1.0;
    bool captured = false;
    float rEscape = max(length(uCamPos) * 1.05, uDiscOuter * 1.2);
    float prevSide = dot(x, uDiscNormal);
    for (int i = 0; i < uMaxSteps; i++) {
        float r = length(x);
        if (r < 2.0) { captured = true; break; }
        if (r > rEscape && dot(x, v) > 0.0) break;
        // step: small near the horizon/photon sphere, large far away
        float dt = clamp(0.08 * (r - 1.5) * (r - 1.5), 0.01, 0.6 + 0.08 * r);
        vec3 k1x = v,                    k1v = accel(x, h2);
        vec3 k2x = v + 0.5 * dt * k1v,   k2v = accel(x + 0.5 * dt * k1x, h2);
        vec3 k3x = v + 0.5 * dt * k2v,   k3v = accel(x + 0.5 * dt * k2x, h2);
        vec3 k4x = v + dt * k3v,         k4v = accel(x + dt * k3x, h2);
        vec3 xn = x + dt / 6.0 * (k1x + 2.0 * k2x + 2.0 * k3x + k4x);
        vec3 vn = v + dt / 6.0 * (k1v + 2.0 * k2v + 2.0 * k3v + k4v);
        float side = dot(xn, uDiscNormal);
        if (uDiscOn == 1 && side * prevSide < 0.0) {
            // linear interpolation to the crossing point
            float f = prevSide / (prevSide - side);
            vec3 xc = mix(x, xn, f);
            float rc = length(xc);
            if (rc > uDiscInner && rc < uDiscOuter) {
                vec3 photonDir = -normalize(mix(v, vn, f));   // physical photon travels opposite to the traced ray
                float Lz = dot(cross(xc, photonDir), uDiscNormal);
                float r32 = pow(rc, 1.5);
                float omega = 1.0 / (r32 + uSpin);
                float denom = r32 - 3.0 * sqrt(rc) + 2.0 * uSpin;
                if (denom <= 0.0) { prevSide = side; x = xn; v = vn; continue; }
                float ut = (r32 + uSpin) / (pow(rc, 0.75) * sqrt(denom));
                float g = 1.0 / (ut * (1.0 - omega * Lz));
                vec3 e1 = normalize(cross(uDiscNormal, abs(uDiscNormal.z) < 0.9 ? vec3(0, 0, 1) : vec3(1, 0, 0)));
                float phi = atan(dot(xc, cross(uDiscNormal, e1)), dot(xc, e1));
                float T = discT(rc) * pow(discPattern(rc, phi), 0.25);
                float Tobs = g * T;
                // bolometric beaming g^4, normalised so the inner disc at g=1 is ~uDiscBrightness
                float I = uDiscBrightness * pow(g, 4.0) * pow(T / uDiscTemp, 4.0) * 40.0 * discPattern(rc, phi + 1.7);
                // soft outer edge and slight optical-depth falloff -> disc is opaque near the hole, thinner outside
                float edge = smoothstep(uDiscOuter, uDiscOuter * 0.75, rc);
                float alpha = edge * mix(1.0, 0.65, smoothstep(uDiscInner * 2.0, uDiscOuter, rc));
                col += trans * alpha * I * blackbody(max(Tobs, 1700.0));
                trans *= 1.0 - alpha;
                if (trans < 0.01) break;
            }
        }
        prevSide = side;
        x = xn; v = vn;
    }
    if (!captured) {
        vec3 d = normalize(uBhToIcrs * v);
        col += trans * uSkyGain * texture(uSky, equirectUV(d)).rgb;
    }
    fragColor = vec4(col, 1.0);
}
