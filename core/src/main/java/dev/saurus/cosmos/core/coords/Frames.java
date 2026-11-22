package dev.saurus.cosmos.core.coords;

import dev.saurus.cosmos.core.math.Mat3;
import dev.saurus.cosmos.core.math.Vec3d;

/** Fixed rotations between the astronomical reference frames the engine uses. */
public final class Frames {
    private Frames() {}

    /** Obliquity of the ecliptic at J2000 as given with the JPL approximate-position elements. */
    public static final double OBLIQUITY_J2000 = 23.43928 * Units.DEG;

    /** Ecliptic J2000 -> ICRS equatorial. */
    public static final Mat3 ECLIPTIC_TO_EQUATORIAL = Mat3.rotX(OBLIQUITY_J2000);
    public static final Mat3 EQUATORIAL_TO_ECLIPTIC = ECLIPTIC_TO_EQUATORIAL.transpose();

    /**
     * ICRS equatorial -> Galactic (l, b) rotation, as tabulated in the Hipparcos Catalogue (ESA 1997, Vol. 1, Sec. 1.5.3).
     * Rows are the Galactic x (towards the Galactic centre), y (towards l=90), z (North Galactic Pole) axes.
     */
    public static final Mat3 EQUATORIAL_TO_GALACTIC = new Mat3(
            -0.0548755604, -0.8734370902, -0.4838350155,
             0.4941094279, -0.4448296300,  0.7469822445,
            -0.8676661490, -0.1980763734,  0.4559837762);
    public static final Mat3 GALACTIC_TO_EQUATORIAL = EQUATORIAL_TO_GALACTIC.transpose();

    /**
     * Orientation matrix of a body from its IAU pole and prime meridian:
     * columns are the body-fixed x (prime meridian on the equator), y, z (north pole) axes expressed in ICRS.
     */
    public static Mat3 bodyFixed(double poleRa, double poleDec, double w) {
        // Standard IAU construction: R = Rz(alpha0 + 90deg) * Rx(90deg - delta0) * Rz(W)
        return Mat3.rotZ(poleRa + Math.PI / 2).mul(Mat3.rotX(Math.PI / 2 - poleDec)).mul(Mat3.rotZ(w));
    }

    public static Vec3d pole(double ra, double dec) { return Vec3d.fromRaDec(ra, dec); }
}
