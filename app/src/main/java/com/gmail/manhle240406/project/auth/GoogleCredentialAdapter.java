package com.gmail.manhle240406.project.auth;

import android.app.Activity;
import android.os.CancellationSignal;
import androidx.core.content.ContextCompat;
import androidx.credentials.*;
import androidx.credentials.exceptions.*;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.FirebaseUser;
import java.lang.ref.WeakReference;

/** Activity-scoped chooser; tokens are exchanged immediately, never placed in app state. */
public final class GoogleCredentialAdapter {
    private final WeakReference<Activity> owner;
    private final AuthCoordinator coordinator;
    private final CredentialManager manager;
    private CancellationSignal cancellation;
    private AuthMutationGate.Ticket choosing;
    private String clientId;
    private android.app.Dialog chooserDialog;
    public GoogleCredentialAdapter(Activity activity, AuthCoordinator coordinator) {
        owner = new WeakReference<>(activity); this.coordinator = coordinator;
        manager = CredentialManager.create(activity.getApplicationContext());
        int id = activity.getResources().getIdentifier("default_web_client_id", "string", activity.getPackageName());
        clientId = id == 0 ? "" : activity.getString(id);
        if (com.gmail.manhle240406.project.BuildConfig.FIREBASE_EMULATORS) clientId = "";
    }
    public boolean available() { return !clientId.isEmpty(); }
    public void request(AuthMutationGate.Ticket ticket, boolean linking, FirebaseUser target, boolean authorizedOnly) {
        Activity activity = owner.get();
        if (ticket == null || !coordinator.accepts(ticket)) return;
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || !available()) {
            coordinator.chooserFailed(ticket, AuthState.Notice.CONFIGURATION); return;
        }
        choosing = ticket; cancellation = new CancellationSignal();
        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(
                new GetGoogleIdOption.Builder().setServerClientId(clientId)
                        .setFilterByAuthorizedAccounts(authorizedOnly).setAutoSelectEnabled(false).build()).build();
        manager.getCredentialAsync(activity, request, cancellation, ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(GetCredentialResponse response) {
                        choosing = null; cancellation = null;
                        Credential credential = response.getCredential();
                        if (!(credential instanceof CustomCredential)
                                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            coordinator.chooserFailed(ticket, AuthState.Notice.CONFIGURATION); return;
                        }
                        try {
                            String token = GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData()).getIdToken();
                            coordinator.google(ticket, token, linking, target);
                        } catch (Exception error) { coordinator.chooserFailed(ticket, AuthState.Notice.CONFIGURATION); }
                    }
                    @Override public void onError(GetCredentialException error) {
                        choosing = null; cancellation = null;
                        if (error instanceof NoCredentialException && authorizedOnly) {
                            // Broader chooser is a deliberate next action, not an automatic sign-in.
                            Activity current = owner.get();
                            if (current != null && !current.isFinishing()) {
                                choosing = ticket;
                                chooserDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(current)
                                        .setMessage(com.gmail.manhle240406.project.R.string.auth_choose_another_google)
                                        .setPositiveButton(com.gmail.manhle240406.project.R.string.auth_choose_account, (dialog, which) -> request(ticket, linking, target, false))
                                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> coordinator.chooserFailed(ticket, AuthState.Notice.CANCELLED))
                                        .setOnCancelListener(dialog -> coordinator.chooserFailed(ticket, AuthState.Notice.CANCELLED)).show();
                            } else coordinator.chooserFailed(ticket, AuthState.Notice.CANCELLED);
                        } else coordinator.chooserFailed(ticket, error instanceof GetCredentialCancellationException
                                ? AuthState.Notice.CANCELLED : AuthState.Notice.CONFIGURATION);
                    }
                });
    }
    public void cancel() {
        if (chooserDialog != null) { chooserDialog.dismiss(); chooserDialog = null; }
        if (cancellation != null) cancellation.cancel();
        if (choosing != null) coordinator.chooserFailed(choosing, AuthState.Notice.CANCELLED);
        choosing = null; cancellation = null;
    }
    public void clearState() {
        Activity activity = owner.get(); if (activity == null) return;
        manager.clearCredentialStateAsync(new ClearCredentialStateRequest(), null, ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override public void onResult(Void value) { }
                    @Override public void onError(ClearCredentialException error) { coordinator.credentialClearFailed(); }
                });
    }
}
