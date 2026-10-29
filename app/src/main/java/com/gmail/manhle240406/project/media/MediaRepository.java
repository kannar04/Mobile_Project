package com.gmail.manhle240406.project.media;

import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Callable port stays unbound until managed services and consumer adapters are authorized. */
public final class MediaRepository {
    public interface TrustedCommands {
        Task<Map<String, Object>> call(String command, Map<String, Object> input);
    }

    public static final TrustedCommands UNAVAILABLE =
            (command, input) ->
                    Tasks.forException(new IllegalStateException("media_service_not_authorized"));
    private final FirebaseFirestore db;
    private final SessionGateway sessions;
    private final TrustedCommands commands;

    public MediaRepository(
            FirebaseFirestore db, SessionGateway sessions, TrustedCommands commands) {
        this.db = db;
        this.sessions = sessions;
        this.commands = Objects.requireNonNull(commands);
    }

    private void require(SessionEpoch.Ticket ticket) {
        if (ticket.uid == null || !sessions.current(ticket))
            throw new IllegalStateException("session_changed");
    }

    private <T> Task<T> scoped(SessionEpoch.Ticket ticket, Task<T> task) {
        return Tasks.withTimeout(task, 15, TimeUnit.SECONDS)
                .continueWith(
                        done -> {
                            require(ticket);
                            return done.getResult();
                        });
    }

    public Task<QuerySnapshot> mine(SessionEpoch.Ticket ticket, DocumentSnapshot cursor) {
        require(ticket);
        Query q =
                db.collection("mediaAssets")
                        .whereEqualTo("ownerUid", ticket.uid)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                        .limit(20);
        if (cursor != null) {
            if (!cursor.getReference().getParent().getPath().equals("mediaAssets")
                    || !ticket.uid.equals(cursor.getString("ownerUid")))
                throw new IllegalArgumentException("foreign_cursor");
            q = q.startAfter(cursor);
        }
        return scoped(ticket, q.get(Source.SERVER));
    }

    public Task<DocumentSnapshot> asset(SessionEpoch.Ticket ticket, String assetId) {
        require(ticket);
        return scoped(
                ticket, db.document("mediaAssets/" + MediaContract.id(assetId)).get(Source.SERVER));
    }

    public Task<Map<String, Object>> issue(
            SessionEpoch.Ticket ticket, String assetId, String operationId, String inputHash) {
        require(ticket);
        if (!com.gmail.manhle240406.project.wiring.SparkCapabilities.uploadsEnabled())
            return Tasks.forException(new IllegalStateException("media_uploads_paused"));
        Map<String, Object> input = new HashMap<>();
        input.put("assetId", MediaContract.id(assetId));
        input.put("operationId", MediaContract.id(operationId));
        input.put("inputHash", MediaContract.digest(inputHash));
        input.put("purpose", "community_private");
        return scoped(
                ticket, commands.call("issueMediaTicket", Collections.unmodifiableMap(input)));
    }

    public Task<Map<String, Object>> remove(
            SessionEpoch.Ticket ticket, String assetId, String operationId, long expectedVersion) {
        require(ticket);
        if (expectedVersion < 1) throw new IllegalArgumentException("asset_version");
        Map<String, Object> input = new HashMap<>();
        input.put("assetId", MediaContract.id(assetId));
        input.put("operationId", MediaContract.id(operationId));
        input.put("expectedVersion", expectedVersion);
        return scoped(
                ticket, commands.call("requestMediaRemoval", Collections.unmodifiableMap(input)));
    }
}
