package com.gmail.manhle240406.project.wiring;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.auth.PasswordRecoveryActivity;
import com.gmail.manhle240406.project.media.PhotoGalleryActivity;
import com.gmail.manhle240406.project.adoption.LostAndFoundActivity;
import com.gmail.manhle240406.project.profile.EnhancedSettingsActivity;
import com.gmail.manhle240406.project.preferences.AppearanceRepository;
import com.google.firebase.FirebaseApp;
import java.util.concurrent.TimeUnit;

/** Runs against the installed Spark APK. No account creation, email send, upload or billing action. */
@RunWith(AndroidJUnit4.class)
public final class SparkWiringNativeTest {
    private AniloveApplication app(){return (AniloveApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();}
    @Test public void realProjectIsSelectedAndPaidCapabilitiesAreOff(){
        assertEquals("aniloveproject",FirebaseApp.getInstance().getOptions().getProjectId());
        assertEquals("course_demo",app().foundation().alias);
        assertFalse(SparkCapabilities.uploadsEnabled());assertFalse(SparkCapabilities.trustedCommandsEnabled());
    }
    @Test public void uploadRouteShowsPausedStateInsteadOfFakeProgress(){
        try(ActivityScenario<PhotoGalleryActivity> screen=ActivityScenario.launch(PhotoGalleryActivity.class)){
            onView(withText(R.string.wiring_uploads_paused)).check(matches(isDisplayed()));
            screen.recreate();onView(withText(R.string.wiring_uploads_paused)).check(matches(isDisplayed()));
        }
    }
    @Test public void managedRouteCannotShowSyntheticClaimSuccess(){
        try(ActivityScenario<LostAndFoundActivity> screen=ActivityScenario.launch(LostAndFoundActivity.class)){
            onView(withText(R.string.wiring_service_unavailable)).check(matches(isDisplayed()));
        }
    }
    @Test public void invalidRecoveryInputNeverClaimsAnEmailWasSent(){
        try(ActivityScenario<PasswordRecoveryActivity> screen=ActivityScenario.launch(PasswordRecoveryActivity.class)){
            onView(withId(R.id.input_recovery_email)).perform(replaceText("invalid"),closeSoftKeyboard());
            onView(withId(R.id.btn_send_recovery)).perform(click());
            onView(withId(R.id.input_recovery_email)).check(matches(hasErrorText(app().getString(R.string.auth_email_invalid))));
            screen.recreate();onView(withId(R.id.input_recovery_email)).check(matches(withText("invalid")));
            onView(withText(R.string.wiring_reset_neutral)).check(doesNotExist());
        }
    }
    @Test public void themeSelectionPersistsAndCanBeRestored() throws Exception {
        AppearanceRepository.State before=app().services().appearance.read().toCompletableFuture().get(5,TimeUnit.SECONDS);
        try(ActivityScenario<EnhancedSettingsActivity> screen=ActivityScenario.launch(EnhancedSettingsActivity.class)){
            app().services().appearance.read().toCompletableFuture().get(5,TimeUnit.SECONDS);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            onView(withId(R.id.btn_setting_theme)).perform(click());
            onView(withText(R.string.wiring_dark)).perform(click());
            AppearanceRepository.State saved=app().services().appearance.read().toCompletableFuture().get(5,TimeUnit.SECONDS);
            assertEquals(AppearanceRepository.Theme.DARK,saved.theme);
        } finally {
            AppearanceRepository.State restored=app().services().appearance.set(before.language,before.theme).toCompletableFuture().get(5,TimeUnit.SECONDS);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app().services().applyAppearance(restored));
        }
    }
}
