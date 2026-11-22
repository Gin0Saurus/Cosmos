#version 330
#extension GL_ARB_separate_shader_objects : require

// Real catalogue stars as screen-aligned sprites. Position = unit direction to the star (scaled into the view volume),
// UV0 = sprite corner (-1..1), Color.rgb = star colour x brightness, Color.a = sprite radius / 8 (pixels).
layout(std140) uniform CosmosStars {
    mat4 StarViewProj;   // world-direction -> clip (rotation + projection only)
    vec4 StarScreen;     // width, height, brightness multiplier, unused
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec2 corner;
layout(location = 1) out vec4 starColor;

void main() {
    vec4 clip = StarViewProj * vec4(Position * 50.0, 1.0);
    float radiusPx = max(Color.a * 8.0, 0.75) + 1.0;
    clip.xy += UV0 * radiusPx * 2.0 / StarScreen.xy * clip.w;
    gl_Position = clip;
    corner = UV0 * radiusPx;
    starColor = vec4(Color.rgb * StarScreen.z, max(Color.a * 8.0, 0.75));
}
