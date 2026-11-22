package dev.saurus.cosmos.core.orbit;

import dev.saurus.cosmos.core.coords.Frames;
import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.time.SimTime;

/**
 * Geocentric Moon from the ELP-2000/82 series as truncated in Meeus, "Astronomical Algorithms" (2nd ed.), ch. 47:
 * the largest periodic terms of Tables 47.A (longitude, distance) and 47.B (latitude) plus the A1-A3 additive terms.
 * This captures evection (1.27 deg), variation (0.66 deg), the annual equation and the main latitude terms, giving
 * ~0.01 deg / ~ few km accuracy, versus ~1-2 deg for a plain precessing ellipse.
 * Output: mean ecliptic/equinox of date -> J2000 (precession in longitude only), then ICRS equatorial.
 */
public final class LunarTheory implements OrbitModel {
    // {D, M, M', F, sigmaL (1e-6 deg), sigmaR (1e-3 km)}
    private static final int[][] LR = {
        {0, 0, 1, 0, 6288774, -20905355}, {2, 0, -1, 0, 1274027, -3699111}, {2, 0, 0, 0, 658314, -2955968},
        {0, 0, 2, 0, 213618, -569925}, {0, 1, 0, 0, -185116, 48888}, {0, 0, 0, 2, -114332, -3149},
        {2, 0, -2, 0, 58793, 246158}, {2, -1, -1, 0, 57066, -152138}, {2, 0, 1, 0, 53322, -170733},
        {2, -1, 0, 0, 45758, -204586}, {0, 1, -1, 0, -40923, -129620}, {1, 0, 0, 0, -34720, 108743},
        {0, 1, 1, 0, -30383, 104755}, {2, 0, 0, -2, 15327, 10321}, {0, 0, 1, 2, -12528, 0},
        {0, 0, 1, -2, 10980, 79661}, {4, 0, -1, 0, 10675, -34782}, {0, 0, 3, 0, 10034, -23210},
        {4, 0, -2, 0, 8548, -21636}, {2, 1, -1, 0, -7888, 24208}, {2, 1, 0, 0, -6766, 30824},
        {1, 0, -1, 0, -5163, -8379}, {1, 1, 0, 0, 4987, -16675}, {2, -1, 1, 0, 4036, -12831},
        {2, 0, 2, 0, 3994, -10445}, {4, 0, 0, 0, 3861, -11650}, {2, 0, -3, 0, 3665, 14403},
        {0, 1, -2, 0, -2689, -7003}, {2, 0, -1, 2, -2602, 0}, {2, -1, -2, 0, 2390, 10056},
        {1, 0, 1, 0, -2348, 6322}, {2, -2, 0, 0, 2236, -9884}, {0, 1, 2, 0, -2120, 5751},
        {0, 2, 0, 0, -2069, 0}, {2, -2, -1, 0, 2048, -4950}, {2, 0, 1, -2, -1773, 4130},
        {2, 0, 0, 2, -1595, 0}, {4, -1, -1, 0, 1215, -3958}, {0, 0, 2, 2, -1110, 0},
        {3, 0, -1, 0, -892, 3258}, {2, 1, 1, 0, -810, 2616}, {4, -1, -2, 0, 759, -1897},
        {0, 2, -1, 0, -713, -2117}, {2, 2, -1, 0, -700, 2354}, {2, 1, -2, 0, 691, 0},
        {2, -1, 0, -2, 596, 0}, {4, 0, 1, 0, 549, -1423}, {0, 0, 4, 0, 537, -1117},
        {4, -1, 0, 0, 520, -1571}, {1, 0, -2, 0, -487, -1739}, {2, 1, 0, -2, -399, 0},
        {0, 0, 2, -2, -381, -4421}, {1, 1, 1, 0, 351, 0}, {3, 0, -2, 0, -340, 0},
        {4, 0, -3, 0, 330, 0}, {2, -1, 2, 0, 327, 0}, {0, 2, 1, 0, -323, 1165},
        {1, 1, -1, 0, 299, 0}, {2, 0, 3, 0, 294, 0}, {2, 0, -1, -2, 0, 8752}};
    // {D, M, M', F, sigmaB (1e-6 deg)}
    private static final int[][] B = {
        {0, 0, 0, 1, 5128122}, {0, 0, 1, 1, 280602}, {0, 0, 1, -1, 277693}, {2, 0, 0, -1, 173237},
        {2, 0, -1, 1, 55413}, {2, 0, -1, -1, 46271}, {2, 0, 0, 1, 32573}, {0, 0, 2, 1, 17198},
        {2, 0, 1, -1, 9266}, {0, 0, 2, -1, 8822}, {2, -1, 0, -1, 8216}, {2, 0, -2, -1, 4324},
        {2, 0, 1, 1, 4200}, {2, 1, 0, -1, -3359}, {2, -1, -1, 1, 2463}, {2, -1, 0, 1, 2211},
        {2, -1, -1, -1, 2065}, {0, 1, -1, -1, -1870}, {4, 0, -1, -1, 1828}, {0, 1, 0, 1, -1794},
        {0, 0, 0, 3, -1749}, {0, 1, -1, 1, -1565}, {1, 0, 0, 1, -1491}, {0, 1, 1, 1, -1475},
        {0, 1, 1, -1, -1410}, {0, 1, 0, -1, -1344}, {1, 0, 0, -1, -1335}, {0, 0, 3, 1, 1107},
        {4, 0, 0, -1, 1021}, {4, 0, -1, 1, 833}};

