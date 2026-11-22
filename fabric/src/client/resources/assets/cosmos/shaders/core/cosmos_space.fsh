#version 330
#extension GL_ARB_separate_shader_objects : require

// Cosmos space view: everything except point stars, in one fullscreen pass.
//  - unresolved Milky Way light (equirect sky map computed from the galaxy model)
//  - planets / moons / stars as true-scale spheres (camera-relative km, cancellation-free intersection)
//  - Rayleigh + Mie single-scattering atmospheres, Saturn-style rings
//  - volumetric galaxy (Milky Way or a catalogued galaxy) when seen from outside
//  - black hole: Schwarzschild null geodesics (x'' = -3 M h^2 x / r^5), lensed background, Kerr-kinematics disc
// Output is premultiplied: rgb added on top of the star sprites already drawn, alpha = how much of them is hidden.

layout(std140) uniform CosmosSpace {
    mat4 CamToWorld;      // columns: right, up, -forward (ICRS)
    vec4 Screen;          // width, height, tanHalfFov, exposure
    vec4 Params;          // bodyCount, time(s), skyGain, ringBody
    vec4 BH;              // camera position relative to the hole, units of M (ICRS); w = 1 if active
    vec4 BHParams;        // spin, disc on (0/1), disc temperature scale (K), disc outer radius (M)
    vec4 Galaxy;          // camera position relative to galaxy centre in kpc, galaxy frame; w = 1 if active
                          // (WorldToGalaxy doubles as the black-hole frame, disc in its xy plane, when BH.w = 1)
    vec4 GalaxyParams;    // scale (1 = Milky Way), bar angle, brightness, unused
    mat4 WorldToGalaxy;   // rotation ICRS -> galaxy frame
    vec4 RingA;           // ring plane normal (ICRS), w = inner radius (km)
    vec4 RingB;           // outer radius (km), body index, unused, unused
    vec4 BodyPos[16];     // camera-relative centre (km), w = radius (km)
    vec4 BodyCol[16];     // albedo / emission rgb, w = type: 0 rocky, 1 gas giant, 2 star, 3 ice giant
    vec4 BodyAtm[16];     // Rayleigh coefficients per km (rgb), w = Rayleigh scale height (km); w = 0 -> airless
    vec4 BodyAtm2[16];    // Mie scattering per km, Mie g, atmosphere thickness (km), star luminosity factor
    vec4 BodyLight[16];   // unit direction to the light source, w = light intensity
};

uniform sampler2D SkySampler;

layout(location = 0) out vec4 fragColor;

const float PI = 3.14159265358979;

vec2 equirectUV(vec3 d) {
    float ra = atan(d.y, d.x), dec = asin(clamp(d.z, -1.0, 1.0));
    return vec2((PI - ra) / (2.0 * PI), (PI / 2.0 - dec) / PI);
}

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

float hash13(vec3 p) { p = fract(p * 0.1031); p += dot(p, p.zyx + 31.32); return fract((p.x + p.y) * p.z); }
float vnoise3(vec3 p) {
    vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x), mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x), mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}
float fbm3(vec3 p) { float s = 0.0, a = 0.5; for (int i = 0; i < 4; i++) { s += a * vnoise3(p); p *= 2.03; a *= 0.5; } return s; }

// ---------------------------------------------------------------- galaxy (same model as MilkyWayModel.java)
vec3 galaxyModel(vec3 p) { // p in pc, galaxy frame
    float sc = GalaxyParams.x;
    p /= sc;
    float R = length(p.xy), az = abs(p.z);
    float ph = 4.0 * (atan(p.y, p.x) - log(max(R, 1.0) / 3000.0) / tan(radians(12.0)));
    float fade = clamp((R - 3000.0) / 2000.0, 0.0, 1.0);
    float arm = 1.0 + 0.35 * fade * cos(ph);
    float armSharp = pow(max(cos(ph), 0.0), 6.0) * fade;
    float disk = exp(-(R - 8277.0) / 2600.0 - az / 300.0) * arm + 0.12 * exp(-(R - 8277.0) / 2000.0 - az / 900.0);
    disk *= R > 25000.0 ? exp(-(R - 25000.0) / 1500.0) : 1.0;
    float c = cos(GalaxyParams.y), s = sin(GalaxyParams.y);
    vec2 b = vec2(p.x * c + p.y * s, -p.x * s + p.y * c);
    float rb = length(vec3(b.x, b.y / 0.4, p.z / 0.3));
    float bulge = 120.0 * exp(-pow(rb / 750.0, 1.6));
    float dust = 0.0, young = 0.0;
    if (az < 600.0) {
        float n = fbm3(p / 400.0);
        dust = 0.9e-3 * exp(-(R - 8277.0) / 3000.0 - az / 100.0) * (0.15 + 2.2 * armSharp) * (0.1 + 2.5 * n * n);
        float knots = smoothstep(0.45, 0.75, fbm3(p / 220.0 + 17.0));
        young = armSharp * (0.35 + knots) * exp(-az / 90.0) * exp(-(R - 8277.0) / 5000.0) * 6.0;
    }
    return vec3(disk + bulge, dust, young);
}

