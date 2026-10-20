package com.gmail.manhle240406.project.community;

import java.util.*;

/** Bounded stable-ID window; versions replace content without moving a reader's rows. */
public final class FeedWindow<T> {
  public interface Identity<T> {
    String id(T row);

    long version(T row);
  }

  private final Identity<T> identity;
  private final LinkedHashMap<String, T> rows = new LinkedHashMap<>();

  public FeedWindow(Identity<T> i) {
    identity = i;
  }

  public void append(List<T> page, boolean prepend) {
    LinkedHashMap<String, T> merged = new LinkedHashMap<>();
    if (prepend) for (T t : page) merged.put(identity.id(t), t);
    merged.putAll(rows);
    for (T t : page) {
      String id = identity.id(t);
      T old = merged.get(id);
      if (old == null || identity.version(t) >= identity.version(old)) merged.put(id, t);
    }
    while (merged.size() > 100) {
      String remove =
          prepend
              ? new ArrayList<>(merged.keySet()).get(merged.size() - 1)
              : merged.keySet().iterator().next();
      merged.remove(remove);
    }
    rows.clear();
    rows.putAll(merged);
  }

  public void replace(List<T> page) {
    clear();
    append(page, false);
  }

  public void remove(String id) {
    rows.remove(id);
  }

  public void update(T row) {
    String id = identity.id(row);
    T old = rows.get(id);
    if (old != null && identity.version(row) >= identity.version(old)) rows.put(id, row);
  }

  public List<T> rows() {
    return new ArrayList<>(rows.values());
  }

  public int position(String id) {
    return new ArrayList<>(rows.keySet()).indexOf(id);
  }

  public void clear() {
    rows.clear();
  }
}
