#version 330 core
// Instanced/point-sprite star renderer. One vertex per star: ICRS unit direction from the observer, apparent
// magnitude from the observer's position, and linear sRGB colour from the star's temperature.
// The sprite size and intensity follow a photometric model: flux F = 10^(-0.4 (m - mLimit)); faint stars are 1-px
// points dimmed by F, bright stars saturate and grow (like the eye / a camera point spread function).
in vec3 aDir;
in float aMag;
in vec3 aColor;
uniform mat3 uIcrsToCam;
uniform float uTanHalfFov;
uniform vec2 uResolution;
uniform float uMagLimit;     // faintest magnitude drawn at full point intensity
uniform float uPixelScale;   // sprite size multiplier (graphics setting / DPI)
out vec3 vColor;
out float vIntensity;
out float vRadius;

void main() {
    vec3 c = uIcrsToCam * aDir;           // camera space: looks down -z
    if (c.z >= 0.0) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); gl_PointSize = 0.0; return; }
    vec2 ndc = c.xy / (-c.z * uTanHalfFov);
    ndc.x /= uResolution.x / uResolution.y;
    float F = pow(10.0, -0.4 * (aMag - uMagLimit));
    float r = clamp(0.75 * pow(F, 0.22), 0.75, 9.0) * uPixelScale;
    vRadius = r;
    vIntensity = F / (r * r * 1.4);
    vColor = aColor;
    gl_PointSize = 2.0 * r + 2.0;
    gl_Position = vec4(ndc, 0.0, 1.0);
}
