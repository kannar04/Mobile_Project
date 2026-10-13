package com.gmail.manhle240406.project.auth;

import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.google.android.material.textfield.TextInputEditText;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AuthUiIntegrationTest {
    @Test public void rotationKeepsModeButNeverRestoresPassword() {
        AniloveApplication app = ApplicationProvider.getApplicationContext();
        app.auth().logout();
        try (ActivityScenario<AuthActivity> activity = ActivityScenario.launch(
                new Intent(app, AuthActivity.class))) {
            activity.onActivity(a -> {
                a.findViewById(R.id.auth_switch).performClick();
                ((TextInputEditText) a.findViewById(R.id.auth_password)).setText("synthetic-password-123");
                ((TextInputEditText) a.findViewById(R.id.auth_confirm)).setText("synthetic-password-123");
            });
            activity.recreate();
            activity.onActivity(a -> {
                assertEquals(android.view.View.VISIBLE, a.findViewById(R.id.auth_confirm_layout).getVisibility());
                assertEquals("", ((TextInputEditText) a.findViewById(R.id.auth_password)).getText().toString());
                assertEquals("", ((TextInputEditText) a.findViewById(R.id.auth_confirm)).getText().toString());
                assertFalse(app.auth().busy());
            });
        }
    }
    @Test public void malformedRegistrationRemainsLocalAndAssociatesErrorWithField() {
        AniloveApplication app = ApplicationProvider.getApplicationContext();
        app.auth().logout();
        try (ActivityScenario<AuthActivity> activity = ActivityScenario.launch(new Intent(app, AuthActivity.class))) {
            activity.onActivity(a -> {
                a.findViewById(R.id.auth_switch).performClick();
                ((TextInputEditText) a.findViewById(R.id.auth_email)).setText("not-an-email");
                a.findViewById(R.id.auth_submit).performClick();
                assertNotNull(((com.google.android.material.textfield.TextInputLayout)
                        a.findViewById(R.id.auth_email_layout)).getError());
                assertFalse(app.auth().busy());
            });
        }
    }
}
