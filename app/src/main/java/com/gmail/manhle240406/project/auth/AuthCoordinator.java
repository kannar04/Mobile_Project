package com.gmail.manhle240406.project.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** App-owned coordinator; it reuses SessionGateway's only Auth observer. */
public final class AuthCoordinator {

    private String newGoogleUid;

    public boolean consumeNewGoogleRegistration(String uid) {
        boolean fresh = uid != null && uid.equals(newGoogleUid);
        if (fresh) newGoogleUid = null;
        return fresh;
    }

    private final FirebaseEnvironment environment;
    private final FirebaseAuth auth;
    private final AccountBootstrapRepository accounts;
    private final SharedPreferences intent;
    private final AuthMutationGate gate;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<AuthState> state = new MutableLiveData<>();
    private boolean setupReady, verificationSending;
    private MembershipRepository.State membership = MembershipRepository.State.UNKNOWN;
    private boolean verificationKnown;
    private boolean keepCurrentAccount, readinessLoading;
    private String operationUid;
    private int readinessRemaining;
    private String readinessUid;
    private long readinessGeneration;
    private long lastVerification = -60000;
    private AuthState.Authority authority = AuthState.Authority.UNKNOWN;
    private AuthState.Notice notice = AuthState.Notice.NONE;

    public AuthCoordinator(Context context, FirebaseEnvironment environment) {
        this.environment = environment;
        intent = context.getSharedPreferences("auth_local_intent", Context.MODE_PRIVATE);
        gate = new AuthMutationGate(intent.getBoolean("visitor", false));
        if (environment.repository == null) {
            auth = null;
            accounts = null;
            notice = AuthState.Notice.CONFIGURATION;
            publish();
            return;
        }
        auth = FirebaseAuth.getInstance();
        auth.setLanguageCode("vi");
        accounts =
                new AccountBootstrapRepository(
                        FirebaseFirestore.getInstance(), environment.sessions);
        environment.sessions.onInvalidated(
                () -> {
                    readinessGeneration++;
                    membership = MembershipRepository.State.UNKNOWN;
                    setupReady = false;
                    authority = AuthState.Authority.UNKNOWN;
                    readinessUid = null;
                    verificationSending = false;
                    verificationKnown = false;
                    readinessLoading = false;
                    if (gate.visitor() || gate.busy()) publish();
                    else refreshReadiness();
                });
        if (gate.visitor()) environment.sessions.logout();
        // Firebase Auth restores the last signed-in identity locally. Do not block cached reading
        // on
        // reload/token traffic; protected writes still perform their normal online authorization.
        if (!gate.visitor() && user() != null) {
            verificationKnown = true;
            refreshReadiness();
        } else refreshReadiness();
    }

    public LiveData<AuthState> state() {
        return state;
    }

    public boolean busy() {
        return gate.busy();
    }

    private FirebaseUser user() {
        return auth == null ? null : auth.getCurrentUser();
    }

    private void publish() {
        FirebaseUser user = user();
        if (gate.visitor()
                || (gate.busy()
                        && (!keepCurrentAccount
                                || user == null
                                || !user.getUid().equals(operationUid)))) user = null;
        boolean google =
                user != null
                        && user.getProviderData().stream()
                                .anyMatch(p -> "google.com".equals(p.getProviderId()));
        boolean password =
                user != null
                        && user.getProviderData().stream()
                                .anyMatch(p -> "password".equals(p.getProviderId()));
        AuthState next =
                new AuthState(
                        user == null ? null : user.getUid(),
                        user == null ? null : user.getEmail(),
                        gate.busy(),
                        setupReady,
                        user != null && user.isEmailVerified(),
                        google,
                        password && !google,
                        !verificationSending
                                && SystemClock.elapsedRealtime() - lastVerification >= 60000,
                        authority,
                        notice,
                        verificationKnown,
                        environment.repository != null,
                        readinessLoading);
        next.membership = membership;
        state.setValue(next);
    }

    private AuthMutationGate.Ticket begin() {
        return begin(false);
    }

    private AuthMutationGate.Ticket begin(boolean keepAccount) {
        if (auth == null) {
            notice = AuthState.Notice.CONFIGURATION;
            publish();
            return null;
        }
        AuthMutationGate.Ticket ticket = gate.begin();
        if (ticket == null) return null;
        FirebaseUser current = user();
        keepCurrentAccount = keepAccount;
        operationUid = keepAccount && current != null ? current.getUid() : null;
        readinessGeneration++;
        readinessLoading = false;
        // Non-secret desired-session intent survives process recreation; no credentials are stored.
        intent.edit().putBoolean("visitor", false).apply();
        notice = AuthState.Notice.NONE;
        publish();
        main.postDelayed(
                () -> {
                    if (gate.current(ticket)) {
                        notice = AuthState.Notice.OUTCOME_UNKNOWN;
                        publish();
                    }
                },
                15000);
        return ticket;
    }

