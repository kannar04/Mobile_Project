package com.gmail.manhle240406.project.foundation;

import static com.gmail.manhle240406.project.foundation.FoundationResult.Status.*;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class FirestoreFoundationRepository implements FoundationRepository {
    private final FirebaseFirestore db;
    private final SessionGateway sessions;

    public FirestoreFoundationRepository(FirebaseFirestore db, SessionGateway sessions) {
        this.db = db;
        this.sessions = sessions;
    }

    private DocumentReference operation(String uid, String id) {
        return db.collection("foundationChecks")
                .document(uid)
                .collection("operations")
                .document(id);
    }

    private static FoundationResult.Status failure(Exception error, boolean write) {
        if (error instanceof TimeoutException) return write ? CHECKING_OUTCOME : UNAVAILABLE;
        if (error instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) error).getCode()) {
                case PERMISSION_DENIED:
                    return PERMISSION_DENIED;
                case UNAUTHENTICATED:
                    return SIGN_IN_REQUIRED;
                case FAILED_PRECONDITION:
                    return MISSING_INDEX;
                case ABORTED:
                    return CONFLICT;
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return write ? CHECKING_OUTCOME : UNAVAILABLE;
                default:
                    return UNAVAILABLE;
            }
        }
        return error instanceof IllegalArgumentException ? CONFIGURATION_ERROR : UNAVAILABLE;
    }

    private static <T> Task<T> bounded(Task<T> task) {
        // Timeout never claims cancellation of the underlying remote operation.
        return Tasks.withTimeout(task, 15, TimeUnit.SECONDS);
    }

    private <T> Task<FoundationResult<T>> signedOut() {
        return Tasks.forResult(FoundationResult.error(SIGN_IN_REQUIRED));
    }

    @Override
    public Task<FoundationResult<FoundationDocuments.Config>> readConfig(ReadSource source) {
        if (source == null) return Tasks.forResult(FoundationResult.error(INVALID_INPUT));
        return bounded(
                        db.document("system/config")
                                .get(source == ReadSource.SERVER ? Source.SERVER : Source.CACHE))
                .continueWith(
                        task -> {
                            if (!task.isSuccessful())
                                return FoundationResult.error(
                                        source == ReadSource.CACHE
                                                ? CACHE_MISS
                                                : failure(task.getException(), false));
                            DocumentSnapshot snapshot = task.getResult();
                            if (!snapshot.exists())
                                return FoundationResult.error(
                                        source == ReadSource.CACHE ? CACHE_MISS : NOT_FOUND);
                            try {
                                return FoundationResult.of(
                                        snapshot.getMetadata().isFromCache() ? CACHED : SERVER,
                                        new FoundationDocuments.Config(snapshot.getData()));
                            } catch (IllegalArgumentException e) {
                                return FoundationResult.error(CONFIGURATION_ERROR);
                            }
                        });
    }

    @Override
    public Task<FoundationResult<FoundationDocuments.Authority>> readMyAuthority() {
        SessionEpoch.Ticket ticket = sessions.capture();
        if (ticket.uid == null) return signedOut();
        return bounded(db.collection("securitySubjects").document(ticket.uid).get(Source.SERVER))
                .continueWith(
                        task -> {
                            if (!sessions.current(ticket))
                                return FoundationResult.error(SESSION_CHANGED);
                            if (!task.isSuccessful())
                                return FoundationResult.error(failure(task.getException(), false));
                            if (!task.getResult().exists())
                                return FoundationResult.error(NOT_FOUND);
                            try {
                                FoundationDocuments.Authority authority =
                                        new FoundationDocuments.Authority(
                                                task.getResult().getData());
                                return FoundationResult.of(
                                        authority.active ? SERVER : RESTRICTED, authority);
                            } catch (IllegalArgumentException e) {
                                return FoundationResult.error(CONFIGURATION_ERROR);
                            }
                        });
    }

    @Override
    public Task<FoundationResult<FoundationDocuments.Diagnostic>> runDiagnostic(String id) {
        if (!FoundationDocuments.validOperationId(id))
            return Tasks.forResult(FoundationResult.error(INVALID_INPUT));
        SessionEpoch.Ticket ticket = sessions.capture();
        if (ticket.uid == null) return signedOut();
        DocumentReference ref = operation(ticket.uid, id);
        return bounded(
                        db.runTransaction(
                                new TransactionOptions.Builder().setMaxAttempts(3).build(),
                                transaction -> {
                                    if (!sessions.current(ticket))
                                        throw new FirebaseFirestoreException(
                                                "Session changed",
                                                FirebaseFirestoreException.Code.UNAUTHENTICATED);
                                    // Check current authority even on an existing-operation retry.
                                    // No cached authority.
                                    DocumentSnapshot subject =
                                            transaction.get(
                                                    db.collection("securitySubjects")
                                                            .document(ticket.uid));
                                    if (!subject.exists()
                                            || !new FoundationDocuments.Authority(subject.getData())
                                                    .active)
                                        throw new FirebaseFirestoreException(
                                                "Restricted diagnostic",
                                                FirebaseFirestoreException.Code.PERMISSION_DENIED);
                                    DocumentSnapshot existing = transaction.get(ref);
                                    if (existing.exists()) {
                                        try {
                                            new FoundationDocuments.Diagnostic(
                                                    existing.getData(), ticket.uid, id);
                                        } catch (IllegalArgumentException e) {
                                            throw new FirebaseFirestoreException(
                                                    "Conflicting diagnostic",
                                                    FirebaseFirestoreException.Code.ABORTED);
                                        }
                                    } else {
                                        Map<String, Object> fields = new HashMap<>();
                                        fields.put("ownerUid", ticket.uid);
                                        fields.put("operationId", id);
                                        fields.put("schemaVersion", 1L);
                                        fields.put("createdAt", FieldValue.serverTimestamp());
                                        transaction.set(ref, fields);
                                    }
                                    return null;
                                }))
                .continueWithTask(
                        task -> {
                            if (!sessions.current(ticket))
                                return Tasks.forResult(FoundationResult.error(SESSION_CHANGED));
                            if (!task.isSuccessful())
                                return Tasks.forResult(
                                        FoundationResult.error(failure(task.getException(), true)));
                            // A server reread supplies the resolved timestamp, including on safe
                            // retries.
                            return bounded(ref.get(Source.SERVER))
                                    .continueWith(
                                            read -> {
                                                if (!sessions.current(ticket))
                                                    return FoundationResult.error(SESSION_CHANGED);
                                                if (!read.isSuccessful())
                                                    return FoundationResult.error(CHECKING_OUTCOME);
                                                if (!read.getResult().exists())
                                                    return FoundationResult.error(CONFLICT);
                                                try {
                                                    return FoundationResult.of(
                                                            COMMITTED,
                                                            new FoundationDocuments.Diagnostic(
                                                                    read.getResult().getData(),
                                                                    ticket.uid,
                                                                    id));
                                                } catch (IllegalArgumentException e) {
                                                    return FoundationResult.error(CONFLICT);
                                                }
                                            });
                        });
    }

    @Override
    public Task<FoundationResult<List<FoundationDocuments.Diagnostic>>> listMyDiagnostics(
            Cursor cursor) {
        SessionEpoch.Ticket ticket = sessions.capture();
        if (ticket.uid == null) return signedOut();
        Query query =
                db.collection("foundationChecks")
                        .document(ticket.uid)
                        .collection("operations")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                        .limit(20);
        if (cursor != null) {
            if (cursor.createdAt == null
                    || !FoundationDocuments.validOperationId(cursor.operationId))
                return Tasks.forResult(FoundationResult.error(INVALID_INPUT));
            query = query.startAfter(cursor.createdAt, cursor.operationId);
        }
        return bounded(query.get(Source.SERVER))
                .continueWith(
                        task -> {
                            if (!sessions.current(ticket))
                                return FoundationResult.error(SESSION_CHANGED);
                            if (!task.isSuccessful())
                                return FoundationResult.error(failure(task.getException(), false));
                            List<FoundationDocuments.Diagnostic> values = new ArrayList<>();
                            try {
                                for (DocumentSnapshot snapshot : task.getResult())
                                    values.add(
                                            new FoundationDocuments.Diagnostic(
                                                    snapshot.getData(),
                                                    ticket.uid,
                                                    snapshot.getId()));
                                return FoundationResult.of(
                                        SERVER, Collections.unmodifiableList(values));
                            } catch (IllegalArgumentException e) {
                                return FoundationResult.error(CONFIGURATION_ERROR);
                            }
                        });
    }

    @Override
    public Task<FoundationResult<Void>> clearMyDiagnostic(String id) {
        if (!FoundationDocuments.validOperationId(id))
            return Tasks.forResult(FoundationResult.error(INVALID_INPUT));
        SessionEpoch.Ticket ticket = sessions.capture();
        if (ticket.uid == null) return signedOut();
        DocumentReference ref = operation(ticket.uid, id);
        // Online transaction avoids an offline queued destructive command.
        return bounded(
                        db.runTransaction(
                                new TransactionOptions.Builder().setMaxAttempts(3).build(),
                                transaction -> {
                                    if (!sessions.current(ticket))
                                        throw new FirebaseFirestoreException(
                                                "Session changed",
                                                FirebaseFirestoreException.Code.UNAUTHENTICATED);
                                    DocumentSnapshot subject =
                                            transaction.get(
                                                    db.collection("securitySubjects")
                                                            .document(ticket.uid));
                                    if (!subject.exists()
                                            || !new FoundationDocuments.Authority(subject.getData())
                                                    .active)
                                        throw new FirebaseFirestoreException(
                                                "Restricted diagnostic",
                                                FirebaseFirestoreException.Code.PERMISSION_DENIED);
                                    DocumentSnapshot existing = transaction.get(ref);
                                    if (existing.exists()) transaction.delete(ref);
                                    return null;
                                }))
                .continueWith(
                        task -> {
                            if (!sessions.current(ticket))
                                return FoundationResult.error(SESSION_CHANGED);
                            return task.isSuccessful()
                                    ? FoundationResult.of(COMMITTED, null)
                                    : FoundationResult.error(failure(task.getException(), true));
                        });
    }
}
