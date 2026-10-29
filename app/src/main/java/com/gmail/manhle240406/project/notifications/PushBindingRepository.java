package com.gmail.manhle240406.project.notifications;

import com.gmail.manhle240406.project.foundation.*;
import com.gmail.manhle240406.project.media.MediaContract;
import com.google.android.gms.tasks.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Authenticated callable transport only. Token acquisition/OS permission/services need separate
 * wiring.
 */
public final class PushBindingRepository {
    public interface TrustedBindings {
        Task<Map<String, Object>> call(String command, Map<String, Object> input);
    }

    public static final TrustedBindings UNAVAILABLE =
            (command, input) ->
                    Tasks.forException(new IllegalStateException("push_service_not_authorized"));
    private final SessionGateway sessions;
    private final TrustedBindings transport;

    public PushBindingRepository(SessionGateway sessions, TrustedBindings transport) {
        this.sessions = sessions;
        this.transport = Objects.requireNonNull(transport);
    }

    private void current(SessionEpoch.Ticket ticket) {
        if (ticket.uid == null || !sessions.current(ticket))
            throw new IllegalStateException("session_changed");
    }

    private Task<Map<String, Object>> call(
            SessionEpoch.Ticket ticket, String command, Map<String, Object> input) {
        current(ticket);
        return Tasks.withTimeout(
                        transport.call(command, Collections.unmodifiableMap(input)),
                        15,
                        TimeUnit.SECONDS)
                .continueWith(
                        done -> {
                            current(ticket);
                            return done.getResult();
                        });
    }

    public Task<Map<String, Object>> register(
            SessionEpoch.Ticket ticket,
            String installationId,
            String operationId,
            String token,
            String secret,
            boolean replaceConfirmed) {
        current(ticket);
        if (token == null
                || token.isEmpty()
                || token.length() > 4096
                || secret == null
                || !secret.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("binding_input");
        Map<String, Object> input = new HashMap<>();
        input.put("installationId", MediaContract.id(installationId));
        input.put("operationId", MediaContract.id(operationId));
        input.put("token", token);
        input.put("secret", secret);
        input.put("replaceConfirmed", replaceConfirmed);
        return call(ticket, "registerInstallation", input);
    }

    public Task<Map<String, Object>> confirm(
            SessionEpoch.Ticket ticket, String challengeId, String nonce, String secret) {
        current(ticket);
        if (nonce == null
                || !nonce.matches("[0-9a-f]{64}")
                || secret == null
                || !secret.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("binding_proof");
        Map<String, Object> input = new HashMap<>();
        input.put("challengeId", MediaContract.id(challengeId));
        input.put("nonce", nonce);
        input.put("secret", secret);
        return call(ticket, "confirmInstallation", input);
    }

    public Task<Map<String, Object>> revoke(
            SessionEpoch.Ticket ticket, String installationId, long epoch, String secret) {
        current(ticket);
        if (epoch < 1 || secret == null || !secret.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("binding_proof");
        Map<String, Object> input = new HashMap<>();
        input.put("installationId", MediaContract.id(installationId));
        input.put("bindingEpoch", epoch);
        input.put("secret", secret);
        return call(ticket, "revokeInstallation", input);
    }

    public Task<Map<String, Object>> renew(
            SessionEpoch.Ticket ticket, String installationId, long epoch, String secret) {
        current(ticket);
        if (epoch < 1 || secret == null || !secret.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("binding_proof");
        Map<String, Object> input = new HashMap<>();
        input.put("installationId", MediaContract.id(installationId));
        input.put("bindingEpoch", epoch);
        input.put("secret", secret);
        return call(ticket, "renewInstallation", input);
    }
}
