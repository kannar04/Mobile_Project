package com.gmail.manhle240406.project.notifications;

import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** F30 owner-only data access; caller supplies a current server-adjusted time. No UI/wiring. */
public final class InboxRepository implements AutoCloseable {
    public static final class Page {
        public final List<DocumentSnapshot> visible;
        public final DocumentSnapshot rawCursor;
        public final boolean moreCandidates;
        public final int inspectedPages;

        Page(List<DocumentSnapshot> rows, DocumentSnapshot cursor, boolean more, int pages) {
            visible = Collections.unmodifiableList(new ArrayList<>(rows));
            rawCursor = cursor;
            moreCandidates = more;
            inspectedPages = pages;
        }
    }

    private final FirebaseFirestore db;
    private final SessionGateway sessions;
    private ListenerRegistration head;
    private final Runnable invalidation = this::detach;

    public InboxRepository(FirebaseFirestore db, SessionGateway gateway) {
        this.db = db;
        sessions = gateway;
        sessions.onInvalidated(invalidation);
    }

    private void require(SessionEpoch.Ticket ticket) {
        if (ticket.uid == null || !sessions.current(ticket))
            throw new IllegalStateException("session_changed");
    }

    private String own(SessionEpoch.Ticket ticket) {
        require(ticket);
        return "users/" + ticket.uid + "/notificationInbox";
    }

    private Query candidates(SessionEpoch.Ticket ticket, DocumentSnapshot cursor) {
        Query q =
                db.collection(own(ticket))
                        .whereIn("state", Arrays.asList("active", "unavailable"))
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                        .limit(InboxContract.PAGE);
        if (cursor != null) {
            if (!cursor.getReference().getParent().getPath().equals(own(ticket)))
                throw new IllegalArgumentException("foreign_cursor");
            q = q.startAfter(cursor);
        }
        return q;
    }

    private <T> Task<T> scoped(SessionEpoch.Ticket ticket, Task<T> task) {
        return Tasks.withTimeout(task, 15, TimeUnit.SECONDS)
                .continueWith(
                        done -> {
                            require(ticket);
                            return done.getResult();
                        });
    }

    public Task<Page> page(SessionEpoch.Ticket ticket, DocumentSnapshot cursor, Timestamp now) {
        return load(ticket, cursor, now, 0);
    }

    private Task<Page> load(
            SessionEpoch.Ticket ticket, DocumentSnapshot cursor, Timestamp now, int inspected) {
        return scoped(ticket, candidates(ticket, cursor).get(Source.SERVER))
                .continueWithTask(
                        done -> {
                            require(ticket);
                            QuerySnapshot raw = done.getResult();
                            List<DocumentSnapshot> visible = new ArrayList<>();
                            for (DocumentSnapshot row : raw) {
                                Timestamp expires = row.getTimestamp("expiresAt");
                                if (expires != null && expires.compareTo(now) > 0) visible.add(row);
                            }
                            DocumentSnapshot last =
                                    raw.isEmpty() ? cursor : raw.getDocuments().get(raw.size() - 1);
                            boolean more = raw.size() == InboxContract.PAGE;
                            int pages = inspected + 1;
                            if (visible.isEmpty() && more && pages < InboxContract.AUTO_PAGES)
                                return load(ticket, last, now, pages);
                            return Tasks.forResult(new Page(visible, last, more, pages));
                        });
    }

    public Task<Boolean> unread(SessionEpoch.Ticket ticket, Timestamp now) {
        return scoped(
                        ticket,
                        db.collection(own(ticket))
                                .whereEqualTo("readAt", null)
                                .whereGreaterThan("expiresAt", now)
                                .orderBy("expiresAt")
                                .limit(1)
                                .get(Source.SERVER))
                .continueWith(
                        done -> {
                            require(ticket);
                            return !done.getResult().isEmpty();
                        });
    }

