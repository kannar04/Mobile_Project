package com.gmail.manhle240406.project.auth;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class AuthActivity extends AppCompatActivity {
    private AuthCoordinator coordinator;
    private AuthViewModel model;
    private GoogleCredentialAdapter google;
    private TextInputEditText email, password, confirm, proof;
    private String lastUid;
    private boolean accountManagement;
    private boolean leavingForMain;
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_auth);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.auth_root), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return insets;
        });
        coordinator = ((AniloveApplication) getApplication()).auth();
        model = new ViewModelProvider(this).get(AuthViewModel.class);
        google = new GoogleCredentialAdapter(this, coordinator);
        email = findViewById(R.id.auth_email); password = findViewById(R.id.auth_password);
        confirm = findViewById(R.id.auth_confirm); proof = findViewById(R.id.auth_proof);
        button(R.id.auth_submit).setOnClickListener(v -> submit());
        button(R.id.auth_switch).setOnClickListener(v -> {
            model.registering(!model.registering()); clearSecrets(); clearErrors(); render(coordinator.state().getValue());
        });
        button(R.id.auth_google).setOnClickListener(v -> google.request(coordinator.beginGoogle(), false, null, true));
        button(R.id.auth_link).setOnClickListener(v -> {
            String value = text(proof);
            if (value.isEmpty()) { field(R.id.auth_proof_layout).setError(getString(R.string.auth_proof_required)); return; }
            proof.setText("");
            coordinator.proveForGoogle(value, ticket -> google.request(ticket, true, coordinator.linkingTarget(), true));
        });
        button(R.id.auth_verify).setOnClickListener(v -> coordinator.sendVerification());
        button(R.id.auth_refresh).setOnClickListener(v -> coordinator.refreshVerification());
        button(R.id.auth_retry_setup).setOnClickListener(v -> coordinator.refreshReadiness());
        button(R.id.auth_back).setOnClickListener(v -> leave());
        password.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE && !model.registering()) { submit(); return true; } return false;
        });
        confirm.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) { submit(); return true; } return false;
        });
        AuthState initial = coordinator.state().getValue();
        accountManagement=getIntent().getBooleanExtra("account",false)&&initial!=null&&initial.uid!=null;
        if (initial != null && initial.uid != null && !initial.pending && !accountManagement) {
            navigateToMain();
            return;
        }
        coordinator.state().observe(this, state -> {
            if (state != null && state.uid != null && !state.pending && !accountManagement) {
                navigateToMain();
                return;
            }
            render(state);
        });
    }
    private void navigateToMain() {
        if(leavingForMain)return;leavingForMain=true;
        startActivity(new android.content.Intent(this, com.gmail.manhle240406.project.MainActivity.class).putExtra(com.gmail.manhle240406.project.MainActivity.PAGE,getIntent().getBooleanExtra("notificationInbox",false)?"notifications":"home")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
    private MaterialButton button(int id) { return findViewById(id); }
    private TextInputLayout field(int id) { return findViewById(id); }
    private String text(TextInputEditText field) { return field.getText() == null ? "" : field.getText().toString(); }
    private void clearSecrets() { if (password != null) { password.setText(""); confirm.setText(""); proof.setText(""); } }
    private void clearErrors() {
        for (int id : new int[]{R.id.auth_email_layout,R.id.auth_password_layout,R.id.auth_confirm_layout,R.id.auth_proof_layout}) field(id).setError(null);
    }
    private void submit() {
        if (coordinator.busy()) return;
        clearErrors();
        String address = text(email), secret = text(password);
        AuthInput.Error result = AuthInput.validate(address, secret, text(confirm), model.registering());
        switch (result) {
            case EMAIL: field(R.id.auth_email_layout).setError(getString(R.string.auth_email_invalid)); email.requestFocus(); return;
            case PASSWORD_REQUIRED: field(R.id.auth_password_layout).setError(getString(R.string.auth_password_required)); password.requestFocus(); return;
            case PASSWORD_POLICY: field(R.id.auth_password_layout).setError(getString(R.string.auth_password_invalid)); password.requestFocus(); return;
            case CONFIRMATION: field(R.id.auth_confirm_layout).setError(getString(R.string.auth_confirmation_invalid)); confirm.requestFocus(); return;
            default: break;
        }
        clearSecrets();
        coordinator.email(address, secret, model.registering());
    }
    private void logout() { coordinator.logout(); google.cancel(); google.clearState(); clearSecrets(); email.setText(""); }
    private void leave() { if (coordinator.busy()) logout(); clearSecrets(); finish(); }
    @Override protected void onStop() { clearSecrets(); super.onStop(); }
    @Override protected void onDestroy() {
        // Window destruction is not a logout intent. A successful Google exchange starts a
        // token reload; destroying this window must not cancel that app-owned refresh.
        if (google != null) google.cancel();
        super.onDestroy();
    }
    private void visible(int id, boolean show) { findViewById(id).setVisibility(show ? View.VISIBLE : View.GONE); }
    private void render(AuthState state) {
        if (state == null) return;
        boolean restoring=state.uid!=null&&!accountManagement;
        visible(R.id.auth_startup_loading,restoring);visible(R.id.auth_form,!restoring);
        if(restoring)return;
        boolean account = state.uid != null;
        if (lastUid != null && !lastUid.equals(state.uid)) { clearSecrets(); email.setText(""); model.registering(false); }
        lastUid = state.uid;
        ((TextView) findViewById(R.id.auth_title)).setText(account ? R.string.auth_account_title
                : model.registering() ? R.string.auth_register_title : R.string.auth_signin_title);
        ((TextView) findViewById(R.id.auth_body)).setText(account ? state.email
                : getString(model.registering() ? R.string.auth_register_body : R.string.auth_signin_body));
        visible(R.id.auth_credentials, !account);
        visible(R.id.auth_account_actions, account);
        visible(R.id.auth_confirm_layout, !account && model.registering());
        visible(R.id.auth_submit, !account);
        visible(R.id.auth_switch, !account);
        visible(R.id.auth_google, !account && !model.registering());
        visible(R.id.auth_google_help, !google.available());
        visible(R.id.auth_progress, state.pending || state.setupLoading);
        visible(R.id.auth_logout, account || state.pending);
        visible(R.id.auth_retry_setup, account && !state.setupReady);
        visible(R.id.auth_profile, account && state.setupReady);
        button(R.id.auth_profile).setEnabled(account && state.setupReady && !state.pending && !state.setupLoading);
        visible(R.id.auth_pets, account);
        visible(R.id.auth_listings, account);
        visible(R.id.auth_community, account);
        button(R.id.auth_community).setEnabled(account && !state.pending);
        button(R.id.auth_listings).setEnabled(account && !state.pending);
        button(R.id.auth_pets).setEnabled(account && !state.pending);
        visible(R.id.auth_verify, false);
        visible(R.id.auth_proof_layout, account && state.canLink && google.available());
        visible(R.id.auth_link, account && (state.canLink || state.googleLinked));
        password.setAutofillHints(model.registering() ? "newPassword" : View.AUTOFILL_HINT_PASSWORD);
        button(R.id.auth_submit).setText(model.registering() ? R.string.auth_register : R.string.auth_signin);
        button(R.id.auth_switch).setText(model.registering() ? R.string.auth_switch_signin : R.string.auth_register);
        button(R.id.auth_link).setText(state.googleLinked ? R.string.auth_google_linked : R.string.auth_google_link);
        for (int id : new int[]{R.id.auth_submit,R.id.auth_switch,R.id.auth_refresh,R.id.auth_retry_setup})
            button(id).setEnabled(state.configured && !state.pending && !state.setupLoading);
        email.setEnabled(state.configured && !state.pending); password.setEnabled(state.configured && !state.pending); confirm.setEnabled(state.configured && !state.pending);
        button(R.id.auth_google).setEnabled(state.configured && !state.pending && google.available());
        button(R.id.auth_link).setEnabled(state.configured && !state.pending && state.canLink && google.available());
        proof.setEnabled(state.configured && !state.pending);
        button(R.id.auth_verify).setEnabled(!state.pending && state.canSend);
        StringBuilder status = new StringBuilder();
        if (account) {
            status.append(getString(!state.verificationKnown ? R.string.auth_verification_unknown
                    : state.verified ? R.string.auth_verified : R.string.auth_unverified));
            if (!state.setupReady) status.append('\n').append(getString(R.string.auth_setup_pending));
            if (state.membership == com.gmail.manhle240406.project.foundation.MembershipRepository.State.RESTRICTED)
                status.append('\n').append(getString(R.string.auth_restricted));
            else if (state.communityReady()) status.append('\n').append(getString(R.string.auth_ready));
            else if(state.verified && state.setupReady) status.append('\n').append(getString(R.string.auth_setup_pending));
        }
        int message = message(state.notice);
        if (message != 0) { if (status.length() > 0) status.append('\n'); status.append(getString(message)); }
        else if (state.pending) status.append(getString(R.string.auth_pending));
        ((TextView) findViewById(R.id.auth_status)).setText(status.toString());
    }
    private int message(AuthState.Notice notice) {
        switch (notice) {
            case CONFIGURATION: return R.string.auth_configuration_error;
            case INVALID_CREDENTIALS: return R.string.auth_credentials_error;
            case NETWORK: return R.string.auth_network_error;
            case THROTTLED: return R.string.auth_throttled;
            case CONFLICT: return R.string.auth_conflict;
            case CANCELLED: return R.string.auth_cancelled;
            case SENT: return R.string.auth_send_success;
            case SEND_FAILED: return R.string.auth_send_error;
            case COOLDOWN: return R.string.auth_cooldown;
            case OUTCOME_UNKNOWN: return R.string.auth_outcome_unknown;
            case PROVIDER_CLEAR_FAILED: return R.string.auth_provider_clear_failed;
            case PASSWORD_POLICY: return R.string.auth_provider_password_policy;
            default: return 0;
        }
    }
}
