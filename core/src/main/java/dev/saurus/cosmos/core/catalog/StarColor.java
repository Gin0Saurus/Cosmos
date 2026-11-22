package dev.saurus.cosmos.core.catalog;

/**
 * Physically based star colours: B-V -> effective temperature -> blackbody spectrum -> CIE 1931 XYZ -> linear sRGB.
 */
public final class StarColor {
    private StarColor() {}

    /** Ballesteros (2012, EPL 97, 34001) B-V to Teff relation for blackbody-like stars. */
    public static double teffFromBV(double bv) {
        bv = Math.max(-0.4, Math.min(2.0, bv));
        return 4600.0 * (1.0 / (0.92 * bv + 1.7) + 1.0 / (0.92 * bv + 0.62));
    }

    /**
     * CIE 1931 2-degree colour matching functions, multi-lobe Gaussian fit of Wyman, Sloan & Shirley (2013, JCGT 2(2)).
     * Wavelength in nm.
     */
    static double xBar(double l) {
        double t1 = (l - 442.0) * (l < 442.0 ? 0.0624 : 0.0374);
        double t2 = (l - 599.8) * (l < 599.8 ? 0.0264 : 0.0323);
        double t3 = (l - 501.1) * (l < 501.1 ? 0.0490 : 0.0382);
        return 0.362 * Math.exp(-0.5 * t1 * t1) + 1.056 * Math.exp(-0.5 * t2 * t2) - 0.065 * Math.exp(-0.5 * t3 * t3);
    }
    static double yBar(double l) {
        double t1 = (l - 568.8) * (l < 568.8 ? 0.0213 : 0.0247);
        double t2 = (l - 530.9) * (l < 530.9 ? 0.0613 : 0.0322);
        return 0.821 * Math.exp(-0.5 * t1 * t1) + 0.286 * Math.exp(-0.5 * t2 * t2);
    }
    static double zBar(double l) {
        double t1 = (l - 437.0) * (l < 437.0 ? 0.0845 : 0.0278);
        double t2 = (l - 459.0) * (l < 459.0 ? 0.0385 : 0.0725);
        return 1.217 * Math.exp(-0.5 * t1 * t1) + 0.681 * Math.exp(-0.5 * t2 * t2);
    }

    /** Planck spectral radiance (arbitrary scale) at wavelength nm and temperature K. */
    static double planck(double nm, double T) {
        double l = nm * 1e-9;
        return 1.0 / (Math.pow(l, 5) * (Math.exp(1.438776877e-2 / (l * T)) - 1.0));
    }

    /** Linear sRGB chromaticity of a blackbody, normalised so the largest channel is 1. */
    public static float[] rgbFromTemperature(double T) {
        double X = 0, Y = 0, Z = 0;
        for (double l = 380; l <= 780; l += 5) {
            double p = planck(l, T);
            X += p * xBar(l); Y += p * yBar(l); Z += p * zBar(l);
        }
        // XYZ -> linear sRGB (IEC 61966-2-1, D65)
        double r = 3.2406 * X - 1.5372 * Y - 0.4986 * Z;
        double g = -0.9689 * X + 1.8758 * Y + 0.0415 * Z;
        double b = 0.0557 * X - 0.2040 * Y + 1.0570 * Z;
        r = Math.max(r, 0); g = Math.max(g, 0); b = Math.max(b, 0);
        double m = Math.max(r, Math.max(g, b));
        return new float[]{(float) (r / m), (float) (g / m), (float) (b / m)};
    }

    /** Stefan-Boltzmann radius estimate in solar radii (nominal Teff_sun = 5772 K, IAU 2015 B3). */
    public static double radiusFromLT(double lumSun, double teff) {
        double tr = teff / 5772.0;
        return Math.sqrt(lumSun) / (tr * tr);
    }
}
