package com.gmail.manhle240406.project.notifications;

import java.util.*;

/** Session-only data window. New head rows wait for explicit refresh; no durable queue/cache. */
public final class InboxWindow {
  public static final class Entry {
    public final String id;
    public final long createdAt, expiresAt, version;

    public Entry(String id, long createdAt, long expiresAt, long version) {
      this.id = InboxContract.itemId(id);
      if (version < 1 || expiresAt <= createdAt) throw new IllegalArgumentException("item_bounds");
      this.createdAt = createdAt;
      this.expiresAt = expiresAt;
      this.version = version;
    }
  }

  private String uid;
  private final Map<String, Entry> rows = new LinkedHashMap<>(), pending = new LinkedHashMap<>();
  private final Map<String, Long> pendingReads = new LinkedHashMap<>();
  private String anchor;
  private int offset;

  public synchronized void session(String nextUid) {
    if (!Objects.equals(uid, nextUid)) {
      rows.clear();
      pending.clear();
      pendingReads.clear();
      anchor = null;
      offset = 0;
      uid = nextUid;
    }
  }

  private void require(String owner) {
    if (owner == null || !Objects.equals(uid, owner))
      throw new IllegalStateException("session_changed");
  }

  private void insert(Map<String, Entry> target, Entry row, long now) {
    if (row.expiresAt <= now) return;
    Entry previous = rows.get(row.id);
    Entry old = target.get(row.id);
    if (previous != null && previous.createdAt != row.createdAt
        || old != null && old.createdAt != row.createdAt)
      throw new IllegalArgumentException("immutable_chronology");
    if (old == null || old.version < row.version) target.put(row.id, row);
  }

  public synchronized void page(String owner, List<Entry> page, long now) {
    require(owner);
    if (page.size() > InboxContract.PAGE) throw new IllegalArgumentException("page_limit");
    for (Entry row : page) insert(rows, row, now);
    trim(now);
  }

  public synchronized void head(String owner, List<Entry> page, long now) {
    require(owner);
    if (page.size() > InboxContract.PAGE) throw new IllegalArgumentException("page_limit");
    for (Entry row : page) insert(rows.containsKey(row.id) ? rows : pending, row, now);
    while (pending.size() > InboxContract.PAGE) pending.remove(pending.keySet().iterator().next());
    trim(now);
  }

  public synchronized boolean newActivity(String owner) {
    require(owner);
    return !pending.isEmpty();
  }

  public synchronized void refresh(String owner, long now) {
    require(owner);
    for (Entry row : pending.values()) insert(rows, row, now);
    pending.clear();
    trim(now);
  }

  private void trim(long now) {
    rows.values().removeIf(e -> e.expiresAt <= now);
    pending.values().removeIf(e -> e.expiresAt <= now);
    List<Entry> ordered = ordered();
    rows.clear();
    for (int i = 0; i < Math.min(InboxContract.WINDOW, ordered.size()); i++)
      rows.put(ordered.get(i).id, ordered.get(i));
    if (anchor != null && !rows.containsKey(anchor)) {
      anchor = null;
      offset = 0;
    }
  }

  private List<Entry> ordered() {
    List<Entry> result = new ArrayList<>(rows.values());
    result.sort(
        Comparator.comparingLong((Entry e) -> e.createdAt)
            .reversed()
            .thenComparing(e -> e.id, Comparator.reverseOrder()));
    return result;
  }

  public synchronized List<Entry> rows(String owner) {
    require(owner);
    return Collections.unmodifiableList(ordered());
  }

  public synchronized void anchor(String owner, String id, int offset) {
    require(owner);
    if (!rows.containsKey(id)) throw new IllegalArgumentException("missing_anchor");
    anchor = id;
    this.offset = offset;
  }

  public synchronized String anchor(String owner) {
    require(owner);
    return anchor;
  }

  public synchronized int offset(String owner) {
    require(owner);
    return offset;
  }

  public synchronized void pendingRead(String owner, String id, long expected) {
    require(owner);
    if (!rows.containsKey(id) || pendingReads.size() >= 20 && !pendingReads.containsKey(id))
      throw new IllegalArgumentException("pending_read_limit");
    pendingReads.putAll(InboxContract.readSet(Collections.singletonMap(id, expected)));
  }

  public synchronized Map<String, Long> pendingReads(String owner) {
    require(owner);
    return Collections.unmodifiableMap(new LinkedHashMap<>(pendingReads));
  }

  public synchronized void reconcileRead(String owner, String id) {
    require(owner);
    pendingReads.remove(id);
  }
}