vec3 renderGalaxy(vec3 rdWorld) {
    vec3 rd = normalize(mat3(WorldToGalaxy) * rdWorld);
    vec3 ro = Galaxy.xyz * 1000.0; // pc
    float sc = GalaxyParams.x, H = 4000.0 * sc, Rmax = 36000.0 * sc;
    float t0 = 0.0, t1 = 1e12;
    if (abs(rd.z) > 1e-6) { float ta = (-H - ro.z) / rd.z, tb = (H - ro.z) / rd.z; t0 = max(t0, min(ta, tb)); t1 = min(t1, max(ta, tb)); }
    float bq = dot(ro, rd), cq = dot(ro, ro) - Rmax * Rmax, hq = bq * bq - cq;
    if (hq < 0.0) return vec3(0.0);
    hq = sqrt(hq);
    t0 = max(t0, -bq - hq); t1 = min(t1, -bq + hq);
    if (t1 <= t0) return vec3(0.0);
    vec3 col = vec3(0.0); float tau = 0.0;
    const int N = 72;
    float dt = (t1 - t0) / float(N);
    float jitter = hash13(vec3(gl_FragCoord.xy, 3.0));
    for (int i = 0; i < N; i++) {
        vec3 p = ro + rd * (t0 + dt * (float(i) + jitter));
        vec3 m = galaxyModel(p);
        float bf = clamp(exp(-length(p.xy) / sc / 2500.0), 0.0, 1.0);
        vec3 pop = mix(vec3(0.75, 0.82, 1.0), vec3(1.0, 0.82, 0.6), bf);
        col += exp(-tau) * dt * (m.x * pop + m.z * vec3(0.45, 0.62, 1.0) + m.z * 0.35 * vec3(1.0, 0.3, 0.45));
        tau += m.y * dt * 1.6;
        if (tau > 12.0) break;
    }
    return col * 6e-5 / sc * GalaxyParams.z;
}

// ---------------------------------------------------------------- planets
// distance along rd to the sphere's near intersection, or -1. Uses a cross product so huge distances do not cancel.
float hitSphere(vec3 c, float r, vec3 rd, out float tFar) {
    float tca = dot(c, rd);
    vec3 perp = cross(c, rd);
    float d2 = dot(perp, perp), h = r * r - d2;
    tFar = -1.0;
    if (h < 0.0 || tca + sqrt(max(h, 0.0)) < 0.0) return -1.0;
    h = sqrt(h);
    tFar = tca + h;
    return max(tca - h, 0.0);
}

float phaseR(float mu) { return 3.0 / (16.0 * PI) * (1.0 + mu * mu); }
float phaseM(float mu, float g) { return 3.0 / (8.0 * PI) * (1.0 - g * g) * (1.0 + mu * mu) / ((2.0 + g * g) * pow(1.0 + g * g - 2.0 * g * mu, 1.5)); }

