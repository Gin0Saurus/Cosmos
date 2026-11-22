#version 330 core
// Background sky: samples the HDR equirect star/Milky Way map along each view ray.
#include "common.glsl"
uniform sampler2D uSky;
uniform mat3 uCamToIcrs;      // camera basis (columns: right, up, -forward) in ICRS
uniform float uTanHalfFov;
uniform vec2 uResolution;
uniform float uExposure;
in vec2 vUv;
out vec4 fragColor;

void main() {
    vec2 p = (gl_FragCoord.xy / uResolution * 2.0 - 1.0) * vec2(uResolution.x / uResolution.y, 1.0) * uTanHalfFov;
    vec3 rd = normalize(uCamToIcrs * vec3(p, -1.0));
    vec3 c = texture(uSky, equirectUV(rd)).rgb * uExposure;
    fragColor = vec4(linearToSrgb(tonemapACES(c)), 1.0);
}
