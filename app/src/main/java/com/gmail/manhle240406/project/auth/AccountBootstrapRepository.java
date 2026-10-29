package com.gmail.manhle240406.project.auth;

import com.gmail.manhle240406.project.foundation.SessionEpoch;
import com.gmail.manhle240406.project.foundation.SessionGateway;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class AccountBootstrapRepository {
    private final FirebaseFirestore db;
    private final SessionGateway sessions;

    public AccountBootstrapRepository(FirebaseFirestore db, SessionGateway sessions) {
        this.db = db;
        this.sessions = sessions;
    }

    public Task<Boolean> ensureAccount() {
        SessionEpoch.Ticket ticket = sessions.capture();
        if (ticket.uid == null) return Tasks.forResult(false);
        DocumentReference ref = db.collection("users").document(ticket.uid);
        return ref.get(Source.CACHE)
                .continueWithTask(
                        cached -> {
                            if (sessions.current(ticket)
                                    && cached.isSuccessful()
                                    && valid(cached.getResult(), ticket.uid)) {
                                ensureOnline(
                                        ticket,
                                        ref); // Refresh opportunistically; cached identity only
                                // opens read surfaces.
                                return Tasks.forResult(true);
                            }
                            return ensureOnline(ticket, ref);
                        });
    }

    /** Revalidates the account root on the server before a protected write. */
    public Task<Boolean> ensureAccountOnline() {
        SessionEpoch.Ticket ticket = sessions.capture();
        if (ticket.uid == null) return Tasks.forResult(false);
        return ensureOnline(ticket, db.collection("users").document(ticket.uid));
    }

    private Task<Boolean> ensureOnline(SessionEpoch.Ticket ticket, DocumentReference ref) {
        return Tasks.withTimeout(
                        db.runTransaction(
                                new TransactionOptions.Builder().setMaxAttempts(3).build(),
                                tx -> {
                                    if (!sessions.current(ticket))
                                        throw new FirebaseFirestoreException(
                                                "Session changed",
                                                FirebaseFirestoreException.Code.UNAUTHENTICATED);
                                    DocumentSnapshot snapshot = tx.get(ref);
                                    if (snapshot.exists()) {
                                        Map<String, Object> data = snapshot.getData();
                                        if (data == null
                                                || data.size() != 3
                                                || !ticket.uid.equals(data.get("uid"))
                                                || !Long.valueOf(1)
                                                        .equals(data.get("schemaVersion"))
                                                || !(data.get("createdAt") instanceof Timestamp))
                                            throw new FirebaseFirestoreException(
                                                    "Invalid account schema",
                                                    FirebaseFirestoreException.Code
                                                            .FAILED_PRECONDITION);
                                    } else {
                                        Map<String, Object> data = new HashMap<>();
                                        data.put("uid", ticket.uid);
                                        data.put("schemaVersion", 1L);
                                        data.put("createdAt", FieldValue.serverTimestamp());
                                        tx.set(ref, data);
                                    }
                                    return true;
                                }),
                        15,
                        TimeUnit.SECONDS)
                .continueWith(
                        task ->
                                sessions.current(ticket)
                                        && task.isSuccessful()
                                        && task.getResult());
    }

    private static boolean valid(DocumentSnapshot snapshot, String uid) {
        if (snapshot == null || !snapshot.exists()) return false;
        Map<String, Object> data = snapshot.getData();
        return data != null
                && data.size() == 3
                && uid.equals(data.get("uid"))
                && Long.valueOf(1).equals(data.get("schemaVersion"))
                && data.get("createdAt") instanceof Timestamp;
    }
}