// single scattering along [t0,t1] through body i's atmosphere; returns inscatter, writes transmittance
vec3 atmosphere(int i, vec3 rd, float t0, float t1, out vec3 trans) {
    vec3 c = BodyPos[i].xyz; float R = BodyPos[i].w;
    vec3 beta = BodyAtm[i].rgb; float Hr = BodyAtm[i].w;
    float mie = BodyAtm2[i].x, g = BodyAtm2[i].y, top = BodyAtm2[i].z;
    vec3 L = BodyLight[i].xyz; float sunI = BodyLight[i].w;
    float mu = dot(rd, L);
    const int VS = 12, LS = 6;
    float ds = (t1 - t0) / float(VS);
    vec3 od = vec3(0.0), sum = vec3(0.0);
    for (int k = 0; k < VS; k++) {
        vec3 q = rd * (t0 + ds * (float(k) + 0.5)) - c;
        float r = length(q), h = r - R;
        float dr = exp(-h / Hr), dm = exp(-h / max(Hr * 0.15, 0.5));
        vec3 ext = beta * dr + vec3(mie * 1.1) * dm;
        od += ext * ds * 0.5;
        // light ray to the sun
        float tf; float th = hitSphere(-q, R, L, tf);
        vec3 odL = vec3(0.0);
        if (th > 0.0 && tf > 0.0) odL = vec3(1e9);
        else {
            float b = dot(q, L), cc = dot(q, q) - (R + top) * (R + top);
            float tTop = -b + sqrt(max(b * b - cc, 0.0));
            float dl = tTop / float(LS);
            for (int j = 0; j < LS; j++) {
                float hl = length(q + L * dl * (float(j) + 0.5)) - R;
                odL += (beta * exp(-hl / Hr) + vec3(mie * 1.1) * exp(-hl / max(Hr * 0.15, 0.5))) * dl;
            }
        }
        sum += exp(-(od + odL)) * (beta * dr * phaseR(mu) + vec3(mie) * dm * phaseM(mu, g)) * ds;
        od += ext * ds * 0.5;
    }
    trans = exp(-od);
    return sum * sunI;
}

vec3 shadeSurface(int i, vec3 n, vec3 rd) {
    vec3 L = BodyLight[i].xyz; float sunI = BodyLight[i].w;
    float type = BodyCol[i].w;
    vec3 alb = BodyCol[i].rgb;
    if (type > 0.5 && type < 1.5) { // gas giant: latitude bands (procedural approximation)
        float lat = n.z;
        float band = 0.82 + 0.18 * sin(lat * 38.0 + 3.0 * vnoise3(n * 6.0)) * (0.7 + 0.3 * vnoise3(n * 20.0));
        alb *= band;
    } else if (type > 2.5) {
        alb *= 0.95 + 0.05 * sin(n.z * 20.0);
    } else {
        alb *= 0.85 + 0.3 * (fbm3(n * 8.0) - 0.5); // albedo variation (procedural)
    }
    float ndl = dot(n, L);
    return alb / PI * max(ndl, 0.0) * sunI;
}

// ---------------------------------------------------------------- black hole
vec3 bhAccel(vec3 x, float h2) { float r2 = dot(x, x); return -3.0 * h2 * x / (r2 * r2 * sqrt(r2)); }

