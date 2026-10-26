package com.gmail.manhle240406.project.foundation;

import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;

import java.util.concurrent.CopyOnWriteArrayList;

/** Observes sessions only. Sign-in/provider policy belongs to F01. */
public final class SessionGateway implements AutoCloseable {
    private final FirebaseAuth auth;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SessionEpoch epoch = new SessionEpoch();
    private final CopyOnWriteArrayList<Runnable> invalidations = new CopyOnWriteArrayList<>();
    private final FirebaseAuth.AuthStateListener listener;

    public SessionGateway(FirebaseAuth auth) {
        this.auth = auth;
        refresh();
        listener = ignored -> refresh();
        auth.addAuthStateListener(listener);
    }

    private void refresh() {
        SessionEpoch.Ticket previous = epoch.capture();
        com.google.firebase.auth.FirebaseUser user = auth.getCurrentUser();
        epoch.change(user == null ? null : user.getUid());
        if (!epoch.current(previous)) notifyInvalidations();
    }

    private void notifyInvalidations() {
        for (Runnable action : invalidations) {
            if (Looper.myLooper() == Looper.getMainLooper()) action.run();
            else main.post(action);
        }
    }

    public SessionEpoch.Ticket capture() {
        refresh();
        return epoch.capture();
    }

    public boolean current(SessionEpoch.Ticket ticket) {
        com.google.firebase.auth.FirebaseUser user = auth.getCurrentUser();
        String currentUid = user == null ? null : user.getUid();
        return epoch.current(ticket) && java.util.Objects.equals(ticket.uid, currentUid);
    }

    public void onInvalidated(Runnable action) {
        invalidations.add(action);
    }

    public void removeInvalidation(Runnable action) {
        invalidations.remove(action);
    }

    public void logout() {
        epoch.invalidate();
        notifyInvalidations();
        auth.signOut();
        refresh();
    }

    @Override
    public void close() {
        auth.removeAuthStateListener(listener);
        epoch.invalidate();
        notifyInvalidations();
        invalidations.clear();
    }
}
