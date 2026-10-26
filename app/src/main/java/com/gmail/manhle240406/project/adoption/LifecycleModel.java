package com.gmail.manhle240406.project.adoption;

import static com.gmail.manhle240406.project.adoption.AdoptionFlowModel.*;

import android.app.Application;

import androidx.lifecycle.*;

import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.foundation.*;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.util.*;

/** Bounded private task state; exact uncertain commands survive rotation, never cross accounts. */
public final class LifecycleModel extends AndroidViewModel {
    static java.util.function.LongSupplier testClock;

    static long captureTime() {
        return com.gmail.manhle240406.project.BuildConfig.DEBUG && testClock != null
                ? testClock.getAsLong()
                : System.currentTimeMillis();
    }

    public final MutableLiveData<Long> changes = new MutableLiveData<>(0L);
    public final Map<String, String> form = new HashMap<>();
    public Map<String, Object> data = new HashMap<>(), readiness = new HashMap<>();
    public String page = "index",
            id,
            listing,
            role = "adopter",
            after,
            error = "",
            success = "",
            reportId;
    public boolean busy, lost;
    public int cursorPage;
    public final Map<String, Integer> rolePages = new HashMap<>();
    public final Map<String, ArrayList<String>> cursors = new HashMap<>();
    private final SessionGateway sessions;
    public final SessionEpoch.Ticket ticket;
    private final SavedStateHandle saved;
    private Map<String, Object> pending, pendingConsent;
    private long generation, event;
    private final Runnable invalidated = this::clear;

    public LifecycleModel(Application app, SavedStateHandle saved) {
        super(app);
        this.saved = saved;
        sessions = ((AniloveApplication) app).foundation().sessions;
        ticket = sessions.capture();
        sessions.onInvalidated(invalidated);
        if (ticket.uid == null) lost = true;
        if (ticket.uid != null
                && ticket.uid.equals(saved.get("owner"))
                && saved.contains("operation")) error = "pending_reconcile";
    }

    public boolean current() {
        return !lost && sessions.current(ticket);
    }

    public void changed() {
        changes.setValue(++event);
    }

    public boolean canRetry() {
        return pending != null;
    }

    public boolean pending() {
        return saved.contains("operation");
    }

    public void load(String page, Map<String, Object> input) {
        if (busy || !current()) return;
        this.page = page;
        read(input, false);
    }

    public void readiness() {
        read(map("kind", "readiness"), true);
    }

    private void read(Map<String, Object> input, boolean ready) {
        if (busy || !current()) return;
        busy = true;
        error = "";
        long g = generation;
        changed();
        AdoptionApi.call("flowRead", input)
                .addOnCompleteListener(
                        t -> {
                            if (!current() || g != generation) return;
                            busy = false;
                            if (t.isSuccessful()) {
                                if (ready) {
                                    readiness = t.getResult();
                                } else data = t.getResult();
                            } else error = code(t.getException(), false);
                            changed();
                            if (ready && t.isSuccessful() && page.equals("index") && !pending())
                                index(role, cursorPage);
                            else if (!ready
                                    && t.isSuccessful()
                                    && (page.equals("listing") || page.equals("apply"))
                                    && readiness.isEmpty()) readiness();
                        });
    }

    public void index(String role, int position) {
        if (busy || pending()) return;
        this.role = role;
        ArrayList<String> c =
                cursors.computeIfAbsent(
                        role,
                        k -> {
                            ArrayList<String> list = new ArrayList<>();
                            list.add(null);
                            return list;
                        });
        cursorPage = Math.max(0, Math.min(position, c.size() - 1));
        rolePages.put(role, cursorPage);
        after = c.get(cursorPage);
        load("index", map("kind", "index", "role", role, "after", after));
    }

    public void next() {
        Object next = data.get("next");
        if (next == null) return;
        ArrayList<String> c = cursors.get(role);
        if (c.size() == cursorPage + 1) c.add(String.valueOf(next));
        index(role, cursorPage + 1);
    }

    public void detail(String id) {
        this.id = id;
        form.clear();
        load("detail", map("kind", "detail", "adoptionId", id));
    }