// traces from the camera; returns colour; sets covered = 1 where the hole visibly bends light (hides point stars)
vec3 renderBlackHole(vec3 rdWorld, out float covered) {
    mat3 toBh = mat3(WorldToGalaxy);
    vec3 rd = normalize(toBh * rdWorld);
    vec3 x = BH.xyz, v = rd;
    float h2 = dot(cross(x, v), cross(x, v));
    float spin = BHParams.x, discOn = BHParams.y, Tdisc = BHParams.z, rOut = BHParams.w;
    float z1 = 1.0 + pow(1.0 - spin * spin, 1.0 / 3.0) * (pow(1.0 + spin, 1.0 / 3.0) + pow(1.0 - spin, 1.0 / 3.0));
    float z2 = sqrt(3.0 * spin * spin + z1 * z1);
    float rIn = 3.0 + z2 - sqrt((3.0 - z1) * (3.0 + z1 + 2.0 * z2));
    vec3 col = vec3(0.0); float trans = 1.0;
    float rEsc = max(length(x) * 1.02, rOut * 1.3);
    bool captured = false;
    float prevSide = x.z;
    for (int i = 0; i < 360; i++) {
        float r = length(x);
        if (r < 2.0) { captured = true; break; }
        if (r > rEsc && dot(x, v) > 0.0) break;
        float dt = clamp(0.08 * (r - 1.5) * (r - 1.5), 0.01, 0.6 + 0.12 * r);
        vec3 k1x = v, k1v = bhAccel(x, h2);
        vec3 k2x = v + 0.5 * dt * k1v, k2v = bhAccel(x + 0.5 * dt * k1x, h2);
        vec3 k3x = v + 0.5 * dt * k2v, k3v = bhAccel(x + 0.5 * dt * k2x, h2);
        vec3 k4x = v + dt * k3v, k4v = bhAccel(x + dt * k3x, h2);
        vec3 xn = x + dt / 6.0 * (k1x + 2.0 * k2x + 2.0 * k3x + k4x);
        vec3 vn = v + dt / 6.0 * (k1v + 2.0 * k2v + 2.0 * k3v + k4v);
        if (discOn > 0.5 && xn.z * prevSide < 0.0) {
            float f = prevSide / (prevSide - xn.z);
            vec3 xc = mix(x, xn, f);
            float rc = length(xc);
            if (rc > rIn && rc < rOut) {
                vec3 pd = -normalize(mix(v, vn, f));
                float Lz = cross(xc, pd).z;
                float r32 = pow(rc, 1.5), om = 1.0 / (r32 + spin), den = r32 - 3.0 * sqrt(rc) + 2.0 * spin;
                if (den > 0.0) {
                    float ut = (r32 + spin) / (pow(rc, 0.75) * sqrt(den));
                    float gfac = 1.0 / (ut * (1.0 - om * Lz));
                    float xr = rIn / rc;
                    float T = Tdisc * pow(xr, 0.75) * pow(max(1.0 - sqrt(xr), 0.0), 0.25);
                    float phi = atan(xc.y, xc.x);
                    float lr = log(rc);
                    float pat = 0.55 + 0.9 * vnoise3(vec3(cos(phi + lr * 0.7) * (4.0 + lr * 6.0) + lr * 9.0, sin(phi + lr * 0.7) * (4.0 + lr * 6.0), 1.0));
                    float I = pow(gfac, 4.0) * pow(T / Tdisc, 4.0) * 40.0 * pat;
                    float a = smoothstep(rOut, rOut * 0.75, rc) * mix(1.0, 0.65, smoothstep(rIn * 2.0, rOut, rc));
                    col += trans * a * I * blackbody(max(gfac * T, 1700.0));
                    trans *= 1.0 - a;
                }
            }
        }
        prevSide = xn.z;
        x = xn; v = vn;
    }
    float bend = acos(clamp(dot(normalize(v), rd), -1.0, 1.0));
    covered = captured ? 1.0 : clamp(bend * 400.0, 0.0, 1.0);
    if (!captured) col += trans * Params.z * texture(SkySampler, equirectUV(normalize(transpose(toBh) * v))).rgb;
    if (trans < 0.99) covered = 1.0;
    return col;
}

