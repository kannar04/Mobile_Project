package com.gmail.manhle240406.project.community;

import com.gmail.manhle240406.project.foundation.*;
import com.gmail.manhle240406.project.profile.ProfileRepository;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;

import java.util.*;

/**
 * At most 20 session-memory aliases; other readers only receive published identity; own draft
 * identity stays viewer-scoped.
 */
public final class AuthorLookup implements AutoCloseable {
    private final SessionGateway sessions;
    private final SessionEpoch.Ticket ticket;
    private final ProfileRepository profiles;
    private final FirebaseFirestore db;
    private final Map<String, Task<Map<String, Object>>> pending = new HashMap<>();
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private int active;
    private final Map<String, TaskCompletionSource<Map<String, Object>>> waiters = new HashMap<>();
    private long generation;
    private long aliasEpoch = SessionAliases.generation();
    private final Runnable invalidation = this::clear;

    public AuthorLookup(SessionGateway sessions) {
        this.sessions = sessions;
        ticket = sessions.capture();
        db = FirebaseFirestore.getInstance();
        profiles = new ProfileRepository(db, sessions);
        sessions.onInvalidated(invalidation);
    }

    public String identityGeneration() {
        return System.identityHashCode(this) + ":" + generation + ":" + SessionAliases.generation();
    }

    public void clear() {
        SessionAliases.clear();
        aliasEpoch = SessionAliases.generation();
        clearLocal();
    }

    private void clearLocal() {
        generation++;
        pending.clear();
        queue.clear();
        for (TaskCompletionSource<Map<String, Object>> w : waiters.values()) w.trySetResult(null);
        waiters.clear();
    }

    public Task<Map<String, Object>> get(String uid) {
        if (!sessions.current(ticket)) return Tasks.forResult(null);
        long epoch = SessionAliases.generation();
        if (aliasEpoch != epoch) {
            aliasEpoch = epoch;
            clearLocal();
        }
        SessionAliases.Entry shared =
                SessionAliases.get(ticket.uid, uid, android.os.SystemClock.elapsedRealtime());
        if (shared != null) return Tasks.forResult(shared.value);
        if (pending.containsKey(uid)) return pending.get(uid);
        if (pending.size() >= 100) return Tasks.forResult(null);
        if (uid == null || uid.isEmpty()) return Tasks.forResult(null);
        final long g = generation, aliasGeneration = SessionAliases.generation();
        TaskCompletionSource<Map<String, Object>> waiter = new TaskCompletionSource<>();
        pending.put(uid, waiter.getTask());
        waiters.put(uid, waiter);
        queue.add(() -> read(uid, g, aliasGeneration, waiter));
        pump();
        return waiter.getTask();
    }

    private void pump() {
        while (active < 4 && !queue.isEmpty()) {
            active++;
            queue.remove().run();
        }
    }

    private void read(
            String uid,
            long g,
            long aliasGeneration,
            TaskCompletionSource<Map<String, Object>> waiter) {
        Task<Map<String, Object>> task;
        if (uid.equals(ticket.uid))
            task =
                    profiles.ownDraft(ticket)
                            .continueWith(
                                    r ->
                                            g == generation
                                                            && r.isSuccessful()
                                                            && r.getResult().code
                                                                    == ProfileRepository.Code.OK
                                                    ? AuthorProfile.own(
                                                            ticket.uid, uid, r.getResult().value)
                                                    : null);
        else
            task =
                    profiles.readPublic(ticket, uid)
                            .continueWith(
                                    r ->
                                            g == generation
                                                            && r.isSuccessful()
                                                            && r.getResult().code
                                                                    == ProfileRepository.Code.OK
                                                    ? r.getResult().value
                                                    : null);
        task.addOnCompleteListener(
                r -> {
                    active--;
                    if (sessions.current(ticket) && g == generation) {
                        pending.remove(uid);
                        waiters.remove(uid);
                        Map<String, Object> result =
                                aliasGeneration == SessionAliases.generation() && r.isSuccessful()
                                        ? r.getResult()
                                        : null;
                        if (aliasGeneration == SessionAliases.generation())
                            SessionAliases.put(
                                    ticket.uid,
                                    uid,
                                    result,
                                    android.os.SystemClock.elapsedRealtime());
                        waiter.trySetResult(result);
                    } else waiter.trySetResult(null);
                    pump();
                });
    }

    @Override
    public void close() {
        clearLocal();
        sessions.removeInvalidation(invalidation);
    }
}
