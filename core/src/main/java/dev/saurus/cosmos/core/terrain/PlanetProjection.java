package dev.saurus.cosmos.core.terrain;

/**
 * Maps a planet's surface onto Minecraft's flat X/Z grid (equirectangular, see docs/SCALE.md).
 * <ul>
 *   <li>X is longitude: x = lon / 2pi * C. The world is periodic in X with period C, so walking east long enough brings
 *       you back to where you started (the server shifts you by C when you pass |x| = C/2; the terrain on both sides is
 *       identical).</li>
 *   <li>Z is latitude, north = -Z: z = -lat / 2pi * C, poles at z = -/+ C/4. Past a pole the terrain continues as the far
 *       side of the globe (lon + 180), and the server mirrors you onto it.</li>
 * </ul>
 * Engine limitation: Minecraft's grid is flat, so east-west distances are stretched by 1/cos(lat) away from the
 * equator (x1.4 at 45 deg, x2 at 60 deg). Terrain stays continuous; only the map projection is distorted.
 */
public record PlanetProjection(int circumference) {
    public static PlanetProjection forRadius(double radiusM) { return new PlanetProjection(SurfaceScale.circumferenceBlocks(radiusM)); }

    public int halfC() { return circumference / 2; }
    public int quarterC() { return circumference / 4; }

    /** Geographic latitude/longitude (radians) of block column (x, z); valid for any x, z (wrap + pole mirror). */
    public double[] latLon(double x, double z) {
        double C = circumference;
        double lat = -z / C * 2 * Math.PI;
        double lon = x / C * 2 * Math.PI;
        // fold latitude into [-pi/2, pi/2]; each fold over a pole adds 180 deg of longitude
        lat = Math.IEEEremainder(lat, 2 * Math.PI);
        if (lat > Math.PI / 2) { lat = Math.PI - lat; lon += Math.PI; }
        else if (lat < -Math.PI / 2) { lat = -Math.PI - lat; lon += Math.PI; }
        lon = Math.IEEEremainder(lon, 2 * Math.PI);
        return new double[] { lat, lon };
    }

    public double x(double lonRad) { return Math.IEEEremainder(lonRad, 2 * Math.PI) / (2 * Math.PI) * circumference; }
    public double z(double latRad) { return -latRad / (2 * Math.PI) * circumference; }

    /** Where a player at (x, z) should be moved so they stay inside the canonical band, or null if they already are. */
    public double[] canonicalise(double x, double z, float yaw) {
        double C = circumference, nx = x, nz = z; float nyaw = yaw; boolean moved = false;
        if (nz < -C / 4) { nz = -C / 2 - nz; nx += C / 2; nyaw += 180; moved = true; }
        else if (nz > C / 4) { nz = C / 2 - nz; nx += C / 2; nyaw += 180; moved = true; }
        if (nx >= C / 2) { nx -= C; moved = true; }
        else if (nx < -C / 2) { nx += C; moved = true; }
        return moved ? new double[] { nx, nz, nyaw } : null;
    }

    /** Unit vector in the body-fixed frame (x toward lon 0, z toward north pole). */
    public static double[] unit(double lat, double lon) {
        double cl = Math.cos(lat);
        return new double[] { cl * Math.cos(lon), cl * Math.sin(lon), Math.sin(lat) };
    }
}
