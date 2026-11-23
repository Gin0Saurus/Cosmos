package dev.saurus.cosmos.core.terrain;

import java.util.List;
import java.util.Locale;

/**
 * Real places built life-size (1 block = 1 m) at their real coordinates. Coordinates: Earth - WGS84; Moon - LRO
 * LROC-measured landing-site positions; Mars/Venus/Titan - NASA/ESA/CNSA published landing sites (planetocentric,
 * east longitude). Engine note: the terrain map is 1:100, so a life-size building covers 100x more map than the
 * real one would; only the main monument of each site is built.
 */
public final class Landmarks {
    private Landmarks() {}
    public enum Kind { PYRAMID, STONEHENGE, EIFFEL, ELIZABETH_TOWER, PISA, COLOSSEUM, APOLLO_LM, APOLLO_LM_ROVER, LANDER, ROVER, PROBE }
    public record Landmark(String id, String name, String body, double lat, double lon, Kind kind, String note) {
        public int radius() {
            return switch (kind) {
                case PYRAMID -> 116; case STONEHENGE -> 22; case EIFFEL -> 64; case ELIZABETH_TOWER -> 8; case PISA -> 12;
                case COLOSSEUM -> 96; case APOLLO_LM, APOLLO_LM_ROVER -> 9; case LANDER -> 5; case ROVER -> 4; case PROBE -> 3;
            };
        }
        public int height() {
            return switch (kind) {
                case PYRAMID -> 139; case STONEHENGE -> 8; case EIFFEL -> 331; case ELIZABETH_TOWER -> 97; case PISA -> 60;
                case COLOSSEUM -> 49; case APOLLO_LM, APOLLO_LM_ROVER -> 6; case LANDER -> 4; case ROVER -> 4; case PROBE -> 2;
            };
        }
    }

    public static final List<Landmark> ALL = List.of(
            new Landmark("great_pyramid", "Great Pyramid of Giza", "earth", 29.97917, 31.13417, Kind.PYRAMID, "Khufu, c. 2560 BC; 230 m base, 139 m tall today"),
            new Landmark("stonehenge", "Stonehenge", "earth", 51.17889, -1.82617, Kind.STONEHENGE, "c. 3000-2000 BC; 33 m sarsen circle, axis on the midsummer sunrise"),
            new Landmark("eiffel_tower", "Eiffel Tower", "earth", 48.85837, 2.29448, Kind.EIFFEL, "1889; 330 m to the antenna tip, platforms at 57, 116 and 276 m"),
            new Landmark("big_ben", "Elizabeth Tower (Big Ben)", "earth", 51.50073, -0.12463, Kind.ELIZABETH_TOWER, "1859; 96 m, clock faces 7 m across"),
            new Landmark("leaning_tower_of_pisa", "Leaning Tower of Pisa", "earth", 43.72300, 10.39659, Kind.PISA, "1372; 56 m, leaning ~4 degrees to the south"),
            new Landmark("colosseum", "Colosseum", "earth", 41.89021, 12.49223, Kind.COLOSSEUM, "AD 80; 189 x 156 m, 48 m tall, outer ring fallen on the south side"),
            new Landmark("apollo_11", "Apollo 11 - Tranquility Base", "moon", 0.67408, 23.47297, Kind.APOLLO_LM, "20 July 1969: Eagle's descent stage and the flag"),
            new Landmark("apollo_12", "Apollo 12 - Ocean of Storms", "moon", -3.01239, -23.42157, Kind.APOLLO_LM, "19 Nov 1969, next to Surveyor 3"),
            new Landmark("apollo_14", "Apollo 14 - Fra Mauro", "moon", -3.64530, -17.47136, Kind.APOLLO_LM, "5 Feb 1971"),
            new Landmark("apollo_15", "Apollo 15 - Hadley-Apennine", "moon", 26.13222, 3.63386, Kind.APOLLO_LM_ROVER, "30 Jul 1971, first Lunar Roving Vehicle"),
            new Landmark("apollo_16", "Apollo 16 - Descartes", "moon", -8.97301, 15.50019, Kind.APOLLO_LM_ROVER, "21 Apr 1972"),
            new Landmark("apollo_17", "Apollo 17 - Taurus-Littrow", "moon", 20.19080, 30.77168, Kind.APOLLO_LM_ROVER, "11 Dec 1972, the last crewed landing"),
            new Landmark("change_3", "Chang'e 3 and Yutu", "moon", 44.1214, -19.5116, Kind.LANDER, "14 Dec 2013, Mare Imbrium"),
            new Landmark("change_4", "Chang'e 4 and Yutu-2", "moon", -45.4446, 177.5991, Kind.LANDER, "3 Jan 2019, first landing on the far side"),
            new Landmark("perseverance", "Perseverance landing site (Octavia E. Butler)", "mars", 18.4447, 77.4508, Kind.ROVER, "18 Feb 2021, Jezero crater"),
            new Landmark("curiosity", "Curiosity landing site (Bradbury Landing)", "mars", -4.5895, 137.4417, Kind.ROVER, "6 Aug 2012, Gale crater"),
            new Landmark("viking_1", "Viking 1", "mars", 22.2692, -47.9506, Kind.LANDER, "20 Jul 1976, Chryse Planitia, first successful Mars landing"),
            new Landmark("viking_2", "Viking 2", "mars", 47.6680, 134.0100, Kind.LANDER, "3 Sep 1976, Utopia Planitia"),
            new Landmark("pathfinder", "Mars Pathfinder and Sojourner", "mars", 19.1300, -33.2200, Kind.LANDER, "4 Jul 1997, Ares Vallis"),
            new Landmark("spirit", "Spirit (final position)", "mars", -14.5684, 175.4726, Kind.ROVER, "2004-2010, Gusev crater"),
            new Landmark("opportunity", "Opportunity landing site (Challenger Memorial Station)", "mars", -1.9462, -5.5266, Kind.ROVER, "25 Jan 2004, Meridiani Planum"),
            new Landmark("insight", "InSight", "mars", 4.5024, 135.6234, Kind.LANDER, "26 Nov 2018, Elysium Planitia"),
            new Landmark("phoenix", "Phoenix", "mars", 68.2188, -125.7492, Kind.LANDER, "25 May 2008, dug up water ice"),
            new Landmark("zhurong", "Tianwen-1 lander and Zhurong", "mars", 25.066, 109.925, Kind.ROVER, "15 May 2021, Utopia Planitia"),
            new Landmark("venera_13", "Venera 13", "venus", -7.5, -56.5, Kind.PROBE, "1 Mar 1982, survived 127 minutes at 457 C"),
            new Landmark("huygens", "Huygens probe", "titan", -10.573, 167.665, Kind.PROBE, "14 Jan 2005, the most distant landing ever"));

    public static List<Landmark> on(String body) { return ALL.stream().filter(l -> l.body().equals(body)).toList(); }

    public static Landmark find(String q) {
        String k = q.toLowerCase(Locale.ROOT).replace(' ', '_').replace("'", "");
        for (Landmark l : ALL) if (l.id().equals(k)) return l;
        for (Landmark l : ALL) if (l.id().contains(k) || l.name().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) return l;
        return null;
    }
}
