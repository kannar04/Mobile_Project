package com.gmail.manhle240406.project.foundation;

import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;

import java.util.*;

/** Ordinary participation only. Never writes or substitutes protected domain authority. */
public final class MembershipRepository {
    public enum State {
        READY,
        RESTRICTED,
        UNKNOWN
    }

    private final FirebaseFirestore db;
    private final SessionGateway sessions;

    public MembershipRepository(FirebaseFirestore db, SessionGateway sessions) {
        this.db = db;
        this.sessions = sessions;
    }

    public static boolean valid(Map<String, Object> m, String uid) {
        return m != null
                && m.keySet()
                        .equals(
                                new HashSet<>(
                                        Arrays.asList(
                                                "uid",
                                                "schemaVersion",
                                                "status",
                                                "membershipVersion",
                                                "createdAt",
                                                "updatedAt")))
                && uid.equals(m.get("uid"))
                && Long.valueOf(1).equals(m.get("schemaVersion"))
                && Arrays.asList("active", "restricted").contains(m.get("status"))
                && m.get("membershipVersion") instanceof Long
                && (Long) m.get("membershipVersion") >= 1
                && m.get("createdAt") instanceof Timestamp
                && m.get("updatedAt") instanceof Timestamp;
    }

    public Task<State> ensureBasicMembership(SessionEpoch.Ticket ticket) {
        return com.google.android.gms.tasks.Tasks.withTimeout(
                        db.runTransaction(
                                new TransactionOptions.Builder().setMaxAttempts(3).build(),
                                tx -> {
                                    if (ticket.uid == null || !sessions.current(ticket))
                                        throw new IllegalStateException("session_changed");
                                    String uid = ticket.uid;
                                    Map<String, Object> shell =
                                            tx.get(db.document("users/" + uid)).getData();
                                    Map<String, Object> subject =
                                            tx.get(db.document("securitySubjects/" + uid))
                                                    .getData();
                                    DocumentReference ref =
                                            db.document("communityMemberships/" + uid);
                                    Map<String, Object> member = tx.get(ref).getData();
                                    if (shell == null
                                            || shell.size() != 3
                                            || !uid.equals(shell.get("uid"))
                                            || !Long.valueOf(1).equals(shell.get("schemaVersion"))
                                            || !(shell.get("createdAt") instanceof Timestamp))
                                        return State.UNKNOWN;
                                    if (subject != null
                                            && (!subject.keySet()
                                                            .equals(
                                                                    new HashSet<>(
                                                                            Arrays.asList(
                                                                                    "status",
                                                                                    "roles",
                                                                                    "updatedAt")))
                                                    || !Arrays.asList("active", "restricted")
                                                            .contains(subject.get("status"))
                                                    || !(subject.get("roles") instanceof List)
                                                    || !(subject.get("updatedAt")
                                                            instanceof Timestamp)))
                                        return State.UNKNOWN;
                                    if (subject != null
                                            && "restricted".equals(subject.get("status")))
                                        return State.RESTRICTED;
                                    if (member != null)
                                        return !valid(member, uid)
                                                ? State.UNKNOWN
                                                : "active".equals(member.get("status"))
                                                        ? State.READY
                                                        : State.RESTRICTED;
                                    Map<String, Object> fresh = new HashMap<>();
                                    fresh.put("uid", uid);
                                    fresh.put("schemaVersion", 1L);
                                    fresh.put("status", "active");
                                    fresh.put("membershipVersion", 1L);
                                    fresh.put("createdAt", FieldValue.serverTimestamp());
                                    fresh.put("updatedAt", FieldValue.serverTimestamp());
                                    if (!sessions.current(ticket))
                                        throw new IllegalStateException("session_changed");
                                    tx.set(ref, fresh);
                                    return State.READY;
                                }),
                        15,
                        java.util.concurrent.TimeUnit.SECONDS)
                .continueWith(
                        t ->
                                sessions.current(ticket) && t.isSuccessful()
                                        ? t.getResult()
                                        : State.UNKNOWN);
    }
}