    private boolean settle(AuthMutationGate.Ticket ticket) {
        AuthMutationGate.Settlement action = gate.settle(ticket);
        if (action == AuthMutationGate.Settlement.DISCARD) return false;
        keepCurrentAccount = false;
        operationUid = null;
        if (action == AuthMutationGate.Settlement.RECONCILE_VISITOR) {
            environment.sessions.logout();
            notice = AuthState.Notice.NONE;
            publish();
            return false;
        }
        return true;
    }

    private static AuthState.Notice error(Exception error) {
        if (error instanceof FirebaseAuthWeakPasswordException)
            return AuthState.Notice.PASSWORD_POLICY;
        if (error instanceof FirebaseAuthUserCollisionException) return AuthState.Notice.CONFLICT;
        if (error instanceof FirebaseAuthInvalidCredentialsException
                || error instanceof FirebaseAuthInvalidUserException)
            return AuthState.Notice.INVALID_CREDENTIALS;
        if (error instanceof com.google.firebase.FirebaseTooManyRequestsException)
            return AuthState.Notice.THROTTLED;
        if (error instanceof com.google.firebase.FirebaseNetworkException)
            return AuthState.Notice.NETWORK;
        return AuthState.Notice.CONFIGURATION;
    }

    public void email(String email, String password, boolean registration) {
        if (AuthInput.validate(email, password, registration ? password : "", registration)
                != AuthInput.Error.NONE) {
            notice = AuthState.Notice.INVALID_CREDENTIALS;
            publish();
            return;
        }
        if (user() != null && !gate.visitor()) {
            refreshReadiness();
            return;
        }
        AuthMutationGate.Ticket ticket = begin();
        if (ticket == null) return;
        Task<AuthResult> task =
                registration
                        ? auth.createUserWithEmailAndPassword(email.trim(), password)
                        : auth.signInWithEmailAndPassword(email.trim(), password);
        task.addOnCompleteListener(
                result -> {
                    if (!settle(ticket)) return;
                    if (!result.isSuccessful()) {
                        if (result.getException() instanceof FirebaseAuthInvalidUserException)
                            logout();
                        notice = error(result.getException());
                        publish();
                        return;
                    }
                    verificationKnown = true;
                    refreshReadiness();
                });
    }

    public AuthMutationGate.Ticket beginGoogle() {
        if (user() != null && !gate.visitor()) return null;
        return begin();
    }

    public void google(
            AuthMutationGate.Ticket ticket, String token, boolean linking, FirebaseUser target) {
        if (!gate.current(ticket)) {
            if (!gate.busy()) return;
            settle(ticket);
            return;
        }
        AuthCredential credential = GoogleAuthProvider.getCredential(token, null);
        FirebaseUser current = user();
        if (linking
                && (target == null
                        || current == null
                        || !target.getUid().equals(current.getUid()))) {
            chooserFailed(ticket, AuthState.Notice.CANCELLED);
            return;
        }
        Task<AuthResult> task =
                linking
                        ? target.linkWithCredential(credential)
                        : auth.signInWithCredential(credential);
        task.addOnCompleteListener(
                result -> {
                    if (!settle(ticket)) return;
                    if (!result.isSuccessful()) {
                        if (result.getException() instanceof FirebaseAuthInvalidUserException)
                            logout();
                        notice = error(result.getException());
                        publish();
                        return;
                    }
                    FirebaseUser returned =
                            result.getResult() == null ? null : result.getResult().getUser();
                    if (returned == null
                            || (linking && !target.getUid().equals(returned.getUid()))) {
                        logout();
                        notice = AuthState.Notice.CONFLICT;
                        publish();
                        return;
                    }
                    if (!linking
                            && result.getResult().getAdditionalUserInfo() != null
                            && result.getResult().getAdditionalUserInfo().isNewUser())
                        newGoogleUid = returned.getUid();
                    refreshVerification();
                });
    }

    public void chooserFailed(AuthMutationGate.Ticket ticket, AuthState.Notice failure) {
        if (!settle(ticket)) return;
        notice = failure;
        publish();
    }

    public void proveForGoogle(String password, Consumer<AuthMutationGate.Ticket> ready) {
        FirebaseUser current = user();
        if (current == null
                || current.getEmail() == null
                || state.getValue() == null
                || !state.getValue().canLink) return;
        AuthMutationGate.Ticket ticket = begin(true);
        if (ticket == null) return;
        current.reauthenticate(EmailAuthProvider.getCredential(current.getEmail(), password))
                .addOnCompleteListener(
                        task -> {
                            if (!gate.current(ticket)) {
                                settle(ticket);
                                return;
                            }
                            if (!task.isSuccessful()) {
                                if (settle(ticket)) {
                                    if (task.getException()
                                            instanceof FirebaseAuthInvalidUserException) logout();
                                    notice = error(task.getException());
                                    publish();
                                }
                                return;
                            }
                            FirebaseUser actual = user();
                            if (actual == null || !current.getUid().equals(actual.getUid())) {
                                chooserFailed(ticket, AuthState.Notice.CANCELLED);
                                return;
                            }
                            ready.accept(ticket);
                        });
    }

