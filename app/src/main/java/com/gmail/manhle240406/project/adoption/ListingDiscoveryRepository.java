package com.gmail.manhle240406.project.adoption;

import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.*;

/** Bounded current-source reads; never read raw public projections from the client. */
public final class ListingDiscoveryRepository implements AutoCloseable {
  private final SessionGateway sessions;
  private boolean closed;
  private long generation;
  private final Map<String, Task<Map<String, Object>>> pending = new HashMap<>();
  private final LinkedHashMap<String, Entry> cache = new LinkedHashMap<>(20, .75f, true);

  private static final class Entry {
    final long at = android.os.SystemClock.elapsedRealtime();
    final Map<String, Object> value;

    Entry(Map<String, Object> v) {
      value = v;
    }
  }

  private final Runnable invalidation = this::clear;

  public ListingDiscoveryRepository(FirebaseFirestore ignored, SessionGateway s) {
    sessions = s;
    sessions.onInvalidated(invalidation);
  }

  public void clear() {
    generation++;
    cache.clear();
    pending.clear();
  }

  private void current(SessionEpoch.Ticket t) {
    if (closed || t.uid == null || !sessions.current(t))
      throw new IllegalStateException("session_changed");
  }

  private Task<Map<String, Object>> read(
      SessionEpoch.Ticket t, String op, Map<String, Object> input) {
    current(t);
    String key = t.uid + op + input.toString();
    Entry e = cache.get(key);
    if (e != null && android.os.SystemClock.elapsedRealtime() - e.at < 30_000)
      return Tasks.forResult(e.value);
    if (pending.containsKey(key)) return pending.get(key);
    if (pending.size() >= 20) return Tasks.forException(new IllegalStateException("read_limit"));
    long g = generation;
    Task<Map<String, Object>> task =
        AdoptionApi.call(op, input)
            .continueWith(
                done -> {
                  current(t);
                  if (g != generation) throw new IllegalStateException("source_invalidated");
                  if (!done.isSuccessful()) throw Objects.requireNonNull(done.getException());
                  return done.getResult();
                });
    pending.put(key, task);
    task.addOnCompleteListener(
        done -> {
          if (g != generation) return;
          pending.remove(key);
          if (done.isSuccessful()) {
            cache.put(key, new Entry(done.getResult()));
            while (cache.size() > 20) cache.remove(cache.keySet().iterator().next());
          } else cache.remove(key);
        });
    return task;
  }

  public static final class Page {
    private final List<ListingRow> rows;
    public final Map<String, Object> cursor;

    @SuppressWarnings("unchecked")
    Page(Map<String, Object> value) {
      rows = new ArrayList<>();
      for (Map<String, Object> d : AdoptionFlowModel.rows(value.get("rows")))
        rows.add(new ListingRow(d));
      cursor =
          value.get("cursor") instanceof Map ? (Map<String, Object>) value.get("cursor") : null;
    }

    public List<ListingRow> getDocuments() {
      return Collections.unmodifiableList(rows);
    }

    public boolean isEmpty() {
      return rows.isEmpty();
    }

    public int size() {
      return rows.size();
    }
  }

  public Task<Page> page(SessionEpoch.Ticket t, DiscoveryQuery spec, Map<String, Object> cursor) {
    return read(
            t,
            "discovery",
            AdoptionFlowModel.map(
                "prefix",
                spec.prefix,
                "availability",
                spec.availability,
                "filters",
                spec.filters,
                "own",
                false,
                "cursor",
                cursor))
        .continueWith(done -> new Page(done.getResult()));
  }

  public Task<Page> ownPage(SessionEpoch.Ticket t, Map<String, Object> cursor) {
    return read(
            t,
            "discovery",
            AdoptionFlowModel.map(
                "prefix",
                "",
                "availability",
                "any",
                "filters",
                Collections.emptyMap(),
                "own",
                true,
                "cursor",
                cursor))
        .continueWith(done -> new Page(done.getResult()));
  }

  public Task<Page> window(SessionEpoch.Ticket t, List<String> ids) {
    return read(t, "discoveryWindow", AdoptionFlowModel.map("ids", ids))
        .continueWith(done -> new Page(done.getResult()));
  }

  public Task<ListingRow> detail(SessionEpoch.Ticket t, String id) {
    return read(
            t, "discoveryDetail", AdoptionFlowModel.map("listingId", AdoptionValues.listingId(id)))
        .continueWith(
            done -> {
              Map<String, Object> row = AdoptionFlowModel.object(done.getResult().get("row"));
              return row.isEmpty() ? null : new ListingRow(row);
            });
  }

  @Override
  public void close() {
    clear();
    closed = true;
    sessions.removeInvalidation(invalidation);
  }
}
