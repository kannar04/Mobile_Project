package com.gmail.manhle240406.project.media;

import android.app.Application;
import android.net.Uri;

import androidx.lifecycle.*;

import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;

import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** Own private avatar choice; no provider URI survives this session. */
public final class AvatarViewModel extends AndroidViewModel {
    public final MutableLiveData<Long> changes = new MutableLiveData<>(0L);
    public String assetId;
    public long version;
    public boolean busy, lost, loaded;
    public String error = "";
    private long event;
    private final SessionGateway sessions;
    private final SessionEpoch.Ticket ticket;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private CandidatePreparer preparer;
    private Runnable pendingRetry;
    private CandidatePreparer.Candidate pendingCandidate;
    private final Runnable invalidation =
            () -> {
                lost = true;
                assetId = null;
                busy = false;
                changed();
            };

    public AvatarViewModel(Application app) {
        super(app);
        sessions = ((AniloveApplication) app).foundation().sessions;
        ticket = sessions == null ? null : sessions.capture();
        if (ticket == null || ticket.uid == null) {
            lost = true;
            return;
        }
        sessions.onInvalidated(invalidation);
        try {
            preparer = new CandidatePreparer(app, sessions);
        } catch (java.io.IOException e) {
            error = "unavailable";
        }
        load(((AniloveApplication) app).auth().consumeNewGoogleRegistration(ticket.uid));
    }

    private boolean current() {
        return !lost && ticket != null && sessions.current(ticket);
    }

    private void changed() {
        changes.setValue(++event);
    }

    public void load(boolean auto) {
        if (!current() || busy) return;
        FirebaseFirestore.getInstance()
                .document("users/" + ticket.uid + "/private/avatar")
                .get(Source.SERVER)
                .addOnCompleteListener(
                        t -> {
                            if (!current()) return;
                            if (t.isSuccessful()) {
                                loaded = true;
                                Map<String, Object> m = t.getResult().getData();
                                assetId = m == null ? null : (String) m.get("assetId");
                                version =
                                        m == null
                                                ? 0
                                                : ((Number) m.get("choiceVersion")).longValue();
                                changed();
                                if (auto && m == null) google(true);
                            } else {
                                error = "unavailable";
                                changed();
                            }
                        });
    }

    public boolean hasPending() {
        return pendingRetry != null;
    }

    public void retry() {
        if (current() && !busy && pendingRetry != null) pendingRetry.run();
    }

    private void begin() {
        busy = true;
        error = "";
        changed();
    }

    public void choose(Uri uri) {
        if (!current() || busy || pendingRetry != null || !loaded || preparer == null) return;
        String id = UUID.randomUUID().toString(),
                op = UUID.randomUUID().toString(),
                bind = UUID.randomUUID().toString();
        long expected = version;
        pendingRetry =
                () -> {
                    begin();
                    Tasks.call(
                                    worker,
                                    () -> {
                                        if (pendingCandidate == null)
                                            pendingCandidate =
                                                    preparer.prepare(
                                                            ticket,
                                                            getApplication()
                                                                    .getContentResolver()
                                                                    .openInputStream(uri));
                                        return pendingCandidate;
                                    })
                            .continueWithTask(
                                    worker,
                                    t -> {
                                        CandidatePreparer.Candidate c = t.getResult();
                                        byte[] bytes = Files.readAllBytes(c.file.toPath());
                                        return ManagedMediaClient.call(
                                                        "reserve",
                                                        ManagedMediaClient.map(
                                                                "assetId",
                                                                id,
                                                                "operationId",
                                                                op,
                                                                "purpose",
                                                                "profile_avatar",
                                                                "inputHash",
                                                                c.sha256))
                                                .continueWithTask(
                                                        worker,
                                                        r -> {
                                                            r.getResult();
                                                            return ManagedMediaClient.upload(
                                                                    id, bytes);
                                                        });
                                    })
                            .continueWithTask(
                                    worker,
                                    t -> {
                                        t.getResult();
                                        if (!current())
                                            return Tasks.forException(
                                                    new IllegalStateException("session_changed"));
                                        return ManagedMediaClient.call(
                                                "avatar",
                                                ManagedMediaClient.map(
                                                        "operationId",
                                                        bind,
                                                        "expectedVersion",
                                                        expected,
                                                        "assetId",
                                                        id));
                                    })
                            .addOnCompleteListener(this::complete);
                };
        pendingRetry.run();
    }

    public void remove() {
        if (!current() || busy || pendingRetry != null || !loaded) return;
        Map<String, Object> intent =
                ManagedMediaClient.map(
                        "operationId",
                        UUID.randomUUID().toString(),
                        "expectedVersion",
                        version,
                        "assetId",
                        null);
        pendingRetry =
                () -> {
                    begin();
                    ManagedMediaClient.call("avatar", intent).addOnCompleteListener(this::complete);
                };
        pendingRetry.run();
    }

    public void google(boolean automatic) {
        if (!current() || busy || pendingRetry != null || !loaded) return;
        Map<String, Object> intent =
                ManagedMediaClient.map(
                        "operationId",
                        UUID.randomUUID().toString(),
                        "expectedVersion",
                        version,
                        "newRegistration",
                        automatic);
        pendingRetry =
                () -> {
                    begin();
                    ManagedMediaClient.call("google", intent).addOnCompleteListener(this::complete);
                };
        pendingRetry.run();
    }

    private void complete(Task<Map<String, Object>> t) {
        if (!current()) return;
        busy = false;
        if (t.isSuccessful()) {
            Map<String, Object> r = t.getResult();
            assetId = (String) r.get("assetId");
            version = ((Number) r.get("choiceVersion")).longValue();
            error = "";
            pendingRetry = null;
            if (pendingCandidate != null) {
                preparer.release(pendingCandidate);
                pendingCandidate = null;
            }
        } else {
            error = String.valueOf(t.getException());
            if (error.contains("conflict")
                    || error.contains("not_new_registration")
                    || error.contains("provider_photo_unavailable")
                    || error.contains("invalid_image")
                    || error.contains("capacity_full")
                    || error.contains("daily_quota")
                    || error.contains("outstanding_quota")
                    || error.contains("restricted")
                    || error.contains("not_verified")) {
                pendingRetry = null;
                if (pendingCandidate != null) {
                    preparer.release(pendingCandidate);
                    pendingCandidate = null;
                }
            }
        }
        changed();
        load(false);
    }

    @Override
    protected void onCleared() {
        lost = true;
        if (sessions != null) sessions.removeInvalidation(invalidation);
        if (preparer != null) preparer.close();
        worker.shutdownNow();
        assetId = null;
    }
}
