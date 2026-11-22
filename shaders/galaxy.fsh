#version 330 core
// Volumetric galaxy (far LOD of the Milky Way, and of catalogued galaxies like M31 with scaled parameters).
// Ray-marches the same stellar density model as MilkyWayModel.java (thin + thick disc, bar/bulge, 4 log-spiral arms,
// halo) with clumpy dust extinction and young-star (HII / OB association) emission in the arms. Units: parsecs,
// galactocentric axes. Structural parameters are literature-based estimates; arm geometry, dust clumping and
// star-forming knots are PROCEDURAL. When the camera is inside the disc, the engine switches to resolved stars
// (catalogue + ProceduralStars) and this pass only supplies the unresolved glow.
#include "common.glsl"
uniform vec2 uResolution;
uniform vec3 uCamPos;      // pc, galactocentric
uniform mat3 uCamBasis;
uniform float uTanHalfFov;
uniform float uScale;      // size multiplier relative to the Milky Way (M31 ~ 1.5)
uniform float uBarAngle;
uniform float uSeed;
out vec4 fragColor;

const float R0 = 8277.0, ZSUN = 20.8;
float hash13(vec3 p) { p = fract(p * 0.1031); p += dot(p, p.zyx + 31.32); return fract((p.x + p.y) * p.z); }
float vnoise3(vec3 p) {
    vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i), n100 = hash13(i + vec3(1, 0, 0)), n010 = hash13(i + vec3(0, 1, 0)), n110 = hash13(i + vec3(1, 1, 0));
    float n001 = hash13(i + vec3(0, 0, 1)), n101 = hash13(i + vec3(1, 0, 1)), n011 = hash13(i + vec3(0, 1, 1)), n111 = hash13(i + vec3(1, 1, 1));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y), mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}
float fbm3(vec3 p) { float s = 0.0, a = 0.5; for (int i = 0; i < 4; i++) { s += a * vnoise3(p); p *= 2.03; a *= 0.5; } return s; }

float armPhase(vec3 p) {
    float R = length(p.xy);
    return 4.0 * (atan(p.y, p.x) - log(max(R, 1.0) / 3000.0) / tan(radians(12.0)));
}

// returns (stellar density, dust density, young-star emission)
vec3 model(vec3 p) {
    p /= uScale;
    float R = length(p.xy), az = abs(p.z);
    float arm = 1.0 + 0.35 * clamp((R - 3000.0) / 2000.0, 0.0, 1.0) * cos(armPhase(p));
    float armSharp = pow(max(cos(armPhase(p)), 0.0), 6.0) * clamp((R - 3000.0) / 2000.0, 0.0, 1.0);
    float norm = exp(-ZSUN / 300.0) + 0.12 * exp(-ZSUN / 900.0);
    float disk = (exp(-(R - R0) / 2600.0 - az / 300.0) * arm + 0.12 * exp(-(R - R0) / 2000.0 - az / 900.0)) / norm;
    disk *= R > 25000.0 ? exp(-(R - 25000.0) / 1500.0) : 1.0;
    float c = cos(uBarAngle), s = sin(uBarAngle);
    vec2 b = vec2(p.x * c + p.y * s, -p.x * s + p.y * c);
    float rb = length(vec3(b.x, b.y / 0.4, p.z / 0.3));
    float bulge = 120.0 * exp(-pow(rb / 750.0, 1.6));
    float dust = 0.0, young = 0.0;
    if (az < 600.0) {
        float n = fbm3(p / 400.0 + uSeed);
        dust = 0.9e-3 * exp(-(R - R0) / 3000.0 - az / 100.0) * (0.15 + 2.2 * armSharp) * (0.1 + 2.5 * n * n);
        float knots = smoothstep(0.45, 0.75, fbm3(p / 220.0 + 17.0 + uSeed));
        young = armSharp * (0.35 + knots) * exp(-az / 90.0) * exp(-(R - R0) / 5000.0) * 6.0;
    }
    return vec3(disk + bulge, dust, young);
}

void main() {
    vec2 sp = (gl_FragCoord.xy / uResolution * 2.0 - 1.0) * vec2(uResolution.x / uResolution.y, 1.0) * uTanHalfFov;
    vec3 rd = normalize(uCamBasis * vec3(sp, -1.0));
    vec3 ro = uCamPos;
    // bounding slab/cylinder of the galaxy
    float H = 4000.0 * uScale, Rmax = 30000.0 * uScale;
    float t0 = 0.0, t1 = 1e9;
    if (abs(rd.z) > 1e-6) { float ta = (-H - ro.z) / rd.z, tb = (H - ro.z) / rd.z; t0 = max(t0, min(ta, tb)); t1 = min(t1, max(ta, tb)); }
    else if (abs(ro.z) > H) t1 = -1.0;
    vec2 cyl = raySphere(ro, rd, Rmax * 1.2);
    t0 = max(t0, cyl.x); t1 = min(t1, cyl.y);
    vec3 col = vec3(0.0);
    float tau = 0.0;
    if (t1 > t0) {
        const int N = 160;
        float jitter = hash13(vec3(gl_FragCoord.xy, 3.0));
        float dt = (t1 - t0) / float(N);
        for (int i = 0; i < N; i++) {
            vec3 p = ro + rd * (t0 + dt * (float(i) + jitter));
            vec3 m = model(p);
            float R = length(p.xy) / uScale;
            float bulgeFrac = clamp(exp(-R / 2500.0), 0.0, 1.0);
            vec3 popColor = mix(vec3(0.75, 0.82, 1.0), vec3(1.0, 0.82, 0.6), bulgeFrac); // young disc bluish, old bulge yellow
            float att = exp(-tau);
            // old-star light + young OB light (blue) + HII emission (H-alpha pink) in the arms
            col += att * dt * (m.x * popColor + m.z * vec3(0.45, 0.62, 1.0) + m.z * 0.35 * vec3(1.0, 0.3, 0.45));
            tau += m.y * dt * 1.6;
            if (tau > 12.0) break;
        }
    }
    fragColor = vec4(col * 6e-5, 1.0);
}
