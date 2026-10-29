package com.gmail.manhle240406.project.wiring;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static org.junit.Assert.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.auth.PasswordRecoveryActivity;
import com.gmail.manhle240406.project.preferences.AppearanceRepository;
import com.gmail.manhle240406.project.profile.EnhancedSettingsActivity;
import com.google.firebase.FirebaseApp;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/** Runs against the installed Blaze course build. Server authorization remains authoritative. */
@RunWith(AndroidJUnit4.class)
public final class SparkWiringNativeTest {
    private AniloveApplication app() {
        return (AniloveApplication)
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getApplicationContext();
    }

    @Test
    public void realProjectIsSelectedAndManagedClientsAreReachable() {
        assertEquals("aniloveproject", FirebaseApp.getInstance().getOptions().getProjectId());
        assertEquals("course_demo", app().foundation().alias);
        assertTrue(SparkCapabilities.uploadsEnabled());
        assertTrue(SparkCapabilities.trustedCommandsEnabled());
        assertTrue(SparkCapabilities.MANAGED_ROUTES.isEmpty());
    }

    @Test
    public void invalidRecoveryInputNeverClaimsAnEmailWasSent() {
        try (ActivityScenario<PasswordRecoveryActivity> screen =
                ActivityScenario.launch(PasswordRecoveryActivity.class)) {
            onView(withId(R.id.input_recovery_email))
                    .perform(replaceText("invalid"), closeSoftKeyboard());
            onView(withId(R.id.btn_send_recovery)).perform(click());
            onView(withId(R.id.input_recovery_email))
                    .check(matches(hasErrorText(app().getString(R.string.auth_email_invalid))));
            screen.recreate();
            onView(withId(R.id.input_recovery_email)).check(matches(withText("invalid")));
            onView(withText(R.string.wiring_reset_neutral)).check(doesNotExist());
        }
    }

    @Test
    public void themeSelectionPersistsAndCanBeRestored() throws Exception {
        AppearanceRepository.State before =
                app().services().appearance.read().toCompletableFuture().get(5, TimeUnit.SECONDS);
        try (ActivityScenario<EnhancedSettingsActivity> screen =
                ActivityScenario.launch(EnhancedSettingsActivity.class)) {
            app().services().appearance.read().toCompletableFuture().get(5, TimeUnit.SECONDS);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            onView(withId(R.id.btn_setting_theme)).perform(click());
            onView(withText(R.string.wiring_dark)).perform(click());
            AppearanceRepository.State saved =
                    app().services()
                            .appearance
                            .read()
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
            assertEquals(AppearanceRepository.Theme.DARK, saved.theme);
        } finally {
            AppearanceRepository.State restored =
                    app().services()
                            .appearance
                            .set(before.language, before.theme)
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
            InstrumentationRegistry.getInstrumentation()
                    .runOnMainSync(() -> app().services().applyAppearance(restored));
        }
    }
}
