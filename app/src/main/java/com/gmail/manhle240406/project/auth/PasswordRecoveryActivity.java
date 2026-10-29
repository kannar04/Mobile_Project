package com.gmail.manhle240406.project.auth;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.*;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.community.SocialViews;
import com.gmail.manhle240406.project.security.AccountSecurityRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Existing recovery form bound to the provider; request acknowledgement never claims password
 * changed.
 */
public final class PasswordRecoveryActivity extends AppCompatActivity {
    public static final class Pending extends AndroidViewModel {
        public final MutableLiveData<AccountSecurityRepository.Result> result =
                new MutableLiveData<>();
        public final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
        final AccountSecurityRepository repo;

        public Pending(android.app.Application app) {
            super(app);
            repo = ((AniloveApplication) app).services().security;
        }

        public void send(String email, String language) {
            if (Boolean.TRUE.equals(busy.getValue())) return;
            if (repo == null) {
                result.setValue(AccountSecurityRepository.Result.CONFIGURATION_REQUIRED);
                return;
            }
            busy.setValue(true);
            repo.requestReset(email, language)
                    .whenComplete(
                            (value, error) -> {
                                result.postValue(
                                        error == null
                                                ? value
                                                : AccountSecurityRepository.Result.UNAVAILABLE);
                                busy.postValue(false);
                            });
        }
    }

    private androidx.appcompat.app.AlertDialog dialog;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_password_recovery);
        SocialViews.insets(findViewById(R.id.recovery_root));
        EditText email = findViewById(R.id.input_recovery_email);
        email.setSaveEnabled(false);
        email.setSaveFromParentEnabled(false);
        if (state != null) email.setText(state.getString("recovery_email", ""));
        findViewById(R.id.recovery_back).setOnClickListener(v -> finish());
        TextView send = findViewById(R.id.btn_send_recovery);
        send.setText(R.string.wiring_reset_action);
        Pending model = new ViewModelProvider(this).get(Pending.class);
        send.setOnClickListener(
                v -> {
                    model.result.setValue(null);
                    String lang =
                            getResources().getConfiguration().getLocales().get(0).getLanguage();
                    model.send(email.getText().toString(), "en".equals(lang) ? "en" : "vi");
                });
        model.busy.observe(this, b -> send.setEnabled(!Boolean.TRUE.equals(b)));
        model.result.observe(
                this,
                r -> {
                    if (r == null) return;
                    int message;
                    switch (r) {
                        case CHECK_EMAIL_IF_ELIGIBLE:
                            message = R.string.wiring_reset_neutral;
                            break;
                        case INVALID_INPUT:
                            email.setError(getString(R.string.auth_email_invalid));
                            email.requestFocus();
                            return;
                        case BUSY:
                        case COOLDOWN:
                            message = R.string.wiring_reset_wait;
                            break;
                        case CONFIGURATION_REQUIRED:
                            message = R.string.wiring_reset_configuration;
                            break;
                        case SESSION_CHANGED:
                            return;
                        default:
                            message = R.string.wiring_reset_failed;
                    }
                    if (dialog != null) dialog.dismiss();
                    dialog =
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle(R.string.wiring_reset_title)
                                    .setMessage(message)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        String email = ((EditText) findViewById(R.id.input_recovery_email)).getText().toString();
        if (email.length() <= 254) state.putString("recovery_email", email);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        super.onDestroy();
    }
}
