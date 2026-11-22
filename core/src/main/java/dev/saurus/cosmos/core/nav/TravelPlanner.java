package dev.saurus.cosmos.core.nav;

import dev.saurus.cosmos.core.coords.Units;

/**
 * Travel time estimates for the navigation UI.
 *
 * <p>REAL PHYSICS modes: constant-thrust "brachistochrone" (accelerate to midpoint, flip, decelerate) with full special
 * relativity: for proper acceleration a over distance d, coordinate (Earth) time t = (2c/a) sinh(a tau_half / c) and
 * ship proper time tau = (2c/a) acosh(1 + a d / (2 c^2)). Also coasting at a fixed speed.
 *
 * <p>GAMEPLAY CONVENIENCE (not physics): the "warp" mode moves the ship at a multiple of c without time dilation or
 * causality consequences. The UI always labels it as such.
 */
public final class TravelPlanner {
    private TravelPlanner() {}

    public record Plan(String mode, double earthTimeS, double shipTimeS, double peakSpeedC, boolean realPhysics) {}

    public static Plan brachistochrone(double distanceM, double accelMs2) {
        double c = Units.C, a = accelMs2;
        double tauHalf = (c / a) * acosh(1 + a * (distanceM / 2) / (c * c));
        double tHalf = (c / a) * Math.sinh(a * tauHalf / c);
        double peakBeta = Math.tanh(a * tauHalf / c);
        return new Plan(String.format("constant %.2f g thrust, flip at midpoint", a / 9.80665), 2 * tHalf, 2 * tauHalf, peakBeta, true);
    }

    public static Plan coast(double distanceM, double beta) {
        double t = distanceM / (beta * Units.C);
        double gamma = 1 / Math.sqrt(1 - beta * beta);
        return new Plan(String.format("coast at %.4g c", beta), t, t / gamma, beta, true);
    }

    /** Gameplay warp at the given multiple of c. Ship and Earth clocks advance together (fiction). */
    public static Plan warp(double distanceM, double multipleOfC) {
        double t = distanceM / (multipleOfC * Units.C);
        return new Plan(String.format("WARP x%.3g c (gameplay convenience, not real physics)", multipleOfC), t, t, multipleOfC, false);
    }

    /** Warp factor that crosses the given distance in the given real seconds. */
    public static double warpFactorFor(double distanceM, double seconds) { return distanceM / (Units.C * seconds); }

    private static double acosh(double x) { return Math.log(x + Math.sqrt(x * x - 1)); }

    public static String formatDuration(double s) {
        double a = Math.abs(s);
        if (a < 120) return String.format("%.1f s", s);
        if (a < 7200) return String.format("%.1f min", s / 60);
        if (a < 172800) return String.format("%.1f h", s / 3600);
        if (a < 2 * Units.JULIAN_YEAR_S) return String.format("%.1f days", s / 86400);
        if (a < 2e6 * Units.JULIAN_YEAR_S) return String.format("%,.1f years", s / Units.JULIAN_YEAR_S);
        return String.format("%,.3g years", s / Units.JULIAN_YEAR_S);
    }
}
