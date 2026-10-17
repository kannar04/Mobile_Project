package com.gmail.manhle240406.project.adoption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.gmail.manhle240406.project.pet.PetSchema;

public final class AdoptionValues {
    private AdoptionValues() {}
    public static String listingId(String id) {
        if (!PetSchema.id(id)) throw new IllegalArgumentException("invalid_listing_id");
        return id;
    }
    public static String catalogId(String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,79}")) throw new IllegalArgumentException("invalid_catalog_id");
        return id;
    }
    public static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b: bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
    public static void text(String value, int max) {
        if (value == null || !value.equals(value.trim()) || value.codePointCount(0, value.length()) > max) throw new IllegalArgumentException("invalid_text");
    }
    public static long version(Map < String, Object > data, String key) {
        Object n = data == null ? null : data.get(key);
        if (!(n instanceof Long) || ((Long) n) < 0) throw new IllegalStateException("unknown_schema");
        return (Long) n;
    }
    public static String canonical(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof String) {
            StringBuilder b = new StringBuilder("\"");
            for (char c: ((String) value).toCharArray()) {
                if (c == '"' || c == '\\') b.append('\\').append(c);
                else if (c < 32) b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                else b.append(c);
            }
            return b.append('"').toString();
        }
        if (value instanceof Map) {
            StringJoiner j = new StringJoiner(",", "{", "}");
            Map < String, Object > sorted = new TreeMap < > ();
            for (Map.Entry < ? , ? > e : ((Map < ? , ? > ) value).entrySet()) sorted.put((String) e.getKey(), e.getValue());
            for (Map.Entry < String, Object > e: sorted.entrySet()) j.add(canonical(e.getKey()) + ":" + canonical(e.getValue()));
            return j.toString();
        }
        if (value instanceof List) {
            StringJoiner j = new StringJoiner(",", "[", "]");
            for (Object item: (List < ? > ) value) j.add(canonical(item));
            return j.toString();
        }
        throw new IllegalArgumentException("canonical_type");
    }
}