package dev.saurus.cosmos.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.saurus.cosmos.core.Universe;
import dev.saurus.cosmos.core.blackhole.BlackHole;
import dev.saurus.cosmos.core.body.Body;
import dev.saurus.cosmos.core.body.Provenance;
import dev.saurus.cosmos.core.catalog.DeepSkyCatalog;
import dev.saurus.cosmos.core.catalog.ExoplanetCatalog;
import dev.saurus.cosmos.core.coords.UniversePos;
import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.math.Vec3d;
import dev.saurus.cosmos.core.nav.ObjectIndex;
import dev.saurus.cosmos.core.nav.TravelPlanner;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/** /cosmos ... client commands. */
public final class CosmosCommands {
    private CosmosCommands() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(ClientCommands.literal("cosmos")
                .executes(c -> help(c))
                .then(ClientCommands.literal("help").executes(CosmosCommands::help))
                .then(ClientCommands.literal("fly").executes(c -> { enterSpace(c, "Earth"); return 1; }))
                .then(ClientCommands.literal("exit").executes(CosmosCommands::exit))
                .then(ClientCommands.literal("goto").then(ClientCommands.argument("name", StringArgumentType.greedyString())
                        .executes(c -> { enterSpace(c, StringArgumentType.getString(c, "name")); return 1; })))
                .then(ClientCommands.literal("search").then(ClientCommands.argument("query", StringArgumentType.greedyString())
                        .executes(c -> search(c, StringArgumentType.getString(c, "query")))))
                .then(ClientCommands.literal("info").then(ClientCommands.argument("name", StringArgumentType.greedyString())
                        .executes(c -> info(c, StringArgumentType.getString(c, "name")))))
                .then(ClientCommands.literal("speed").then(ClientCommands.argument("factor", DoubleArgumentType.doubleArg(0.001, 1000))
                        .executes(c -> { CosmosState.speedFactor = DoubleArgumentType.getDouble(c, "factor"); say(c, "§bSpeed factor: " + CosmosState.speedFactor + " x distance-to-nearest-surface per second"); return 1; })))
                .then(ClientCommands.literal("time")
                        .then(ClientCommands.literal("now").executes(c -> { CosmosState.time.set(CosmosState.nowUtc().seconds()); CosmosState.time.setWarp(1); say(c, "§bTime reset to now (real time)."); return 1; }))
                        .then(ClientCommands.argument("warp", DoubleArgumentType.doubleArg(-1e9, 1e9))
                                .executes(c -> { CosmosState.time.setWarp(DoubleArgumentType.getDouble(c, "warp")); say(c, "§bTime warp x" + CosmosState.time.warp()); return 1; })))
                .then(ClientCommands.literal("sky")
                        .then(ClientCommands.literal("on").executes(c -> { CosmosState.overworldSky = true; say(c, "§bReal star sky on."); return 1; }))
                        .then(ClientCommands.literal("off").executes(c -> { CosmosState.overworldSky = false; say(c, "§bReal star sky off."); return 1; })))
                .then(ClientCommands.literal("location").then(ClientCommands.argument("lat", DoubleArgumentType.doubleArg(-90, 90))
                        .then(ClientCommands.argument("lon", DoubleArgumentType.doubleArg(-180, 180)).executes(c -> {
                            CosmosState.observerLatDeg = DoubleArgumentType.getDouble(c, "lat");
                            CosmosState.observerLonDeg = DoubleArgumentType.getDouble(c, "lon");
                            say(c, "§bSky observer set to latitude " + CosmosState.observerLatDeg + ", longitude " + CosmosState.observerLonDeg);
                            return 1; }))))
        );
    }

    private static int help(CommandContext<FabricClientCommandSource> c) {
        say(c, "§6§lCosmos§r §7— a true-scale universe");
        say(c, "§e/cosmos fly§7 — launch into space (above Earth)");
        say(c, "§e/cosmos goto <name>§7 — fly to anything: Moon, Saturn, Sirius, Proxima Centauri b, TRAPPIST-1, Sgr A*, Andromeda");
        say(c, "§e/cosmos search <text>§7 / §e/cosmos info <name>§7 — look things up (with data sources)");
        say(c, "§e/cosmos speed <x>§7 — flight speed factor (default 0.5)   §e/cosmos time <x>|now§7 — time warp");
        say(c, "§e/cosmos exit§7 — back to your world   §e/cosmos sky on|off§7 — real stars in the overworld");
        say(c, "§7In space: WASD move, Space/Shift up/down, Sprint = x20. Speed scales with distance to the nearest object.");
        return 1;
    }

    private static Universe needUniverse(CommandContext<FabricClientCommandSource> c) {
        Universe u = CosmosState.universe();
        if (u == null) say(c, "§cCosmos is still loading its star catalogues (a few seconds after launch). Try again shortly.");
        return u;
    }

    private static int exit(CommandContext<FabricClientCommandSource> c) {
        CosmosState.spaceMode = false;
        say(c, "§bWelcome back to your world.");
        return 1;
    }

    static void enterSpace(CommandContext<FabricClientCommandSource> c, String name) {
        if (needUniverse(c) == null) return;
        for (String line : goTo(name)) say(c, line);
    }

    /** Jump to a named object (used by the command and by the automated in-game test). Returns chat lines. */
    public static List<String> goTo(String name) {
        Universe u = CosmosState.universe();
        if (u == null) return List.of("§cCosmos is still loading.");
        List<ObjectIndex.Hit> hits = u.index.search(name, 1);
        if (hits.isEmpty()) return List.of("§cNothing called '" + name + "' in the catalogues. Try /cosmos search " + name);
        ObjectIndex.Entry e = hits.get(0).entry();
        double t = CosmosState.time.seconds();
        UniversePos target = e.position().apply(t);
        double radius = radiusOf(u, e);
        // arrive on the lit side, a few radii out, looking at the target
        Vec3d fromTargetToLight = target.to(UniversePos.ORIGIN);
        Vec3d dir = fromTargetToLight.length() > 1 ? fromTargetToLight.normalize() : new Vec3d(0, -1, 0.3).normalize();
        Vec3d side = dir.cross(CosmosState.upHint()).normalize();
        Vec3d approach = dir.mul(0.8).add(side.mul(0.6)).normalize();
        double standoff = standoff(e, radius);
        UniversePos from = target.add(approach.mul(standoff));
        double dist = CosmosState.shipPos.distanceTo(target);
        CosmosState.shipPos = from;
        CosmosState.targetName = e.name();
        CosmosState.targetPos = target;
        CosmosState.spaceMode = true;
        float[] yp = CosmosState.lookAt(from.to(target));
        var p = Minecraft.getInstance().player;
        if (p != null) { p.setYRot(yp[0]); p.setXRot(yp[1]); }
        TravelPlanner.Plan g = TravelPlanner.brachistochrone(Math.max(dist, 1), 9.80665);
        return List.of("§bArrived at §f" + e.name() + "§b (" + e.detail() + "). Distance now " + Units.formatDistance(standoff) + ".",
                "§7(Jump was a gameplay warp. Real 1 g ship: " + TravelPlanner.formatDuration(g.earthTimeS()) + " Earth time, "
                        + TravelPlanner.formatDuration(g.shipTimeS()) + " on board.)");
    }

    private static double radiusOf(Universe u, ObjectIndex.Entry e) {
        Object r = e.ref();
        if (r instanceof Body b) return b.meanRadius();
        if (r instanceof Integer si) return u.stars.radiusEstimateRsun(si) * Units.R_SUN;
        if (r instanceof BlackHole bh) return bh.horizonRadiusM();
        if (r instanceof DeepSkyCatalog.Dso d) return Double.isNaN(d.diameterPc()) ? 10 * Units.PARSEC : d.diameterPc() / 2 * Units.PARSEC;
        if (r instanceof ExoplanetCatalog.Row row) return (Double.isNaN(row.radiusRj()) ? 0.3 : row.radiusRj()) * Units.JUPITER_RADIUS;
        if (r instanceof String) return Units.AU;
        return 1e7;
    }

    private static double standoff(ObjectIndex.Entry e, double radius) {
        return switch (e.kind()) {
            case BLACK_HOLE -> radius * 15;            // ~30 M: lensing and disc fill the view
            case GALAXY -> radius * 3.5;
            case CLUSTER, NEBULA -> radius * 3;
            case STAR -> e.ref() instanceof String ? 1.5 * Units.AU : radius * 8;
            case EXOPLANET -> 0.02 * Units.AU;
            default -> radius * 4;
        };
    }

    private static int search(CommandContext<FabricClientCommandSource> c, String q) {
        Universe u = needUniverse(c);
        if (u == null) return 0;
        List<ObjectIndex.Hit> hits = u.index.search(q, 8);
        if (hits.isEmpty()) { say(c, "§7No matches for '" + q + "'."); return 0; }
        double t = CosmosState.time.seconds();
        for (ObjectIndex.Hit h : hits) {
            double d = UniversePos.ORIGIN.distanceTo(h.entry().position().apply(t));
            say(c, "§f" + h.entry().name() + " §7[" + h.entry().kind().name().toLowerCase().replace('_', ' ') + "] " + h.entry().detail() + " §8— " + Units.formatDistance(d) + " from the Sun");
        }
        return hits.size();
    }

    private static int info(CommandContext<FabricClientCommandSource> c, String q) {
        Universe u = needUniverse(c);
        if (u == null) return 0;
        List<ObjectIndex.Hit> hits = u.index.search(q, 1);
        if (hits.isEmpty()) { say(c, "§7No matches for '" + q + "'."); return 0; }
        ObjectIndex.Entry e = hits.get(0).entry();
        double t = CosmosState.time.seconds();
        say(c, "§6" + e.name() + " §7(" + e.kind().name().toLowerCase().replace('_', ' ') + ")");
        say(c, "§7Distance from the Sun: §f" + Units.formatDistance(UniversePos.ORIGIN.distanceTo(e.position().apply(t))));
        Object r = e.ref();
        if (r instanceof Body b) for (Provenance.Fact f : b.facts()) fact(c, f.field(), f.value(), f.provenance(), f.source());
        else if (r instanceof Integer si) {
            fact(c, "Spectral type", String.valueOf(u.stars.spectralType(si)), Provenance.MEASURED, "HYG v4.1");
            fact(c, "Apparent magnitude (from Earth)", String.format("%.2f", u.stars.appMagFromSun(si)), Provenance.MEASURED, "HYG v4.1");
            fact(c, "Luminosity", String.format("%.4g L_sun", u.stars.luminosity(si)), Provenance.MEASURED, "HYG v4.1");
            fact(c, "Temperature", String.format("%.0f K", u.stars.teff(si)), Provenance.ESTIMATE, "Ballesteros 2012 from B-V");
            fact(c, "Radius", String.format("%.3g R_sun", u.stars.radiusEstimateRsun(si)), Provenance.ESTIMATE, "Stefan-Boltzmann");
        } else if (r instanceof BlackHole bh) {
            fact(c, "Mass", String.format("%.4g M_sun", bh.massSun()), Provenance.MEASURED, bh.source());
            fact(c, "Event horizon radius", Units.formatDistance(bh.horizonRadiusM()), Provenance.ESTIMATE, "Kerr r+ from mass and spin");
        } else if (r instanceof DeepSkyCatalog.Dso d) {
            fact(c, "Type", d.type() + " " + d.hubble(), Provenance.MEASURED, "OpenNGC");
            fact(c, "Distance", String.format("%.4g kpc", d.distKpc()), d.distProvenance(), d.distSource());
            if (!Double.isNaN(d.diameterPc())) fact(c, "Diameter", String.format("%.3g ly", d.diameterPc() * 3.26156), Provenance.ESTIMATE, "angular size x distance");
        } else if (r instanceof ExoplanetCatalog.Row row) {
            fact(c, "Status", row.status(), Provenance.MEASURED, "Open Exoplanet Catalogue");
            fact(c, "Discovered", row.method() + " " + row.year(), Provenance.MEASURED, "Open Exoplanet Catalogue");
            if (!Double.isNaN(row.periodD())) fact(c, "Orbital period", String.format("%.4g days", row.periodD()), Provenance.MEASURED, "Open Exoplanet Catalogue");
            if (!Double.isNaN(row.massMj())) fact(c, "Mass", String.format("%.3g Earth masses", row.massMj() * 317.83), Provenance.MEASURED, "Open Exoplanet Catalogue");
            if (!Double.isNaN(row.teqK())) fact(c, "Equilibrium temperature", String.format("%.0f K", row.teqK()), Provenance.MEASURED, "Open Exoplanet Catalogue");
        }
        return 1;
    }

    private static void fact(CommandContext<FabricClientCommandSource> c, String k, String v, Provenance p, String src) {
        String badge = switch (p) { case MEASURED -> "§a[measured]"; case ESTIMATE -> "§e[estimate]"; case PROCEDURAL -> "§d[procedural]"; };
        say(c, "§7" + k + ": §f" + v + " " + badge + " §8" + (src == null ? "" : src));
    }

    static void say(CommandContext<FabricClientCommandSource> c, String s) { c.getSource().sendFeedback(Component.literal(s)); }
}
