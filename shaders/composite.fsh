#version 330 core
// Final HDR composite: scene HDR buffer -> bloom-ish glare (cheap 2-ring blur) -> ACES tonemap -> sRGB.
#include "common.glsl"
uniform sampler2D uHdr;
uniform vec2 uResolution;
uniform float uExposure;
uniform float uBloom;
out vec4 fragColor;
void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    vec3 c = texture(uHdr, uv).rgb;
    // Bloom from the HDR mip chain: each level is a progressively wider box-filtered copy; energy above 1.0 spills.
    vec3 glow = vec3(0.0);
    float weights[6] = float[](0.0, 0.5, 0.35, 0.25, 0.18, 0.12);
    for (int l = 1; l < 6; l++) glow += max(textureLod(uHdr, uv, float(l)).rgb - 0.6, 0.0) * weights[l];
    c += uBloom * glow;
    fragColor = vec4(linearToSrgb(tonemapACES(c * uExposure)), 1.0);
}