    public FirebaseUser linkingTarget() {
        return user();
    }

    public void logout() {
        newGoogleUid = null;
        gate.logout();
        readinessGeneration++;
        readinessUid = null;
        readinessLoading = false;
        keepCurrentAccount = false;
        operationUid = null;
        setupReady = false;
        membership = MembershipRepository.State.UNKNOWN;
        authority = AuthState.Authority.UNKNOWN;
        verificationSending = false;
        verificationKnown = false;
        notice = AuthState.Notice.NONE;
        if (auth != null) {
            auth.signOut();
            environment.sessions.logout();
        }
        intent.edit().putBoolean("visitor", false).apply();
        publish();
    }

    public void refreshReadiness() {
        if (auth == null || gate.visitor() || gate.busy() || readinessLoading) {
            publish();
            return;
        }
        FirebaseUser current = user();
        if (current == null) {
            setupReady = false;
            authority = AuthState.Authority.UNKNOWN;
            publish();
            return;
        }
        SessionEpoch.Ticket ticket = environment.sessions.capture();
        String uid = current.getUid();
        if (!uid.equals(readinessUid)) {
            setupReady = false;
            authority = AuthState.Authority.UNKNOWN;
            lastVerification = -60000;
        }
        readinessUid = uid;
        long generation = ++readinessGeneration;
        readinessLoading = true;
        readinessRemaining = 1;
        accounts.ensureAccount()
                .addOnCompleteListener(
                        task -> {
                            if (!environment.sessions.current(ticket)
                                    || generation != readinessGeneration
                                    || gate.visitor()) return;
                            setupReady =
                                    task.isSuccessful() && Boolean.TRUE.equals(task.getResult());
                            membership = MembershipRepository.State.UNKNOWN;
                            if (setupReady) {
                                publish();
                                new MembershipRepository(
                                                FirebaseFirestore.getInstance(),
                                                environment.sessions)
                                        .ensureBasicMembership(ticket)
                                        .addOnCompleteListener(
                                                m -> {
                                                    if (!environment.sessions.current(ticket)
                                                            || generation != readinessGeneration
                                                            || gate.visitor()) return;
                                                    membership =
                                                            m.isSuccessful()
                                                                    ? m.getResult()
                                                                    : MembershipRepository.State
                                                                            .UNKNOWN;
                                                    completeReadinessPart();
                                                });
                            } else completeReadinessPart();
                        });
        // Ordinary membership checks explicit restrictions; privileged tools authorize on the
        // server.
        publish();
    }

    public void refreshVerification() {
        FirebaseUser current = user();
        if (current == null) return;
        AuthMutationGate.Ticket ticket = begin(true);
        if (ticket == null) return;
        // Reload and token refresh are settled before another session mutation.
        current.reload()
                .continueWithTask(
                        task -> {
                            if (!task.isSuccessful())
                                return Tasks.forException(task.getException());
                            if (!gate.current(ticket))
                                return Tasks.forException(new IllegalStateException("Cancelled"));
                            return current.getIdToken(true);
                        })
                .addOnCompleteListener(
                        task -> {
                            if (!settle(ticket)) return;
                            verificationKnown = task.isSuccessful();
                            if (!task.isSuccessful()) {
                                if (task.getException() instanceof FirebaseAuthInvalidUserException)
                                    logout();
                                notice = error(task.getException());
                            }
                            refreshReadiness();
                        });
    }

    private void completeReadinessPart() {
        if (--readinessRemaining <= 0) readinessLoading = false;
        publish();
    }

    public void sendVerification() {
        FirebaseUser current = user();
        if (current == null
                || current.isEmailVerified()
                || gate.visitor()
                || gate.busy()
                || verificationSending) return;
        if (SystemClock.elapsedRealtime() - lastVerification < 60000) {
            notice = AuthState.Notice.COOLDOWN;
            publish();
            return;
        }
        SessionEpoch.Ticket ticket = environment.sessions.capture();
        verificationSending = true;
        lastVerification = SystemClock.elapsedRealtime();
        publish();
        Tasks.withTimeout(current.sendEmailVerification(), 15, TimeUnit.SECONDS)
                .addOnCompleteListener(
                        task -> {
                            if (!environment.sessions.current(ticket) || gate.visitor()) return;
                            verificationSending = false;
                            notice =
                                    task.isSuccessful()
                                            ? AuthState.Notice.SENT
                                            : AuthState.Notice.SEND_FAILED;
                            publish();
                        });
        main.postDelayed(
                () -> {
                    if (environment.sessions.current(ticket) && !gate.visitor()) publish();
                },
                60000);
    }

    public boolean accepts(AuthMutationGate.Ticket ticket) {
        return gate.current(ticket);
    }

    public void credentialClearFailed() {
        if (gate.visitor()) {
            notice = AuthState.Notice.PROVIDER_CLEAR_FAILED;
            publish();
        }
    }
}
