package com.gmail.manhle240406.project.security;

import java.util.*;
import java.util.concurrent.*;

/**
 * F43 own tracked intent. Managed service must derive UID/verified auth_time; the client cannot
 * attest either.
 */
public final class AccountClosureRepository {
    public interface Managed {
        CompletionStage<Status> preflight();

        CompletionStage<Status> begin(String requestId, long policyVersion, long manifestVersion);

        CompletionStage<Status> status(String requestId);
    }

    public static final Managed UNAVAILABLE =
            new Managed() {
                private CompletionStage<Status> denied() {
                    CompletableFuture<Status> f = new CompletableFuture<>();
                    f.completeExceptionally(
                            new IllegalStateException("closure_managed_service_not_authorized"));
                    return f;
                }

                public CompletionStage<Status> preflight() {
                    return denied();
                }

                public CompletionStage<Status> begin(String id, long p, long m) {
                    return denied();
                }

                public CompletionStage<Status> status(String id) {
                    return denied();
                }
            };

    public static final class Status {
        public final String ownerUid, requestId;
        public final long version, policyVersion, manifestVersion;
        public final ClosurePolicy.Phase phase;
        public final boolean accepted;

        public Status(
                String uid,
                String request,
                long version,
                long policy,
                long manifest,
                ClosurePolicy.Phase phase,
                boolean accepted) {
            if (uid == null
                    || uid.isBlank()
                    || request == null
                    || !request.matches("[A-Za-z0-9_-]{1,128}")
                    || version < 1
                    || policy < 1
                    || manifest < 1
                    || phase == null) throw new IllegalArgumentException("closure_status");
            ownerUid = uid;
            requestId = request;
            this.version = version;
            policyVersion = policy;
            manifestVersion = manifest;
            this.phase = phase;
            this.accepted = accepted;
        }
    }

    public static final class AcceptedCleanupFailure extends RuntimeException {
        public final Status acceptedStatus;

        public AcceptedCleanupFailure(Status status) {
            super("closure_accepted_local_cleanup_required");
            acceptedStatus = status;
        }
    }

    private final Managed managed;
    private final AccountSecurityRepository.Session session;
    private final AccountSecurityRepository.Cleanup cleanup;
    private boolean pending;
    private String unresolved;
    private long pendingPolicy, pendingManifest;

    public AccountClosureRepository(
            Managed managed,
            AccountSecurityRepository.Session session,
            AccountSecurityRepository.Cleanup cleanup) {
        this.managed = Objects.requireNonNull(managed);
        this.session = Objects.requireNonNull(session);
        this.cleanup = Objects.requireNonNull(cleanup);
    }

    public CompletionStage<Status> preflight() {
        Object ticket = current();
        return managed.preflight().thenApply(s -> checked(ticket, s, null));
    }

    /** Retries after an unknown result use reconcile; no automatic second destructive request. */
    public synchronized CompletionStage<Status> begin(
            String requestId, long policy, long manifest) {
        if (requestId == null
                || !requestId.matches("[A-Za-z0-9_-]{1,128}")
                || policy < 1
                || manifest < 1) throw new IllegalArgumentException("closure_intent");
        if (pending || unresolved != null)
            throw new IllegalStateException("reconcile_existing_closure_first");
        Object ticket = current();
        pending = true;
        unresolved = requestId;
        pendingPolicy = policy;
        pendingManifest = manifest;
        CompletionStage<Status> work;
        try {
            work =
                    managed.begin(requestId, policy, manifest)
                            .thenCompose(s -> accepted(ticket, s, requestId, policy, manifest));
        } catch (RuntimeException e) {
            pending = false;
            throw e;
        }
        return work.whenComplete(
                (s, e) -> {
                    synchronized (this) {
                        pending = false;
                    }
                });
    }

    public synchronized CompletionStage<Status> reconcile() {
        if (pending || unresolved == null)
            throw new IllegalStateException("no_reconcilable_request");
        Object ticket = current();
        String id = unresolved;
        long p = pendingPolicy, m = pendingManifest;
        pending = true;
        CompletionStage<Status> work;
        try {
            work = managed.status(id).thenCompose(s -> accepted(ticket, s, id, p, m));
        } catch (RuntimeException e) {
            pending = false;
            throw e;
        }
        return work.whenComplete(
                (s, e) -> {
                    synchronized (this) {
                        pending = false;
                    }
                });
    }

    public CompletionStage<Status> ownStatus(String requestId) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("request_id");
        Object t = current();
        return managed.status(requestId).thenApply(s -> checked(t, s, requestId));
    }

    private CompletionStage<Status> accepted(
            Object ticket, Status status, String id, long policy, long manifest) {
        Status safe = checked(ticket, status, id);
        if (safe.policyVersion != policy || safe.manifestVersion != manifest || !safe.accepted)
            throw new IllegalStateException("closure_not_acknowledged");
        // Local sign-out occurs even if disk cleanup fails. Cloud request remains accepted, never
        // resubmitted.
        session.signOut();
        try {
            return cleanup.clear(safe.ownerUid)
                    .handle(
                            (v, e) -> {
                                if (e != null) throw new AcceptedCleanupFailure(safe);
                                synchronized (this) {
                                    unresolved = null;
                                }
                                return safe;
                            });
        } catch (RuntimeException e) {
            throw new AcceptedCleanupFailure(safe);
        }
    }

    private Object current() {
        Object t = session.capture();
        if (session.uid(t) == null) throw new IllegalStateException("sign_in_required");
        return t;
    }

    private Status checked(Object ticket, Status s, String id) {
        if (!session.current(ticket)
                || s == null
                || !session.uid(ticket).equals(s.ownerUid)
                || id != null && !id.equals(s.requestId))
            throw new IllegalStateException("session_or_status_mismatch");
        return s;
    }
}
