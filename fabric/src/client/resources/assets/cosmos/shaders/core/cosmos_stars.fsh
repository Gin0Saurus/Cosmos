#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 corner;
layout(location = 1) in vec4 starColor;

layout(location = 0) out vec4 fragColor;

void main() {
    float r = starColor.a;
    float d2 = dot(corner, corner);
    float core = exp(-d2 / (0.55 * r * r));
    if (core < 0.004) discard;
    fragColor = vec4(starColor.rgb * core, 0.0);
}
