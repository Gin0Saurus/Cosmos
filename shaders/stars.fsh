#version 330 core
// Star sprite: Gaussian core plus a faint wide halo for very bright stars, additive HDR output.
in vec3 vColor;
in float vIntensity;
in float vRadius;
out vec4 fragColor;
void main() {
    vec2 d = (gl_PointCoord - 0.5) * (2.0 * vRadius + 2.0);
    float r2 = dot(d, d);
    float core = exp(-r2 / (0.5 * vRadius * vRadius));
    float halo = 0.04 * exp(-sqrt(r2) / max(vRadius * 0.9, 1.0)) * smoothstep(1.0, 20.0, vIntensity * vRadius * vRadius);
    fragColor = vec4(vColor * vIntensity * (core + halo), 1.0);
}
