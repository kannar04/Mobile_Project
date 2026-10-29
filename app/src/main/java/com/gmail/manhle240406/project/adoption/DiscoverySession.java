package com.gmail.manhle240406.project.adoption;

import com.gmail.manhle240406.project.community.FeedWindow;

import java.util.*;

/** UID/query-generation state only. No presentation or durable search/browse history. */
public final class DiscoverySession<T> implements AutoCloseable {
    private final FeedWindow<T> window;
    private final FeedWindow.Identity<T> identity;
    private String uid, signature, anchor;
    private int offset;
    private long generation;
    private final Set<String> inflight = new HashSet<>();
    private final List<T> pendingHead = new ArrayList<>();
    private final LinkedHashMap<String, String> checkpoints = new LinkedHashMap<>();
    private com.gmail.manhle240406.project.foundation.SessionGateway sessions;
    private final Runnable invalidation = this::clear;

    public DiscoverySession(FeedWindow.Identity<T> identity) {
        this.identity = identity;
        window = new FeedWindow<>(identity);
    }

    public DiscoverySession(
            FeedWindow.Identity<T> identity,
            com.gmail.manhle240406.project.foundation.SessionGateway sessions) {
        this(identity);
        this.sessions = sessions;
        sessions.onInvalidated(invalidation);
    }

    public synchronized long apply(String owner, DiscoveryQuery query) {
        if (owner == null) throw new IllegalArgumentException("signed_in_required");
        if (!Objects.equals(uid, owner)) {
            clear();
            uid = owner;
        }
        if (!Objects.equals(signature, query.signature)) {
            signature = query.signature;
            generation++;
            window.clear();
            pendingHead.clear();
            inflight.clear();
            anchor = null;
            offset = 0;
        }
        return generation;
    }

    public synchronized boolean begin(long expected, String cursor) {
        return expected == generation
                && uid != null
                && inflight.add(cursor == null ? "head" : cursor);
    }

    public synchronized boolean accept(long expected, String cursor, List<T> page) {
        if (expected != generation || uid == null) return false;
        if (page.size() > 20) throw new IllegalArgumentException("page_limit");
        inflight.remove(cursor == null ? "head" : cursor);
        window.append(page, false);
        repairAnchor();
        return true;
    }

    public synchronized void failed(long expected, String cursor) {
        if (expected == generation) inflight.remove(cursor == null ? "head" : cursor);
    }

    public synchronized void head(long expected, List<T> page) {
        if (expected != generation) return;
        if (page.size() > 20) throw new IllegalArgumentException("page_limit");
        pendingHead.clear();
        pendingHead.addAll(page);
    }

    public synchronized void refresh(long expected) {
        if (expected != generation) return;
        window.append(pendingHead, true);
        pendingHead.clear();
        repairAnchor();
    }

    public synchronized List<T> rows() {
        return window.rows();
    }

    public synchronized void anchor(String id, int offset) {
        if (window.position(id) < 0) throw new IllegalArgumentException("missing_anchor");
        anchor = id;
        this.offset = offset;
        checkpoints.put(signature, id);
        while (checkpoints.size() > 3) checkpoints.remove(checkpoints.keySet().iterator().next());
    }

    public synchronized String anchor() {
        return anchor;
    }

    public synchronized int offset() {
        return offset;
    }

    public synchronized void remove(String id) {
        int old = window.position(id);
        window.remove(id);
        if (Objects.equals(anchor, id)) {
            List<T> rows = window.rows();
            anchor =
                    rows.isEmpty()
                            ? null
                            : identity.id(rows.get(Math.min(Math.max(old, 0), rows.size() - 1)));
            offset = 0;
            checkpoints.remove(signature);
        }
    }

    private void repairAnchor() {
        if (anchor != null && window.position(anchor) < 0) {
            List<T> rows = window.rows();
            anchor = rows.isEmpty() ? null : identity.id(rows.get(0));
            offset = 0;
        }
    }

    public synchronized void clear() {
        window.clear();
        inflight.clear();
        pendingHead.clear();
        checkpoints.clear();
        uid = null;
        signature = null;
        anchor = null;
        offset = 0;
        generation++;
    }

    @Override
    public synchronized void close() {
        if (sessions != null) {
            sessions.removeInvalidation(invalidation);
            sessions = null;
        }
        clear();
    }
}
