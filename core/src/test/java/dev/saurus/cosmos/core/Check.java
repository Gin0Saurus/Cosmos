package dev.saurus.cosmos.core;

import java.util.ArrayList;
import java.util.List;

/** Tiny dependency-free test harness (JUnit is not reachable from the build sandbox). */
public final class Check {
    private static final List<String> failures = new ArrayList<>();
    private static int passed;

    public static void near(String what, double actual, double expected, double tol) {
        boolean ok = Math.abs(actual - expected) <= tol && !Double.isNaN(actual);
        report(ok, String.format("%s: got %.10g, expected %.10g (tol %.3g)", what, actual, expected, tol));
    }
    public static void rel(String what, double actual, double expected, double relTol) {
        boolean ok = Math.abs(actual - expected) <= relTol * Math.abs(expected) && !Double.isNaN(actual);
        report(ok, String.format("%s: got %.10g, expected %.10g (rel tol %.3g, rel err %.3g)", what, actual, expected, relTol,
                Math.abs(actual - expected) / Math.abs(expected)));
    }
    public static void that(String what, boolean cond) { report(cond, what); }
    public static void info(String s) { System.out.println("    . " + s); }

    private static void report(boolean ok, String msg) {
        if (ok) { passed++; System.out.println("  PASS " + msg); }
        else { failures.add(msg); System.out.println("  FAIL " + msg); }
    }
    public static int finish() {
        System.out.println();
        System.out.println(passed + " passed, " + failures.size() + " failed");
        for (String f : failures) System.out.println("  FAILED: " + f);
        return failures.isEmpty() ? 0 : 1;
    }
}
