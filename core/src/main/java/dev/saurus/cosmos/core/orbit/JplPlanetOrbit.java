package dev.saurus.cosmos.core.orbit;

import dev.saurus.cosmos.core.coords.Frames;
import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.time.SimTime;

/**
 * Heliocentric planet position from JPL "Keplerian Elements for Approximate Positions of the Major Planets"
 * (Standish), Table 1, valid 1800-2050. Elements: a [au], e, I [deg], L [deg], long.peri [deg], long.node [deg]
 * plus linear rates per Julian century. Procedure follows the JPL page step by step.
 *
 * <p>Nominal accuracy over 1800-2050 (per JPL): ~arcseconds to arcminutes in longitude. Outside that window the
 * elements still evolve linearly and remain qualitatively correct, but degrade; the UI flags dates outside 1800-2050.
 */
public final class JplPlanetOrbit implements OrbitModel {
    private final double[] el, rate;
    private final double gmTotal;

    public JplPlanetOrbit(double[] elements, double[] rates, double gmSunPlusBody) {
        this.el = elements.clone(); this.rate = rates.clone(); this.gmTotal = gmSunPlusBody;
    }

    public Kepler.Elements elementsAt(double t) {
        double T = t / SimTime.DAY / SimTime.JULIAN_CENTURY_DAYS;
        double a = (el[0] + rate[0] * T) * Units.AU;
        double e = el[1] + rate[1] * T;
        double I = Math.toRadians(el[2] + rate[2] * T);
        double L = el[3] + rate[3] * T;
        double varpi = el[4] + rate[4] * T;
        double node = el[5] + rate[5] * T;
        double argp = Math.toRadians(varpi - node);
        double M = Math.toRadians(L - varpi);
        return new Kepler.Elements(a, e, I, Math.toRadians(node), argp, M);
    }

    @Override public Vec3d position(double t) {
        return Frames.ECLIPTIC_TO_EQUATORIAL.apply(Kepler.position(elementsAt(t)));
    }

    @Override public double period() {
        double a = el[0] * Units.AU;
        return 2 * Math.PI * Math.sqrt(a * a * a / gmTotal);
    }

    public static boolean inValidityWindow(double t) {
        double T = t / SimTime.DAY / SimTime.JULIAN_CENTURY_DAYS; // 1800..2050 = -2.0 .. +0.5 centuries
        return T >= -2.0 && T <= 0.5;
    }
}
