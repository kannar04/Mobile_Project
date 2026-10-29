package com.gmail.manhle240406.project.security;

import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** F02 provider commands only. No credentials, email history or action codes are persisted. */
public final class AccountSecurityRepository {
    public enum Result {
        CHECK_EMAIL_IF_ELIGIBLE,
        SIGN_IN_REQUIRED,
        INVALID_INPUT,
        BUSY,
        COOLDOWN,
        CONFIGURATION_REQUIRED,
        PROVIDER_MANAGED,
        WRONG_CREDENTIAL,
        NETWORK,
        THROTTLED,
        POLICY,
        UNAVAILABLE,
        OUTCOME_UNKNOWN,
        SESSION_CHANGED,
        CLEANUP_REQUIRED
    }

    public interface Session {
        Object capture();

        String uid(Object ticket);

        boolean current(Object ticket);

        void signOut();
    }

    public interface Provider {
        CompletionStage<Void> reset(String email, String language);

        CompletionStage<Boolean> hasPassword(String uid);

        CompletionStage<Void> reauthenticate(String uid, String password);

        CompletionStage<Void> update(String uid, String password);
    }

    /** Must invalidate every known local namespace/listener/push binding; no default no-op. */
    public interface Cleanup {
        CompletionStage<Void> clear(String uid);
    }

    public static final class Failure extends RuntimeException {
        public final Result result;

        public Failure(Result result) {
            super(result.name());
            this.result = result;
        }
    }

    private final Provider provider;
    private final Session session;
    private final Cleanup cleanup;
    private final LongSupplier elapsed;
    private final boolean resetConfigurationVerified;
    private boolean busy;
    private long nextReset = -1;

    public AccountSecurityRepository(
            Provider p, Session s, Cleanup c, LongSupplier clock, boolean verified) {
        provider = java.util.Objects.requireNonNull(p);
        session = java.util.Objects.requireNonNull(s);
        cleanup = java.util.Objects.requireNonNull(c);
        elapsed = clock;
        resetConfigurationVerified = verified;
    }

    private static CompletableFuture<Result> done(Result r) {
        return CompletableFuture.completedFuture(r);
    }

    private static Result error(Throwable e, boolean mutation) {
        while (e instanceof CompletionException || e instanceof ExecutionException)
            e = e.getCause();
        if (e instanceof Failure) {
            Result r = ((Failure) e).result;
            return mutation && (r == Result.NETWORK || r == Result.UNAVAILABLE)
                    ? Result.OUTCOME_UNKNOWN
                    : r;
        }
        return mutation ? Result.OUTCOME_UNKNOWN : Result.UNAVAILABLE;
    }

    public synchronized CompletionStage<Result> requestReset(String email, String language) {
        if (email == null
                || email.length() > 254
                || !email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || !java.util.Arrays.asList("vi", "en").contains(language))
            return done(Result.INVALID_INPUT);
        if (!resetConfigurationVerified) return done(Result.CONFIGURATION_REQUIRED);
        if (busy) return done(Result.BUSY);
        long now = elapsed.getAsLong();
        if (nextReset >= 0 && now < nextReset) return done(Result.COOLDOWN);
        busy = true;
        nextReset = now + 60_000;
        Object ticket = session.capture();
        try {
            return provider.reset(email.trim(), language)
                    .handle(
                            (v, e) -> {
                                synchronized (this) {
                                    busy = false;
                                }
                                if (!session.current(ticket)) return Result.SESSION_CHANGED;
                                return e == null ? Result.CHECK_EMAIL_IF_ELIGIBLE : error(e, false);
                            });
        } catch (RuntimeException e) {
            busy = false;
            return done(error(e, false));
        }
    }

    public synchronized CompletionStage<Result> changePassword(
            String current, String replacement, String confirmation) {
        if (current == null
                || current.isEmpty()
                || replacement == null
                || replacement.isEmpty()
                || replacement.length() > 4096
                || !replacement.equals(confirmation)) return done(Result.INVALID_INPUT);
        if (busy) return done(Result.BUSY);
        Object ticket = session.capture();
        String uid = session.uid(ticket);
        if (uid == null) return done(Result.SIGN_IN_REQUIRED);
        busy = true;
        java.util.concurrent.atomic.AtomicBoolean signedOutHere =
                new java.util.concurrent.atomic.AtomicBoolean();
        // Secrets live only in this short-lived SDK operation; never in state, receipts or retry
        // jobs.
        CompletionStage<Result> operation;
        try {
            operation =
                    provider.hasPassword(uid)
                            .thenCompose(
                                    available -> {
                                        requireCurrent(ticket);
                                        if (!available) return done(Result.PROVIDER_MANAGED);
                                        return provider.reauthenticate(uid, current)
                                                .thenCompose(
                                                        v -> {
                                                            requireCurrent(ticket);
                                                            return provider.update(uid, replacement)
                                                                    .handle(
                                                                            (ignored, failure) -> {
                                                                                if (!session
                                                                                        .current(
                                                                                                ticket))
                                                                                    return done(
                                                                                            Result
                                                                                                    .SESSION_CHANGED);
                                                                                Result result =
                                                                                        failure
                                                                                                        == null
                                                                                                ? Result
                                                                                                        .SIGN_IN_REQUIRED
                                                                                                : error(
                                                                                                        failure,
                                                                                                        true);
                                                                                // Unknown provider
                                                                                // outcome also
                                                                                // requires explicit
                                                                                // sign-in; never
                                                                                // replay the
                                                                                // password.
                                                                                if (result
                                                                                                != Result
                                                                                                        .SIGN_IN_REQUIRED
                                                                                        && result
                                                                                                != Result
                                                                                                        .OUTCOME_UNKNOWN)
                                                                                    return done(
                                                                                            result);
                                                                                signedOutHere.set(
                                                                                        true);
                                                                                session.signOut();
                                                                                try {
                                                                                    return cleanup.clear(
                                                                                                    uid)
                                                                                            .handle(
                                                                                                    (x,
                                                                                                            e) ->
                                                                                                            e
                                                                                                                            == null
                                                                                                                    ? result
                                                                                                                    : Result
                                                                                                                            .CLEANUP_REQUIRED);
                                                                                } catch (
                                                                                        RuntimeException
                                                                                                e) {
                                                                                    return done(
                                                                                            Result
                                                                                                    .CLEANUP_REQUIRED);
                                                                                }
                                                                            })
                                                                    .thenCompose(x -> x);
                                                        });
                                    })
                            .exceptionally(e -> error(e, false));
        } catch (RuntimeException e) {
            operation = done(error(e, false));
        }
        return operation.handle(
                (result, e) -> {
                    synchronized (this) {
                        busy = false;
                    }
                    if (!signedOutHere.get() && !session.current(ticket))
                        return Result.SESSION_CHANGED;
                    return e == null ? result : error(e, false);
                });
    }

    private void requireCurrent(Object ticket) {
        if (!session.current(ticket)) throw new Failure(Result.SESSION_CHANGED);
    }
}
