package dev.saurus.cosmos.core.time;

/**
 * Simulation clock. Stored as seconds of TDB since J2000.0 (JD 2451545.0 TDB) in a double:
 * resolution is ~0.1 microsecond today and ~4 ms at a million years from J2000, which is ample.
 *
 * <p>TT/TDB differ by < 2 ms (periodic); UTC = TT - 69.184 s since 2017 (TAI-UTC = 37 s) until a new leap second.
 * These are applied explicitly in {@link #fromUtc} so the sky matches real-time when the player wants it.
 */
public final class SimTime {
    public static final double J2000_JD = 2451545.0;
    public static final double DAY = 86400.0;
    public static final double JULIAN_CENTURY_DAYS = 36525.0;
    /** TT - UTC = 32.184 s + (TAI-UTC). TAI-UTC = 37 s since 2017-01-01 (IERS Bulletin C; no leap second announced since). */
    public static final double TT_MINUS_UTC = 69.184;

    private double secondsTdb;
    private double warp = 1.0;

    public SimTime(double secondsSinceJ2000Tdb) { this.secondsTdb = secondsSinceJ2000Tdb; }

    public static SimTime ofJulianDate(double jdTdb) { return new SimTime((jdTdb - J2000_JD) * DAY); }

    /** Gregorian calendar UTC date-time -> SimTime. */
    public static SimTime fromUtc(int year, int month, int day, int hour, int minute, double second) {
        return ofJulianDate(julianDate(year, month, day, hour, minute, second) + TT_MINUS_UTC / DAY);
    }

    /** Meeus, Astronomical Algorithms ch. 7 (Gregorian calendar). */
    public static double julianDate(int y, int m, int d, int hh, int mm, double ss) {
        if (m <= 2) { y -= 1; m += 12; }
        int a = y / 100, b = 2 - a + a / 4;
        double dayFrac = d + (hh + (mm + ss / 60.0) / 60.0) / 24.0;
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + dayFrac + b - 1524.5;
    }

    public double seconds() { return secondsTdb; }
    public double julianDate() { return J2000_JD + secondsTdb / DAY; }
    public double daysSinceJ2000() { return secondsTdb / DAY; }
    public double centuriesSinceJ2000() { return secondsTdb / DAY / JULIAN_CENTURY_DAYS; }

    public double warp() { return warp; }
    /** Time acceleration factor (1 = real time). Negative values run time backwards. */
    public void setWarp(double w) { this.warp = w; }

    /** Advance by a real (wall-clock) interval; returns the simulated seconds that passed. */
    public double tick(double realSeconds) { double dt = realSeconds * warp; secondsTdb += dt; return dt; }
    public void set(double secondsSinceJ2000Tdb) { this.secondsTdb = secondsSinceJ2000Tdb; }
    public SimTime copy() { SimTime t = new SimTime(secondsTdb); t.warp = warp; return t; }
}
