package com.gmail.manhle240406.project.adoption;

import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** F10 typed trusted-command port. No role provisioning or publication writer integration. */
public final class ListingReviewRepository {
    public interface Commands {
        Task<Map<String, Object>> call(String command, Map<String, Object> intent);
    }

    public static final Commands UNAVAILABLE =
            (command, intent) ->
                    Tasks.forException(new IllegalStateException("review_adapter_not_authorized"));
    private final FirebaseFirestore db;
    private final SessionGateway sessions;
    private final Commands commands;

    public ListingReviewRepository(FirebaseFirestore db, SessionGateway s, Commands c) {
        this.db = db;
        sessions = s;
        commands = Objects.requireNonNull(c);
    }

    private void current(SessionEpoch.Ticket t) {
        if (t.uid == null || !sessions.current(t))
            throw new IllegalStateException("session_changed");
    }

    public Task<DocumentSnapshot> request(SessionEpoch.Ticket t, String listing, long version) {
        current(t);
        if (version < 1) throw new IllegalArgumentException("review_version");
        return Tasks.withTimeout(
                        db.document(
                                        "listingReviewRequests/"
                                                + AdoptionValues.listingId(listing)
                                                + "/versions/v"
                                                + version)
                                .get(Source.SERVER),
                        15,
                        TimeUnit.SECONDS)
                .continueWith(
                        done -> {
                            current(t);
                            if (!done.isSuccessful())
                                throw Objects.requireNonNull(done.getException());
                            return done.getResult();
                        });
    }

    public Task<QuerySnapshot> decisions(
            SessionEpoch.Ticket t, String listing, long version, DocumentSnapshot cursor) {
        current(t);
        if (version < 1) throw new IllegalArgumentException("review_version");
        String path =
                "listingReviews/"
                        + AdoptionValues.listingId(listing)
                        + "/versions/v"
                        + version
                        + "/decisions";
        Query q =
                db.collection(path)
                        .orderBy("serverCreatedAt", Query.Direction.DESCENDING)
                        .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                        .limit(20);
        if (cursor != null) {
            if (!cursor.getReference().getParent().getPath().equals(path))
                throw new IllegalArgumentException("foreign_cursor");
            q = q.startAfter(cursor);
        }
        return Tasks.withTimeout(q.get(Source.SERVER), 15, TimeUnit.SECONDS)
                .continueWith(
                        done -> {
                            current(t);
                            if (!done.isSuccessful())
                                throw Objects.requireNonNull(done.getException());
                            return done.getResult();
                        });
    }

    public Task<Map<String, Object>> execute(
            SessionEpoch.Ticket t, String command, Map<String, Object> input) {
        current(t);
        if (!Arrays.asList("submit", "cancel", "assign", "decide", "revoke").contains(command)
                || !command.equals(input.get("command")))
            throw new IllegalArgumentException("review_command");
        return Tasks.withTimeout(
                        commands.call(
                                command,
                                com.gmail.manhle240406.project.listing.ListingSchema.freeze(input)),
                        15,
                        TimeUnit.SECONDS)
                .continueWith(
                        done -> {
                            current(t);
                            if (!done.isSuccessful())
                                throw Objects.requireNonNull(done.getException());
                            return done.getResult();
                        });
    }
}