    @Override public Vec3d position(double t) {
        double T = t / SimTime.DAY / SimTime.JULIAN_CENTURY_DAYS;
        double Lp = deg(218.3164477 + 481267.88123421 * T - 0.0015786 * T * T);
        double D = deg(297.8501921 + 445267.1114034 * T - 0.0018819 * T * T);
        double M = deg(357.5291092 + 35999.0502909 * T - 0.0001536 * T * T);
        double Mp = deg(134.9633964 + 477198.8675055 * T + 0.0087414 * T * T);
        double F = deg(93.2720950 + 483202.0175233 * T - 0.0036539 * T * T);
        double A1 = deg(119.75 + 131.849 * T), A2 = deg(53.09 + 479264.290 * T), A3 = deg(313.45 + 481266.484 * T);
        double E = 1 - 0.002516 * T - 0.0000074 * T * T;
        double sl = 0, sr = 0, sb = 0;
        for (int[] r : LR) {
            double arg = r[0] * D + r[1] * M + r[2] * Mp + r[3] * F;
            double ef = Math.abs(r[1]) == 1 ? E : Math.abs(r[1]) == 2 ? E * E : 1;
            sl += r[4] * ef * Math.sin(arg);
            sr += r[5] * ef * Math.cos(arg);
        }
        for (int[] r : B) {
            double arg = r[0] * D + r[1] * M + r[2] * Mp + r[3] * F;
            double ef = Math.abs(r[1]) == 1 ? E : Math.abs(r[1]) == 2 ? E * E : 1;
            sb += r[4] * ef * Math.sin(arg);
        }
        sl += 3958 * Math.sin(A1) + 1962 * Math.sin(Lp - F) + 318 * Math.sin(A2);
        sb += -2235 * Math.sin(Lp) + 382 * Math.sin(A3) + 175 * Math.sin(A1 - F) + 175 * Math.sin(A1 + F)
              + 127 * Math.sin(Lp - Mp) - 115 * Math.sin(Lp + Mp);
        double lambdaOfDate = Lp + Math.toRadians(sl * 1e-6);
        double beta = Math.toRadians(sb * 1e-6);
        double distKm = 385000.56 + sr / 1000.0;
        // General precession in longitude since J2000 (IAU 1976 linear+quadratic term), referred back to J2000.
        double lambda = lambdaOfDate - Math.toRadians((5029.0966 * T + 1.11113 * T * T) / 3600.0);
        Vec3d ecl = new Vec3d(Math.cos(beta) * Math.cos(lambda), Math.cos(beta) * Math.sin(lambda), Math.sin(beta)).mul(distKm * Units.KM);
        return Frames.ECLIPTIC_TO_EQUATORIAL.apply(ecl);
    }

    @Override public double period() { return 27.321661 * SimTime.DAY; } // sidereal month

    private static double deg(double d) { return Math.toRadians(d % 360.0); }
}