    /**
     * Resolves current authorization only; future wiring owns navigation and deliberate markRead.
     */
    public Task<Boolean> targetAvailable(SessionEpoch.Ticket ticket, String itemId, Timestamp now) {
        require(ticket);
        String path = own(ticket) + "/" + InboxContract.itemId(itemId);
        return scoped(ticket, db.document(path).get(Source.SERVER))
                .continueWithTask(
                        done -> {
                            require(ticket);
                            DocumentSnapshot row = done.getResult();
                            Timestamp expiry = row.getTimestamp("expiresAt");
                            if (!row.exists()
                                    || !ticket.uid.equals(row.getString("recipientUid"))
                                    || expiry == null
                                    || expiry.compareTo(now) <= 0
                                    || !"active".equals(row.getString("state"))
                                    || !InboxContract.route(
                                            row.getString("kind"),
                                            row.getString("routeType"),
                                            row.getString("targetId"),
                                            row.getString("templateKey")))
                                return Tasks.forResult(false);
                            String route = row.getString("routeType"),
                                    target = row.getString("targetId");
                            if ("pet".equals(route))
                                return com.gmail.manhle240406.project.adoption.AdoptionApi.call(
                                                "pet",
                                                com.gmail.manhle240406.project.adoption
                                                        .AdoptionFlowModel.map("petId", target))
                                        .continueWith(
                                                fresh -> {
                                                    require(ticket);
                                                    return fresh.isSuccessful();
                                                });
                            if ("inbox".equals(route)) return Tasks.forResult(true);
                            if ("adoption".equals(route))
                                return com.gmail.manhle240406.project.adoption.AdoptionApi.call(
                                                "flowRead",
                                                com.gmail.manhle240406.project.adoption
                                                        .AdoptionFlowModel.map(
                                                        "kind", "detail", "adoptionId", target))
                                        .continueWith(
                                                fresh -> {
                                                    require(ticket);
                                                    return fresh.isSuccessful();
                                                });
                            if ("adoption_request".equals(route))
                                return com.gmail.manhle240406.project.adoption.AdoptionApi.call(
                                                "flowRead",
                                                com.gmail.manhle240406.project.adoption
                                                        .AdoptionFlowModel.map(
                                                        "kind", "request", "requestId", target))
                                        .continueWith(
                                                fresh -> {
                                                    require(ticket);
                                                    return fresh.isSuccessful();
                                                });
                            if ("direct".equals(route))
                                return com.gmail.manhle240406.project.media.ManagedMediaClient.call(
                                                "directThread",
                                                com.gmail.manhle240406.project.media
                                                        .ManagedMediaClient.map(
                                                        "conversationId",
                                                        target,
                                                        "before",
                                                        null,
                                                        "since",
                                                        0))
                                        .continueWith(
                                                fresh -> {
                                                    require(ticket);
                                                    return fresh.isSuccessful();
                                                });
                            String current =
                                    "post".equals(route)
                                            ? "communityFeed/" + target
                                            : "users/" + ticket.uid + "/reportStatus/" + target;
                            return scoped(ticket, db.document(current).get(Source.SERVER))
                                    .continueWith(
                                            fresh -> {
                                                require(ticket);
                                                if (!fresh.isSuccessful()) return false;
                                                DocumentSnapshot value = fresh.getResult();
                                                return value.exists()
                                                        && ("post".equals(route)
                                                                ? com.gmail.manhle240406.project
                                                                        .community.SocialSchema
                                                                        .feed(
                                                                                value.getData(),
                                                                                value.getId())
                                                                : Long.valueOf(1)
                                                                        .equals(
                                                                                value.getLong(
                                                                                        "schemaVersion")));
                                            });
                        });
    }

    public synchronized void listenHead(
            SessionEpoch.Ticket ticket,
            com.google.firebase.firestore.EventListener<QuerySnapshot> callback) {
        require(ticket);
        detach();
        head =
                candidates(ticket, null)
                        .addSnapshotListener(
                                MetadataChanges.INCLUDE,
                                (rows, error) -> {
                                    if (sessions.current(ticket)) callback.onEvent(rows, error);
                                });
    }

