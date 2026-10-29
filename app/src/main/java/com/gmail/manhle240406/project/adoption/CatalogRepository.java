package com.gmail.manhle240406.project.adoption;

import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Signed-in selectors only. No mobile Admin writer or authoritative pet reference mutation. */
public final class CatalogRepository {
    private final FirebaseFirestore db;
    private final SessionGateway sessions;

    public CatalogRepository(FirebaseFirestore db, SessionGateway s) {
        this.db = db;
        sessions = s;
    }

    private void current(SessionEpoch.Ticket t) {
        if (t.uid == null || !sessions.current(t))
            throw new IllegalStateException("session_changed");
    }

    private String collection(String kind) {
        switch (kind) {
            case "species":
                return "catalogSpecies";
            case "breed":
                return "catalogBreeds";
            case "region":
                return "catalogRegions";
            default:
                throw new IllegalArgumentException("catalog_kind");
        }
    }

    private <T> Task<T> scoped(SessionEpoch.Ticket t, Task<T> task) {
        current(t);
        return Tasks.withTimeout(task, 15, TimeUnit.SECONDS)
                .continueWith(
                        done -> {
                            current(t);
                            if (!done.isSuccessful())
                                throw Objects.requireNonNull(done.getException());
                            return done.getResult();
                        });
    }

    public Task<DocumentSnapshot> get(SessionEpoch.Ticket t, String kind, String id) {
        current(t);
        return scoped(
                t,
                db.collection(collection(kind))
                        .document(AdoptionValues.catalogId(id))
                        .get(Source.SERVER));
    }

    public Task<QuerySnapshot> page(
            SessionEpoch.Ticket t, String kind, String speciesId, DocumentSnapshot cursor) {
        current(t);
        Query q = db.collection(collection(kind)).whereEqualTo("state", "active");
        if ("breed".equals(kind))
            q = q.whereEqualTo("speciesId", AdoptionValues.catalogId(speciesId));
        q = q.orderBy("sortKey").orderBy(FieldPath.documentId()).limit(50);
        if (cursor != null) {
            if (!cursor.getReference().getParent().getPath().equals(collection(kind)))
                throw new IllegalArgumentException("foreign_cursor");
            q = q.startAfter(cursor);
        }
        return scoped(t, q.get(Source.SERVER));
    }

    public Task<QuerySnapshot> history(
            SessionEpoch.Ticket t, String kind, String id, DocumentSnapshot cursor) {
        current(t);
        String path = collection(kind) + "/" + AdoptionValues.catalogId(id) + "/revisions";
        Query q = db.collection(path).orderBy("version", Query.Direction.DESCENDING).limit(50);
        if (cursor != null) {
            if (!cursor.getReference().getParent().getPath().equals(path))
                throw new IllegalArgumentException("foreign_cursor");
            q = q.startAfter(cursor);
        }
        return scoped(t, q.get(Source.SERVER));
    }
}
