package com.gmail.manhle240406.project.adoption;

import android.content.Context;

import com.gmail.manhle240406.project.foundation.SessionEpoch;
import com.gmail.manhle240406.project.foundation.SessionGateway;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.*;

import java.util.*;
import java.util.function.Consumer;

/** Process-shared, bounded stale-while-revalidate view of the public species catalog. */
public final class SpeciesCatalogStore {
    public static final class Entry {
        public final String id, vi, en;
        public final long version;

        Entry(String id, String vi, String en, long version) {
            this.id = id;
            this.vi = vi;
            this.en = en;
            this.version = version;
        }

        public String label(Context context) {
            return "en"
                            .equals(
                                    context.getResources()
                                            .getConfiguration()
                                            .getLocales()
                                            .get(0)
                                            .getLanguage())
                    ? en
                    : vi;
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof Entry)) return false;
            Entry other = (Entry) value;
            return version == other.version
                    && id.equals(other.id)
                    && vi.equals(other.vi)
                    && en.equals(other.en);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, vi, en, version);
        }
    }

    private static final String PREFS = "species_catalog_v1", ROWS = "rows", SYNC = "sync";
    private static final long REFRESH_MS = 5 * 60 * 1000L;
    private final Context app;
    private final SessionGateway sessions;
    private final CatalogRepository repository;
    private final List<Consumer<List<Entry>>> waiting = new ArrayList<>();
    private List<Entry> rows;
    private boolean loading;

    public SpeciesCatalogStore(Context context, FirebaseFirestore db, SessionGateway sessions) {
        app = context.getApplicationContext();
        this.sessions = sessions;
        repository = new CatalogRepository(db, sessions);
        rows = read();
    }

    public synchronized List<Entry> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(rows));
    }

    public synchronized String label(String id) {
        for (Entry row : rows) if (row.id.equals(id)) return row.label(app);
        return id == null ? "" : id;
    }

    public void refresh(boolean force, Consumer<List<Entry>> callback) {
        SessionEpoch.Ticket ticket = sessions == null ? null : sessions.capture();
        if (ticket == null || ticket.uid == null) {
            if (callback != null) callback.accept(snapshot());
            return;
        }
        synchronized (this) {
            if (callback != null) waiting.add(callback);
            long age =
                    System.currentTimeMillis()
                            - app.getSharedPreferences(PREFS, 0).getLong(SYNC, 0);
            if (loading) return;
            if (!force && age >= 0 && age < REFRESH_MS) {
                deliver();
                return;
            }
            loading = true;
        }
        repository
                .page(ticket, "species", null, null)
                .addOnCompleteListener(
                        done -> {
                            List<Entry> accepted = null;
                            if (done.isSuccessful() && sessions.current(ticket)) {
                                try {
                                    accepted = parse(done.getResult().getDocuments());
                                } catch (RuntimeException ignored) {
                                    accepted = null;
                                }
                            }
                            synchronized (this) {
                                loading = false;
                                if (accepted != null
                                        && !accepted.isEmpty()
                                        && !accepted.equals(rows)) {
                                    rows = accepted;
                                    write(rows);
                                } else if (accepted != null)
                                    app.getSharedPreferences(PREFS, 0)
                                            .edit()
                                            .putLong(SYNC, System.currentTimeMillis())
                                            .apply();
                                deliver();
                            }
                        });
    }

    private synchronized void deliver() {
        List<Entry> copy = snapshot();
        List<Consumer<List<Entry>>> callbacks = new ArrayList<>(waiting);
        waiting.clear();
        for (Consumer<List<Entry>> callback : callbacks) callback.accept(copy);
    }

    private List<Entry> parse(List<DocumentSnapshot> docs) {
        List<Entry> out = new ArrayList<>();
        for (DocumentSnapshot doc : docs) {
            if (out.size() >= 50) break;
            if (!"species".equals(doc.getString("kind"))
                    || !"active".equals(doc.getString("state"))) continue;
            Map<?, ?> labels =
                    doc.get("labels") instanceof Map
                            ? (Map<?, ?>) doc.get("labels")
                            : Collections.emptyMap();
            String vi = String.valueOf(labels.get("vi")), en = String.valueOf(labels.get("en"));
            Long version = doc.getLong("version");
            if (!doc.getId().matches("[a-z0-9][a-z0-9_-]{0,79}")
                    || vi.length() < 1
                    || vi.length() > 80
                    || en.length() < 1
                    || en.length() > 80
                    || version == null
                    || version < 1) throw new IllegalArgumentException("catalog_schema");
            out.add(new Entry(doc.getId(), vi, en, version));
        }
        return out;
    }

    private List<Entry> read() {
        String raw = app.getSharedPreferences(PREFS, 0).getString(ROWS, null);
        if (raw != null)
            try {
                JSONArray array = new JSONArray(raw);
                List<Entry> out = new ArrayList<>();
                for (int i = 0; i < array.length() && i < 50; i++) {
                    JSONObject row = array.getJSONObject(i);
                    String id = row.getString("id"),
                            vi = row.getString("vi"),
                            en = row.getString("en");
                    long version = row.getLong("version");
                    if (!id.matches("[a-z0-9][a-z0-9_-]{0,79}")
                            || vi.length() < 1
                            || vi.length() > 80
                            || en.length() < 1
                            || en.length() > 80
                            || version < 1) throw new JSONException("invalid");
                    out.add(new Entry(id, vi, en, version));
                }
                if (!out.isEmpty()) return out;
            } catch (JSONException ignored) {
            }
        return Arrays.asList(
                new Entry("cat", "Mèo", "Cat", 1),
                new Entry("dog", "Chó", "Dog", 1),
                new Entry("unknown", "Chưa rõ loài", "Unknown species", 1));
    }

    private void write(List<Entry> values) {
        JSONArray array = new JSONArray();
        try {
            for (Entry row : values)
                array.put(
                        new JSONObject()
                                .put("id", row.id)
                                .put("vi", row.vi)
                                .put("en", row.en)
                                .put("version", row.version));
            app.getSharedPreferences(PREFS, 0)
                    .edit()
                    .putString(ROWS, array.toString())
                    .putLong(SYNC, System.currentTimeMillis())
                    .apply();
        } catch (JSONException ignored) {
        }
    }
}