    public synchronized void detach() {
        if (head != null) {
            head.remove();
            head = null;
        }
    }

    public Task<Void> markRead(SessionEpoch.Ticket ticket, Map<String, Long> expected) {
        return mark(ticket, InboxContract.readSet(expected), false);
    }

    public Task<Void> dismiss(SessionEpoch.Ticket ticket, String item, long expectedVersion) {
        return mark(
                ticket,
                InboxContract.readSet(Collections.singletonMap(item, expectedVersion)),
                true);
    }

    private Task<Void> mark(
            SessionEpoch.Ticket ticket, Map<String, Long> expected, boolean dismiss) {
        String own = own(ticket);
        Task<Void> task =
                db.runTransaction(
                        new TransactionOptions.Builder().setMaxAttempts(3).build(),
                        tx -> {
                            require(ticket);
                            Map<DocumentReference, Map<String, Object>> changes =
                                    new LinkedHashMap<>();
                            // All reads precede all writes. Up to20 owner rows; no per-row shell
                            // read in rules.
                            for (Map.Entry<String, Long> e : expected.entrySet()) {
                                DocumentReference ref = db.document(own + "/" + e.getKey());
                                DocumentSnapshot row = tx.get(ref);
                                if (!row.exists()
                                        || !ticket.uid.equals(row.getString("recipientUid")))
                                    throw new IllegalStateException("item_unavailable");
                                if (dismiss
                                        ? "dismissed".equals(row.getString("state"))
                                        : row.get("readAt") != null) continue;
                                if (!e.getValue().equals(row.getLong("itemVersion")))
                                    throw new IllegalStateException("version_conflict");
                                Map<String, Object> fields = new HashMap<>();
                                fields.put("itemVersion", e.getValue() + 1);
                                fields.put("updatedAt", FieldValue.serverTimestamp());
                                if (row.get("readAt") == null)
                                    fields.put("readAt", FieldValue.serverTimestamp());
                                if (dismiss) {
                                    fields.put("state", "dismissed");
                                    fields.put("dismissedAt", FieldValue.serverTimestamp());
                                }
                                changes.put(ref, fields);
                            }
                            require(ticket);
                            for (Map.Entry<DocumentReference, Map<String, Object>> e :
                                    changes.entrySet()) tx.update(e.getKey(), e.getValue());
                            return null;
                        });
        return scoped(ticket, task);
    }

    public Task<DocumentSnapshot> preferences(SessionEpoch.Ticket ticket) {
        require(ticket);
        return scoped(
                ticket,
                db.document("users/" + ticket.uid + "/notificationPreferences/main")
                        .get(Source.SERVER));
    }

    public Task<Void> savePreferences(
            SessionEpoch.Ticket ticket,
            long version,
            boolean push,
            boolean comments,
            boolean ownStatus) {
        require(ticket);
        if (version < 0 || (!push && (comments || ownStatus)))
            throw new IllegalArgumentException("preferences");
        DocumentReference ref =
                db.document("users/" + ticket.uid + "/notificationPreferences/main");
        return scoped(
                ticket,
                db.runTransaction(
                        tx -> {
                            require(ticket);
                            DocumentSnapshot old = tx.get(ref);
                            if ((old.exists() ? old.getLong("version") : 0L) != version)
                                throw new IllegalStateException("version_conflict");
                            if (!old.exists() && (push || comments || ownStatus))
                                throw new IllegalArgumentException("initialize_preferences_off");
                            Map<String, Object> value = new HashMap<>();
                            value.put("schemaVersion", 1);
                            value.put("pushEnabled", push);
                            value.put("commentsPush", comments);
                            value.put("ownStatusPush", ownStatus);
                            value.put("version", version + 1);
                            value.put("updatedAt", FieldValue.serverTimestamp());
                            require(ticket);
                            tx.set(ref, value);
                            return null;
                        }));
    }

    @Override
    public void close() {
        detach();
        sessions.removeInvalidation(invalidation);
    }
}
