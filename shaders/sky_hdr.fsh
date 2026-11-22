#version 330 core
// Background sky (HDR pass): equirect diffuse Milky Way map sampled along the view ray, linear output.
#include "common.glsl"
uniform sampler2D uSky;
uniform mat3 uCamToIcrs;
uniform float uTanHalfFov;
uniform vec2 uResolution;
uniform float uGain;
out vec4 fragColor;
void main() {
    vec2 p = (gl_FragCoord.xy / uResolution * 2.0 - 1.0) * vec2(uResolution.x / uResolution.y, 1.0) * uTanHalfFov;
    vec3 rd = normalize(uCamToIcrs * vec3(p, -1.0));
    fragColor = vec4(texture(uSky, equirectUV(rd)).rgb * uGain, 1.0);
}
