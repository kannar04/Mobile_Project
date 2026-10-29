package com.gmail.manhle240406.project.community;

import java.util.*;

/**
 * Bounded viewer-scoped profile names and avatar references. Own identity never crosses viewers.
 */
public final class SessionAliases {
    private static final LinkedHashMap<String, Entry> entries =
            new LinkedHashMap<>(100, .75f, true);
    private static long generation;

    public static final class Entry {
        public final Map<String, Object> value;
        final long at;

        Entry(Map<String, Object> v, long n) {
            value = v == null ? null : Collections.unmodifiableMap(new HashMap<>(v));
            at = n;
        }
    }

    private SessionAliases() {}

    public static synchronized long generation() {
        return generation;
    }

    public static synchronized void clear() {
        generation++;
        entries.clear();
    }

    static synchronized Entry get(String viewer, String author, long now) {
        String key = viewer + ":" + author;
        Entry e = entries.get(key);
        if (e != null && (now < e.at || now - e.at >= (e.value == null ? 5_000 : 10 * 60_000))) {
            entries.remove(key);
            return null;
        }
        return e;
    }

    static synchronized void put(
            String viewer, String author, Map<String, Object> value, long now) {
        entries.put(viewer + ":" + author, new Entry(value, now));
        while (entries.size() > 100) entries.remove(entries.keySet().iterator().next());
    }
}
