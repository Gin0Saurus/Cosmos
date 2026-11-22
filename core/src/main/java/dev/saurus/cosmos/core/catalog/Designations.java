package dev.saurus.cosmos.core.catalog;

import java.util.HashMap;
import java.util.Map;

/** Expands catalogue abbreviations ("Alp1 Cen") into searchable names ("Alpha Centauri", "Alpha1 Centauri"). */
public final class Designations {
    private Designations() {}

    private static final Map<String, String> GREEK = new HashMap<>();
    private static final Map<String, String> GENITIVE = new HashMap<>();
    static {
        String[] g = {"Alp", "Alpha", "Bet", "Beta", "Gam", "Gamma", "Del", "Delta", "Eps", "Epsilon", "Zet", "Zeta",
                "Eta", "Eta", "The", "Theta", "Iot", "Iota", "Kap", "Kappa", "Lam", "Lambda", "Mu", "Mu", "Nu", "Nu",
                "Xi", "Xi", "Omi", "Omicron", "Pi", "Pi", "Rho", "Rho", "Sig", "Sigma", "Tau", "Tau", "Ups", "Upsilon",
                "Phi", "Phi", "Chi", "Chi", "Psi", "Psi", "Ome", "Omega"};
        for (int i = 0; i < g.length; i += 2) GREEK.put(g[i], g[i + 1]);
        String[] c = {"And", "Andromedae", "Ant", "Antliae", "Aps", "Apodis", "Aqr", "Aquarii", "Aql", "Aquilae", "Ara", "Arae",
                "Ari", "Arietis", "Aur", "Aurigae", "Boo", "Bootis", "Cae", "Caeli", "Cam", "Camelopardalis", "Cnc", "Cancri",
                "CVn", "Canum Venaticorum", "CMa", "Canis Majoris", "CMi", "Canis Minoris", "Cap", "Capricorni", "Car", "Carinae",
                "Cas", "Cassiopeiae", "Cen", "Centauri", "Cep", "Cephei", "Cet", "Ceti", "Cha", "Chamaeleontis", "Cir", "Circini",
                "Col", "Columbae", "Com", "Comae Berenices", "CrA", "Coronae Australis", "CrB", "Coronae Borealis", "Crv", "Corvi",
                "Crt", "Crateris", "Cru", "Crucis", "Cyg", "Cygni", "Del", "Delphini", "Dor", "Doradus", "Dra", "Draconis",
                "Equ", "Equulei", "Eri", "Eridani", "For", "Fornacis", "Gem", "Geminorum", "Gru", "Gruis", "Her", "Herculis",
                "Hor", "Horologii", "Hya", "Hydrae", "Hyi", "Hydri", "Ind", "Indi", "Lac", "Lacertae", "Leo", "Leonis",
                "LMi", "Leonis Minoris", "Lep", "Leporis", "Lib", "Librae", "Lup", "Lupi", "Lyn", "Lyncis", "Lyr", "Lyrae",
                "Men", "Mensae", "Mic", "Microscopii", "Mon", "Monocerotis", "Mus", "Muscae", "Nor", "Normae", "Oct", "Octantis",
                "Oph", "Ophiuchi", "Ori", "Orionis", "Pav", "Pavonis", "Peg", "Pegasi", "Per", "Persei", "Phe", "Phoenicis",
                "Pic", "Pictoris", "Psc", "Piscium", "PsA", "Piscis Austrini", "Pup", "Puppis", "Pyx", "Pyxidis", "Ret", "Reticuli",
                "Sge", "Sagittae", "Sgr", "Sagittarii", "Sco", "Scorpii", "Scl", "Sculptoris", "Sct", "Scuti", "Ser", "Serpentis",
                "Sex", "Sextantis", "Tau", "Tauri", "Tel", "Telescopii", "Tri", "Trianguli", "TrA", "Trianguli Australis",
                "Tuc", "Tucanae", "UMa", "Ursae Majoris", "UMi", "Ursae Minoris", "Vel", "Velorum", "Vir", "Virginis",
                "Vol", "Volantis", "Vul", "Vulpeculae"};
        for (int i = 0; i < c.length; i += 2) GENITIVE.put(c[i], c[i + 1]);
    }

    /** HYG bayer field ("Alp1", "Bet", "Eps") + constellation ("Cen") -> {"Alpha Centauri", "Alpha1 Centauri"}. */
    public static String[] expandBayer(String bayer, String con) {
        if (bayer == null || bayer.isEmpty() || con == null || con.isEmpty()) return new String[0];
        String gen = GENITIVE.getOrDefault(con, con);
        String letters = bayer.replaceAll("[0-9\\-]", ""), digits = bayer.replaceAll("[^0-9]", "");
        String greek = GREEK.get(letters);
        if (greek == null) return new String[]{bayer + " " + gen};
        return digits.isEmpty() ? new String[]{greek + " " + gen}
                : new String[]{greek + " " + gen, greek + digits + " " + gen, greek + "-" + digits + " " + gen};
    }
}
