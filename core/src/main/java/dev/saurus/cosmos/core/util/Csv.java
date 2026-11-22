package dev.saurus.cosmos.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** RFC-4180-ish CSV streaming reader (quoted fields, doubled quotes). Single-line records only. */
public final class Csv {
    private Csv() {}

    public static final class Row {
        private final Map<String, Integer> idx; private final String[] f;
        Row(Map<String, Integer> idx, String[] f) { this.idx = idx; this.f = f; }
        public String s(String col) { Integer i = idx.get(col); return i == null || i >= f.length ? "" : f[i]; }
        public double d(String col, double def) {
            String v = s(col); if (v.isEmpty()) return def;
            try { return Double.parseDouble(v); } catch (NumberFormatException e) { return def; }
        }
        public boolean has(String col) { return !s(col).isEmpty(); }
    }

    public static void read(InputStream in, Consumer<Row> sink) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16)) {
            String line = r.readLine();
            if (line == null) return;
            String[] head = split(line);
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < head.length; i++) idx.put(head[i], i);
            while ((line = r.readLine()) != null) if (!line.isEmpty()) sink.accept(new Row(idx, split(line)));
        }
    }

    public static String[] split(String line) {
        List<String> out = new ArrayList<>(24);
        StringBuilder b = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (q) {
                if (c == '"') { if (i + 1 < line.length() && line.charAt(i + 1) == '"') { b.append('"'); i++; } else q = false; }
                else b.append(c);
            } else if (c == '"') q = true;
            else if (c == ',') { out.add(b.toString()); b.setLength(0); }
            else b.append(c);
        }
        out.add(b.toString());
        return out.toArray(new String[0]);
    }
}
