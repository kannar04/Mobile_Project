package com.gmail.manhle240406.project.adoption;

import com.gmail.manhle240406.project.pet.PetSchema;
import com.google.firebase.Timestamp;

import java.util.*;

/** Immutable minimized managed-read DTO; not a fabricated Firestore snapshot. */
public final class ListingRow {
    public boolean invalidated;
    public final long receivedAt = android.os.SystemClock.elapsedRealtime();
    private final Map<String, Object> data;

    public ListingRow(Map<String, Object> input) {
        Map<String, Object> normalized = normalize(input), canonical = new HashMap<>(normalized);
        canonical.remove("thumbnailAssetId");
        DiscoveryProjection.validate(canonical, (String) canonical.get("listingId"));
        Object thumbnail = normalized.get("thumbnailAssetId");
        if (thumbnail != null && !PetSchema.id(thumbnail))
            throw new IllegalArgumentException("invalid_thumbnail_id");
        data = Collections.unmodifiableMap(normalized);
    }

    private static Map<String, Object> normalize(Map<String, Object> input) {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> e : input.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Number && ((Number) v).doubleValue() == ((Number) v).longValue())
                v = ((Number) v).longValue();
            if ((e.getKey().equals("firstPublishedAt") || e.getKey().equals("updatedAt"))
                    && v instanceof String)
                v = new Timestamp(java.util.Date.from(java.time.Instant.parse((String) v)));
            if ((e.getKey().equals("firstPublishedAt") || e.getKey().equals("updatedAt"))
                    && v instanceof Map) {
                Map<?, ?> stamp = (Map<?, ?>) v;
                Object seconds =
                        stamp.containsKey("_seconds")
                                ? stamp.get("_seconds")
                                : stamp.get("seconds");
                Object nanos =
                        stamp.containsKey("_nanoseconds")
                                ? stamp.get("_nanoseconds")
                                : stamp.get("nanoseconds");
                if (seconds instanceof Number)
                    v =
                            new Timestamp(
                                    ((Number) seconds).longValue(),
                                    nanos instanceof Number ? ((Number) nanos).intValue() : 0);
            }
            out.put(e.getKey(), v);
        }
        return out;
    }

    public String getId() {
        return (String) data.get("listingId");
    }

    public String getString(String key) {
        return (String) data.get(key);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public Long getLong(String key) {
        return ((Number) data.get(key)).longValue();
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Timestamp getTimestamp(String key) {
        return (Timestamp) data.get(key);
    }

    public boolean exists() {
        return true;
    }
}
