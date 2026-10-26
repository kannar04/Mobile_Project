package com.gmail.manhle240406.project.community;

import android.app.Application;

import androidx.lifecycle.*;

import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.adoption.*;
import com.gmail.manhle240406.project.foundation.*;
import com.google.firebase.firestore.*;

import java.util.*;

/** Private text remains in rotation memory; cold recovery contains receipt IDs only. */
public final class ChatSendModel extends AndroidViewModel {
    public final MutableLiveData<Long> changes = new MutableLiveData<>(0L);
    public String body = "", error = "";
    public boolean busy, lost, committed;
    private long event;
    private String pendingBody, messageId, operationId, reservation;
    private long base;
    private final SessionGateway sessions;
    private final SessionEpoch.Ticket ticket;
    private final SavedStateHandle state;
    private final ParticipantCommunicationRepository repository;
    private final Runnable invalidation =
            () -> {
                lost = true;
                body = "";
                pendingBody = null;
                stateClear();
                changed();
            };

    public ChatSendModel(Application app, SavedStateHandle saved) {
        super(app);
        state = saved;
        sessions = ((AniloveApplication) app).foundation().sessions;
        ticket = sessions.capture();
        repository =
                new ParticipantCommunicationRepository(FirebaseFirestore.getInstance(), sessions);
        if (!Objects.equals(state.get("chat_owner"), ticket.uid)) stateClear();
        messageId = state.get("chat_message");
        operationId = state.get("chat_operation");
        reservation = state.get("chat_reservation");
        Long b = state.get("chat_base");
        base = b == null ? 0 : b;
        sessions.onInvalidated(invalidation);
    }

    private void changed() {
        changes.setValue(++event);
    }

    private boolean current() {
        return !lost && ticket.uid != null && sessions.current(ticket);
    }

    public boolean pending() {
        return operationId != null;
    }

    private void stateClear() {
        for (String k :
                new String[] {
                    "chat_owner", "chat_message", "chat_operation", "chat_reservation", "chat_base"
                }) state.remove(k);
    }

    public void send(String id) {
        if (!current() || busy) return;
        if (pending()) {
            if (pendingBody == null) reconcile();
            else attempt();
            return;
        }
        String text = body.trim();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > 2000) return;
        busy = true;
        changed();
        FirebaseFirestore.getInstance()
                .document("conversationCounters/" + id)
                .get(Source.SERVER)
                .addOnCompleteListener(
                        t -> {
                            if (!current()) return;
                            busy = false;
                            if (!t.isSuccessful() || !t.getResult().exists()) {
                                error = "unavailable";
                                changed();
                                return;
                            }
                            base = t.getResult().getLong("version");
                            reservation = id;
                            messageId = UUID.randomUUID().toString();
                            operationId = UUID.randomUUID().toString();
                            pendingBody = text;
                            state.set("chat_owner", ticket.uid);
                            state.set("chat_message", messageId);
                            state.set("chat_operation", operationId);
                            state.set("chat_reservation", id);
                            state.set("chat_base", base);
                            attempt();
                        });
    }

    private void attempt() {
        busy = true;
        error = "";
        changed();
        repository
                .send(ticket, reservation, messageId, operationId, base, pendingBody)
                .addOnCompleteListener(
                        t -> {
                            if (!current()) return;
                            busy = false;
                            if (t.isSuccessful()) success();
                            else {
                                for (Throwable cause = t.getException();
                                        cause != null;
                                        cause = cause.getCause())
                                    if (cause instanceof IllegalStateException
                                                    && "send_conflict".equals(cause.getMessage())
                                            || cause instanceof FirebaseFirestoreException
                                                    && ((FirebaseFirestoreException) cause)
                                                                    .getCode()
                                                            == FirebaseFirestoreException.Code
                                                                    .PERMISSION_DENIED) {
                                        operationId = null;
                                        messageId = null;
                                        pendingBody = null;
                                        stateClear();
                                        break;
                                    }
                                error = "unavailable";
                                changed();
                            }
                        });
    }

    private void success() {
        body = "";
        pendingBody = null;
        operationId = null;
        messageId = null;
        stateClear();
        error = "";
        committed = true;
        changed();
    }

    public void reconcile() {
        if (!current() || busy || operationId == null) return;
        busy = true;
        changed();
        FirebaseFirestore.getInstance()
                .document("conversationReceipts/" + ticket.uid + "/operations/" + operationId)
                .get(Source.SERVER)
                .addOnCompleteListener(
                        t -> {
                            if (!current()) return;
                            busy = false;
                            if (t.isSuccessful()
                                    && t.getResult().exists()
                                    && Objects.equals(
                                            t.getResult().getString("reservationId"), reservation)
                                    && Objects.equals(
                                            t.getResult().getString("messageId"), messageId))
                                success();
                            else {
                                error = "unknown_outcome";
                                changed();
                            }
                        });
    }

    @Override
    protected void onCleared() {
        lost = true;
        repository.close();
        sessions.removeInvalidation(invalidation);
        body = "";
        pendingBody = null;
        super.onCleared();
    }
}
