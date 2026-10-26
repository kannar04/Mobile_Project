package com.gmail.manhle240406.project.community;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.adoption.FavoritesActivity;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.*;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class SocialRefreshUiTest {
    private AniloveApplication app;

    @Before
    public void account() throws Exception {
        app = ApplicationProvider.getApplicationContext();
        assertEquals("local_emulator", app.foundation().alias);
        Tasks.await(FirebaseAuth.getInstance().signInAnonymously(), 20, TimeUnit.SECONDS);
    }

    @After
    public void leave() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.auth().logout());
    }

    private androidx.test.espresso.ViewAction showKeyboard() {
        return new androidx.test.espresso.ViewAction() {
            public org.hamcrest.Matcher<View> getConstraints() {
                return isDisplayed();
            }

            public String getDescription() {
                return "show actual IME and await its visible inset";
            }

            public void perform(androidx.test.espresso.UiController ui, View v) {
                v.requestFocus();
                ((android.view.inputmethod.InputMethodManager)
                                v.getContext()
                                        .getSystemService(
                                                android.content.Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(
                                v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                long until = android.os.SystemClock.uptimeMillis() + 5000;
                while (android.os.SystemClock.uptimeMillis() < until) {
                    androidx.core.view.WindowInsetsCompat i =
                            androidx.core.view.ViewCompat.getRootWindowInsets(v);
                    if (i != null && i.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()))
                        return;
                    ui.loopMainThreadForAtLeast(16);
                }
                throw new AssertionError("IME did not become visible");
            }
        };
    }

    private void configure(ActivityScenario<?> scenario) {
        scenario.onActivity(
                a ->
                        a.setRequestedOrientation(
                                "landscape"
                                                .equals(
                                                        InstrumentationRegistry.getArguments()
                                                                .getString("orientation"))
                                        ? android.content.pm.ActivityInfo
                                                .SCREEN_ORIENTATION_LANDSCAPE
                                        : android.content.pm.ActivityInfo
                                                .SCREEN_ORIENTATION_PORTRAIT));
    }

    private void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(500, 5000);
        File folder = new File(app.getExternalFilesDir(null), "ui-social");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        Bitmap image =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        image.recycle();
    }

    @Test
    public void composerRetainsPrivateTextAndBackOffersDiscard() throws Exception {
        try (ActivityScenario<SocialComposerActivity> s =
                ActivityScenario.launch(SocialComposerActivity.class)) {
            configure(s);
            onView(withId(R.id.social_body_input))
                    .perform(
                            replaceText("A quiet sunny afternoon with Miu."),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            s.recreate();
            onView(withId(R.id.social_body_input))
                    .check(matches(withText("A quiet sunny afternoon with Miu.")));
            s.onActivity(
                    a -> {
                        assertFalse(a.findViewById(R.id.social_body_input).isSaveEnabled());
                        assertFalse(
                                new ViewModelProvider(a)
                                        .get(DailyViewModel.class)
                                        .saved
                                        .contains("body"));
                    });
            capture("composer");
            androidx.test.espresso.Espresso.pressBack();
            onView(withText(R.string.community_discard))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(click());
        }
    }

    @Test
    public void commentDraftAndControlsSurviveRecreationWithoutFalseSend() throws Exception {
        Intent i =
                new Intent(app, SocialDetailActivity.class)
                        .putExtra("post", UUID.randomUUID().toString());
        try (ActivityScenario<SocialDetailActivity> s = ActivityScenario.launch(i)) {
            configure(s);
            onView(withId(R.id.social_send_action)).check(matches(not(isEnabled())));
            onView(withId(R.id.social_comment_input)).check(matches(isDisplayed()));
            capture("comments-unavailable");
            s.onActivity(
                    a -> {
                        DailyViewModel m = new ViewModelProvider(a).get(DailyViewModel.class);
                        m.projection = new HashMap<>();
                        m.projection.put(
                                "body",
                                "A quiet afternoon with Miu. What games does your pet enjoy?");
                        m.projection.put("contentVersion", 1L);
                        m.notice = "";
                        m.changed();
                    });
            onView(withId(R.id.social_comment_input))
                    .perform(
                            replaceText("My cat likes playing in a cardboard box."),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            onView(withId(R.id.social_send_action)).check(matches(isEnabled()));
            capture("comments");
            onView(withId(R.id.social_comment_input)).perform(click(), showKeyboard());
            onView(withId(R.id.social_send_action)).check(matches(isDisplayed()));
            capture("comments-keyboard");
            androidx.test.espresso.Espresso.closeSoftKeyboard();
            s.recreate();
            onView(withId(R.id.social_comment_input))
                    .check(matches(withText("My cat likes playing in a cardboard box.")));
        }
    }

    @Test
    public void favoritesNeverPresentsSamplePetsAsSavedData() throws Exception {
        try (ActivityScenario<FavoritesActivity> s =
                ActivityScenario.launch(FavoritesActivity.class)) {
            configure(s);
            onView(withText(R.string.ui_saved_unconnected)).check(matches(isDisplayed()));
            onView(withText("B\u00f4ng (Ch\u00f3 Poodle)")).check(doesNotExist());
            capture("favorites");
            s.recreate();
            onView(withText(R.string.ui_saved_unconnected)).check(matches(isDisplayed()));
        }
    }
}
