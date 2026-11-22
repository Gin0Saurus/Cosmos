package dev.saurus.cosmos.core.coords;

/** Physical constants and unit conversions. All engine-internal lengths are metres, times are seconds. */
public final class Units {
    private Units() {}
    /** IAU 2012 Resolution B2: exact. */
    public static final double AU = 149_597_870_700.0;
    /** Speed of light, exact (SI). */
    public static final double C = 299_792_458.0;
    /** Julian year (exact, IAU). */
    public static final double JULIAN_YEAR_S = 365.25 * 86400.0;
    public static final double LIGHT_YEAR = C * JULIAN_YEAR_S;
    /** IAU 2015 Resolution B2: parsec = 648000/pi au (exact). */
    public static final double PARSEC = 648000.0 / Math.PI * AU;
    public static final double KPC = 1e3 * PARSEC, MPC = 1e6 * PARSEC;
    public static final double KM = 1000.0;
    /** CODATA 2018 Newtonian constant of gravitation. */
    public static final double G = 6.67430e-11;
    /** IAU 2015 B3 nominal solar mass parameter (m^3 s^-2). */
    public static final double GM_SUN = 1.3271244e20;
    public static final double M_SUN = GM_SUN / G;
    public static final double R_SUN = 695_700_000.0;
    public static final double L_SUN_W = 3.828e26; // IAU 2015 B3 nominal solar luminosity
    public static final double JUPITER_MASS = 1898.125e24, JUPITER_RADIUS = 71_492_000.0;
    public static final double EARTH_MASS = 5.97217e24, EARTH_RADIUS = 6_378_136.6;
    public static final double DEG = Math.PI / 180.0;

    public static String formatDistance(double m) {
        double a = Math.abs(m);
        if (a < 1e4) return String.format("%.1f m", m);
        if (a < 0.01 * AU) return String.format("%,.0f km", m / KM);
        if (a < 0.1 * LIGHT_YEAR) return String.format("%.4f AU", m / AU);
        if (a < 1e6 * LIGHT_YEAR) return String.format("%,.3f ly", m / LIGHT_YEAR);
        if (a < 1e9 * LIGHT_YEAR) return String.format("%,.3f Mly", m / LIGHT_YEAR / 1e6);
        return String.format("%,.3f Gly", m / LIGHT_YEAR / 1e9);
    }
}
