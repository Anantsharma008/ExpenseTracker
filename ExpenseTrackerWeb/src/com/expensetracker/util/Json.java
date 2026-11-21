package com.expensetracker.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny JSON helper: escape strings for output and parse a flat JSON object (string/number/bool/null values). */
public final class Json {
    private Json() { }

    public static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    public static Map<String, String> parseObject(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        int[] p = {0};
        skipWs(json, p);
        expect(json, p, '{');
        skipWs(json, p);
        if (peek(json, p) == '}') return map;
        while (true) {
            skipWs(json, p);
            String key = readString(json, p);
            skipWs(json, p);
            expect(json, p, ':');
            skipWs(json, p);
            char c = peek(json, p);
            String value;
            if (c == '"') value = readString(json, p);
            else {
                int start = p[0];
                while (p[0] < json.length() && ",}".indexOf(json.charAt(p[0])) < 0) p[0]++;
                value = json.substring(start, p[0]).trim();
                if (value.equals("null")) value = null;
            }
            map.put(key, value);
            skipWs(json, p);
            char n = peek(json, p);
            p[0]++;
            if (n == '}') return map;
            if (n != ',') throw new IllegalArgumentException("Invalid JSON");
        }
    }

    private static char peek(String s, int[] p) {
        if (p[0] >= s.length()) throw new IllegalArgumentException("Invalid JSON");
        return s.charAt(p[0]);
    }

    private static void expect(String s, int[] p, char c) {
        if (peek(s, p) != c) throw new IllegalArgumentException("Invalid JSON");
        p[0]++;
    }

    private static void skipWs(String s, int[] p) {
        while (p[0] < s.length() && Character.isWhitespace(s.charAt(p[0]))) p[0]++;
    }

    private static String readString(String s, int[] p) {
        expect(s, p, '"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = peek(s, p);
            p[0]++;
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            char e = peek(s, p);
            p[0]++;
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'u':
                    if (p[0] + 4 > s.length()) throw new IllegalArgumentException("Invalid JSON");
                    sb.append((char) Integer.parseInt(s.substring(p[0], p[0] + 4), 16));
                    p[0] += 4;
                    break;
                default: sb.append(e);
            }
        }
    }
}