    public void command(String command, Map<String, Object> values, long version) {
        command(command, values, version, null);
    }

    public void command(
            String command, Map<String, Object> values, long version, Map<String, Object> consent) {
        if (busy || pending() || !current()) return;
        pendingConsent = consent == null ? null : new HashMap<>(consent);
        pending = new HashMap<>(values);
        pending.put("command", command);
        pending.put("expectedVersion", version);
        pending.put("operationId", UUID.randomUUID().toString());
        saved.set("owner", ticket.uid);
        saved.set("operation", pending.get("operationId"));
        saved.set("issuedAt", System.currentTimeMillis());
        send();
    }

    public void send() {
        if (busy || pending == null || !current()) return;
        busy = true;
        error = "";
        long g = generation;
        changed();
        AdoptionApi.call("flow", pending, pendingConsent)
                .addOnCompleteListener(
                        t -> {
                            if (!current() || g != generation) return;
                            busy = false;
                            if (t.isSuccessful()) {
                                success = "saved";
                                Map<String, Object> result = t.getResult();
                                boolean terms = "acceptTerms".equals(pending.get("command"));
                                if (pendingConsent != null) readiness.put("terms", true);
                                clearPending();
                                if (terms) {
                                    readiness();
                                } else if (result.get("adoptionId") != null)
                                    detail(String.valueOf(result.get("adoptionId")));
                                else if (result.get("requestId") != null) {
                                    id = String.valueOf(result.get("requestId"));
                                    load("request", map("kind", "request", "requestId", id));
                                } else if (listing != null)
                                    load("listing", map("kind", "listing", "listingId", listing));
                                else readiness();
                            } else {
                                error = code(t.getException(), true);
                                if (!error.equals("pending_reconcile")) clearPending();
                            }
                            changed();
                        });
    }

    public void reconcile() {
        if (busy || !pending() || !current()) return;
        busy = true;
        long g = generation;
        AdoptionApi.call("flowRead", map("kind", "receipt", "operationId", saved.get("operation")))
                .addOnCompleteListener(
                        t -> {
                            if (!current() || g != generation) return;
                            busy = false;
                            if (t.isSuccessful() && t.getResult().get("receipt") != null) {
                                clearPending();
                                if (id != null) detail(id);
                                else readiness();
                            } else if (t.isSuccessful()
                                    && pending == null
                                    && saved.get("issuedAt") instanceof Long
                                    && number(t.getResult().get("serverTime"))
                                                    - (Long) saved.get("issuedAt")
                                            > 120000) {
                                clearPending();
                                error = "";
                                readiness();
                            } else error = "pending_reconcile";
                            changed();
                        });
    }

    private void clearPending() {
        pending = null;
        pendingConsent = null;
        saved.remove("operation");
        saved.remove("issuedAt");
    }

    private String code(Exception e, boolean write) {
        for (Throwable x = e; x != null; x = x.getCause())
            if (x instanceof FirebaseFunctionsException) {
                FirebaseFunctionsException f = (FirebaseFunctionsException) x;
                if (Arrays.asList(
                                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                                FirebaseFunctionsException.Code.INTERNAL,
                                FirebaseFunctionsException.Code.UNAVAILABLE)
                        .contains(f.getCode()))
                    return write ? "pending_reconcile" : "network_unavailable";
                if (f.getCode() == FirebaseFunctionsException.Code.UNAUTHENTICATED)
                    return "unauthenticated";
                if (f.getCode() == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED)
                    return "rate_limited";
                String message = f.getMessage();
                return message == null || message.trim().isEmpty()
                        ? "server_rejected"
                        : message.trim();
            }
        return write ? "pending_reconcile" : "network_unavailable";
    }

    private void clear() {
        generation++;
        lost = true;
        form.clear();
        data.clear();
        readiness.clear();
        pending = null;
        pendingConsent = null;
        saved.remove("operation");
        changed();
    }

    @Override
    protected void onCleared() {
        sessions.removeInvalidation(invalidated);
        form.clear();
        data.clear();
        pending = null;
        pendingConsent = null;
        super.onCleared();
    }
}
