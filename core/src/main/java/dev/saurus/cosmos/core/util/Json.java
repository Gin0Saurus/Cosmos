package dev.saurus.cosmos.core.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal strict JSON reader (objects -> LinkedHashMap, arrays -> ArrayList, numbers -> Double). No dependencies. */
public final class Json {
    private final String s; private int p;
    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json j = new Json(text); j.ws(); Object v = j.value(); j.ws();
        if (j.p != j.s.length()) throw j.err("trailing characters");
        return v;
    }

    private Object value() {
        ws();
        if (p >= s.length()) throw err("unexpected end");
        char c = s.charAt(p);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return number();
        }
    }
    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>(); p++; ws();
        if (peek() == '}') { p++; return m; }
        while (true) {
            ws(); String k = string(); ws(); if (s.charAt(p++) != ':') throw err("expected ':'");
            m.put(k, value()); ws();
            char c = s.charAt(p++);
            if (c == '}') return m; if (c != ',') throw err("expected ',' or '}'");
        }
    }
    private List<Object> array() {
        List<Object> l = new ArrayList<>(); p++; ws();
        if (peek() == ']') { p++; return l; }
        while (true) {
            l.add(value()); ws();
            char c = s.charAt(p++);
            if (c == ']') return l; if (c != ',') throw err("expected ',' or ']'");
        }
    }
    private String string() {
        if (s.charAt(p) != '"') throw err("expected string");
        StringBuilder b = new StringBuilder(); p++;
        while (true) {
            char c = s.charAt(p++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = s.charAt(p++);
                switch (e) {
                    case 'n' -> b.append('\n'); case 't' -> b.append('\t'); case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b'); case 'f' -> b.append('\f');
                    case 'u' -> { b.append((char) Integer.parseInt(s.substring(p, p + 4), 16)); p += 4; }
                    default -> b.append(e);
                }
            } else b.append(c);
        }
    }
    private Double number() {
        int st = p;
        while (p < s.length() && "+-0123456789.eE".indexOf(s.charAt(p)) >= 0) p++;
        if (st == p) throw err("unexpected character '" + s.charAt(p) + "'");
        return Double.parseDouble(s.substring(st, p));
    }
    private void expect(String w) { if (!s.startsWith(w, p)) throw err("expected " + w); p += w.length(); }
    private char peek() { return s.charAt(p); }
    private void ws() { while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++; }
    private RuntimeException err(String m) { return new IllegalArgumentException("JSON " + m + " at " + p); }

    // ---- typed accessors
    @SuppressWarnings("unchecked") public static Map<String, Object> obj(Object o) { return (Map<String, Object>) o; }
    @SuppressWarnings("unchecked") public static List<Object> arr(Object o) { return (List<Object>) o; }
    public static double num(Map<String, Object> m, String k) { return ((Number) m.get(k)).doubleValue(); }
    public static double num(Map<String, Object> m, String k, double def) { Object o = m.get(k); return o instanceof Number n ? n.doubleValue() : def; }
    public static String str(Map<String, Object> m, String k) { Object o = m.get(k); return o == null ? null : o.toString(); }
    public static double[] nums(Object o) {
        List<Object> l = arr(o); double[] d = new double[l.size()];
        for (int i = 0; i < d.length; i++) d[i] = ((Number) l.get(i)).doubleValue();
        return d;
    }
}