void main() {
    vec2 p = (gl_FragCoord.xy / Screen.xy * 2.0 - 1.0) * vec2(Screen.x / Screen.y, 1.0) * Screen.z;
    vec3 rd = normalize(mat3(CamToWorld) * vec3(p, -1.0));
    float pixelAngle = 2.0 * Screen.z / Screen.y;

    vec3 col = vec3(0.0);
    float alpha = 0.0;

    // background
    if (BH.w > 0.5) {
        float cov; col = renderBlackHole(rd, cov); alpha = cov;
    } else {
        col = Params.z * texture(SkySampler, equirectUV(rd)).rgb;
    }
    if (Galaxy.w > 0.5) col += renderGalaxy(rd);

    // bodies: nearest hit
    int nb = int(Params.x);
    float bestT = 1e30; int best = -1; float bestFar = 0.0;
    for (int i = 0; i < 16; i++) {
        if (i >= nb) break;
        float tf; float t = hitSphere(BodyPos[i].xyz, BodyPos[i].w, rd, tf);
        if (t >= 0.0 && t < bestT) { bestT = t; best = i; bestFar = tf; }
    }

    // rings (one ringed body), drawn where the ring plane is hit in front of the planet
    vec3 ringCol = vec3(0.0); float ringA = 0.0; float ringT = 1e30;
    int rb = int(RingB.y);
    if (rb >= 0 && rb < nb) {
        vec3 c = BodyPos[rb].xyz; vec3 nrm = RingA.xyz;
        float den = dot(rd, nrm);
        if (abs(den) > 1e-6) {
            float t = dot(c, nrm) / den;
            if (t > 0.0) {
                float r = length(rd * t - c);
                if (r > RingA.w && r < RingB.x) {
                    float x = r / BodyPos[rb].w;   // radius in planet radii (Saturn: 1 = 60,268 km)
                    float k = 60268.0 / BodyPos[rb].w;
                    float rk = r;                   // km; ring structure from NASA ring fact sheet radii
                    float tau = rk < 74658.0 ? 0.02 : rk < 91975.0 ? 0.2 : rk < 117507.0 ? 1.3 : rk < 122340.0 ? 0.06 : rk < 136780.0 ? 0.6 : 0.08;
                    tau *= 0.8 + 0.4 * vnoise3(vec3(rk / 600.0, 0.0, 0.0));
                    float a = 1.0 - exp(-tau / max(abs(den), 0.05));
                    vec3 L = BodyLight[rb].xyz;
                    // planet shadow on the rings
                    float tf2; float sh = hitSphere(c - rd * t, BodyPos[rb].w, L, tf2) >= 0.0 && tf2 > 0.0 ? 0.08 : 1.0;
                    ringCol = vec3(0.86, 0.80, 0.68) * BodyLight[rb].w * 0.25 * sh;
                    ringA = clamp(a, 0.0, 0.97);
                    ringT = t;
                }
            }
        }
    }

    if (best >= 0) {
        int i = best;
        vec3 c = BodyPos[i].xyz; float R = BodyPos[i].w;
        vec3 hp = rd * bestT - c; vec3 n = normalize(hp);
        vec3 surf;
        if (BodyCol[i].w > 1.5 && BodyCol[i].w < 2.5) {
            float mu = max(dot(n, -rd), 0.0);
            surf = BodyCol[i].rgb * BodyAtm2[i].w * (1.0 - 0.6 * (1.0 - mu)); // limb darkening
        } else {
            surf = shadeSurface(i, n, rd);
        }
        if (BodyAtm[i].w > 0.0) {
            float tf; float t0 = hitSphere(c, R + BodyAtm2[i].z, rd, tf);
            vec3 tr; vec3 ins = atmosphere(i, rd, max(t0, 0.0), bestT, tr);
            surf = surf * tr + ins;
        }
        col = surf; alpha = 1.0;
    } else {
        // atmosphere limb seen against space
        for (int i = 0; i < 16; i++) {
            if (i >= nb) break;
            if (BodyAtm[i].w <= 0.0) continue;
            float tf; float t0 = hitSphere(BodyPos[i].xyz, BodyPos[i].w + BodyAtm2[i].z, rd, tf);
            if (t0 < 0.0 && tf < 0.0) continue;
            vec3 tr; vec3 ins = atmosphere(i, rd, max(t0, 0.0), tf, tr);
            col = col * tr + ins;
            alpha = max(alpha, 1.0 - min(min(tr.r, tr.g), tr.b));
        }
    }
    if (ringA > 0.0 && ringT < bestT) { col = mix(col, ringCol, ringA); alpha = max(alpha, ringA); }

    // unresolved bodies as points + star glare
    for (int i = 0; i < 16; i++) {
        if (i >= nb) break;
        vec3 c = BodyPos[i].xyz; float dist = length(c);
        vec3 dir = c / dist;
        float ang = acos(clamp(dot(dir, rd), -1.0, 1.0));
        float angR = BodyPos[i].w / dist;
        if (BodyCol[i].w > 1.5 && BodyCol[i].w < 2.5) {
            float glare = BodyAtm2[i].w * angR * angR / (ang * ang + angR * angR * 0.5) * 0.08;
            col += BodyCol[i].rgb * min(glare, 40.0);
        } else if (angR < pixelAngle) {
            float phase = 0.5 + 0.5 * dot(-dir, BodyLight[i].xyz);
            float flux = BodyLight[i].w * dot(BodyCol[i].rgb, vec3(0.33)) * angR * angR / (pixelAngle * pixelAngle) * phase * 6.0;
            col += BodyCol[i].rgb * flux * exp(-ang * ang / (pixelAngle * pixelAngle * 1.2));
        }
    }

    // tone map (exponential) + gamma; premultiplied output
    vec3 mapped = 1.0 - exp(-col * Screen.w);
    mapped = pow(mapped, vec3(1.0 / 2.2));
    fragColor = vec4(mapped, alpha);
}
