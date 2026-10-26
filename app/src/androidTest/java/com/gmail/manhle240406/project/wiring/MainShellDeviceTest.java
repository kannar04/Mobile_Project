package com.gmail.manhle240406.project.wiring;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static org.junit.Assert.*;

import android.app.*;
import android.content.Context;
import android.os.*;
import android.view.*;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.SocialComposerActivity;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import org.junit.*;
import org.junit.runner.RunWith;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Native operations with no account switching, fixture writes, sign-out or personal evidence. */
@RunWith(AndroidJUnit4.class)
public final class MainShellDeviceTest {
    private static String originalUid;

    @BeforeClass
    public static void signedInProfile() throws Exception {
        long until = SystemClock.elapsedRealtime() + 15000;
        while (FirebaseAuth.getInstance().getCurrentUser() == null
                && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100);
        assertNotNull(
                "Sign in and complete your own display name before this read-only journey",
                FirebaseAuth.getInstance().getCurrentUser());
        originalUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DocumentSnapshot draft =
                Tasks.await(
                        FirebaseFirestore.getInstance()
                                .document("users/" + originalUid + "/profileDraft/current")
                                .get(Source.SERVER),
                        20,
                        TimeUnit.SECONDS);
        assertTrue(
                "The user must complete private profile setup",
                draft.exists()
                        && com.gmail.manhle240406.project.profile.ProfileSchema.validContent(
                                draft.getData()));
    }

    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private void ready(ActivityScenario<MainActivity> scenario) throws Exception {
        AtomicBoolean ready = new AtomicBoolean();
        long until = SystemClock.elapsedRealtime() + 20000;
        do {
            scenario.onActivity(
                    a -> ready.set(a.findViewById(R.id.home_notifications_btn) != null));
            if (!ready.get()) SystemClock.sleep(100);
        } while (!ready.get() && SystemClock.elapsedRealtime() < until);
        assertTrue("Main shell must finish its own private-name check", ready.get());
    }

    @After
    public void preserveAccount() {
        assertNotNull(FirebaseAuth.getInstance().getCurrentUser());
        assertEquals(
                "Test must preserve selected identity",
                originalUid,
                FirebaseAuth.getInstance().getCurrentUser().getUid());
    }

    @Test
    public void tabsReuseOneActivityOneBarAndHomeContent() throws Exception {
        AtomicInteger otherActivities = new AtomicInteger();
        Application app = (Application) context().getApplicationContext();
        Application.ActivityLifecycleCallbacks watcher =
                new Application.ActivityLifecycleCallbacks() {
                    public void onActivityCreated(Activity a, Bundle b) {
                        if (!(a instanceof MainActivity)) otherActivities.incrementAndGet();
                    }

                    public void onActivityStarted(Activity a) {}

                    public void onActivityResumed(Activity a) {}

                    public void onActivityPaused(Activity a) {}

                    public void onActivityStopped(Activity a) {}

                    public void onActivitySaveInstanceState(Activity a, Bundle b) {}

                    public void onActivityDestroyed(Activity a) {}
                };
        app.registerActivityLifecycleCallbacks(watcher);
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            ready(scenario);
            AtomicReference<View> originalBar = new AtomicReference<>();
            AtomicReference<Fragment> originalHome = new AtomicReference<>();
            scenario.onActivity(
                    a -> {
                        originalBar.set(a.findViewById(R.id.bottom_navigation));
                        originalHome.set(a.getSupportFragmentManager().findFragmentByTag("home"));
                    });
            int[] tabs = {
                R.id.nav_favorites,
                R.id.nav_chat,
                R.id.nav_profile,
                R.id.nav_home,
                R.id.nav_favorites,
                R.id.nav_home
            };
            String[] pages = {"favorites", "chat", "profile", "home", "favorites", "home"};
            for (int i = 0; i < tabs.length; i++) {
                onView(withId(tabs[i])).perform(click());
                final String page = pages[i];
                scenario.onActivity(
                        a -> {
                            assertEquals(page, a.destination());
                            assertSame(originalBar.get(), a.findViewById(R.id.bottom_navigation));
                            assertSame(
                                    originalHome.get(),
                                    a.getSupportFragmentManager().findFragmentByTag("home"));
                            assertTrue(
                                    a.getSupportFragmentManager()
                                            .getPrimaryNavigationFragment()
                                            .isVisible());
                        });
                onView(withId(R.id.bottom_navigation)).check(matches(isDisplayed()));
            }
            assertEquals("Tab changes must not launch another Activity", 0, otherActivities.get());
        } finally {
            app.unregisterActivityLifecycleCallbacks(watcher);
        }
    }

    @Test
    public void homeNotificationsStayInShellAndBackReturnsHome() throws Exception {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            ready(scenario);
            onView(withId(R.id.home_notifications_btn)).perform(click());
            scenario.onActivity(
                    a -> {
                        assertEquals("notifications", a.destination());
                        assertNotNull(a.findViewById(R.id.inbox_root));
                        assertEquals(
                                R.id.nav_home,
                                ((BottomNavigationView) a.findViewById(R.id.bottom_navigation))
                                        .getSelectedItemId());
                    });
            onView(withId(R.id.bottom_navigation)).check(matches(isDisplayed()));
            pressBack();
            scenario.onActivity(a -> assertEquals("home", a.destination()));
            onView(withId(R.id.home_notifications_btn)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void selectedTabAndContentRestoreAfterRecreation() throws Exception {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            ready(scenario);
            onView(withId(R.id.nav_chat)).perform(click());
            scenario.recreate();
            long until = SystemClock.elapsedRealtime() + 20000;
            AtomicBoolean visible = new AtomicBoolean();
            do {
                scenario.onActivity(
                        a ->
                                visible.set(
                                        a.findViewById(R.id.bottom_navigation).getVisibility()
                                                == View.VISIBLE));
                if (!visible.get()) SystemClock.sleep(100);
            } while (!visible.get() && SystemClock.elapsedRealtime() < until);
            assertTrue(visible.get());
            scenario.onActivity(
                    a -> {
                        assertEquals("chat", a.destination());
                        assertEquals(
                                R.id.nav_chat,
                                ((BottomNavigationView) a.findViewById(R.id.bottom_navigation))
                                        .getSelectedItemId());
                        assertTrue(
                                a.getSupportFragmentManager().getPrimaryNavigationFragment()
                                        instanceof
                                        com.gmail.manhle240406.project.community
                                                .ConversationsFragment);
                    });
            onView(withId(R.id.bottom_navigation)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void closingAccountWindowDuringTokenRefreshKeepsSignedInSession() throws Exception {
        try (ActivityScenario<com.gmail.manhle240406.project.auth.AuthActivity> screen =
                ActivityScenario.launch(
                        new android.content.Intent(
                                        context(),
                                        com.gmail.manhle240406.project.auth.AuthActivity.class)
                                .putExtra("account", true))) {
            screen.onActivity(
                    a -> ((AniloveApplication) a.getApplication()).auth().refreshVerification());
        }
        // Closing a window must never become a logout intent, including while Firebase reloads.
        assertNotNull(FirebaseAuth.getInstance().getCurrentUser());
        assertEquals(originalUid, FirebaseAuth.getInstance().getCurrentUser().getUid());
    }

    @Test
    public void profileKeyboardReservesContentSpaceAndBarReturnsWhenDismissed() throws Exception {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            ready(scenario);
            onView(withId(R.id.nav_profile)).perform(click());
            AtomicBoolean enabled = new AtomicBoolean();
            long until = SystemClock.elapsedRealtime() + 20000;
            do {
                scenario.onActivity(
                        a -> {
                            View edit = a.findViewById(R.id.profile_edit);
                            enabled.set(edit != null && edit.isEnabled());
                        });
                if (!enabled.get()) SystemClock.sleep(100);
            } while (!enabled.get() && SystemClock.elapsedRealtime() < until);
            assertTrue("Private profile must load", enabled.get());
            onView(withId(R.id.profile_edit)).perform(click());
            onView(withId(R.id.profile_name)).perform(click());
            AtomicBoolean keyboard = new AtomicBoolean();
            until = SystemClock.elapsedRealtime() + 10000;
            do {
                scenario.onActivity(
                        a -> {
                            androidx.core.view.WindowInsetsCompat insets =
                                    androidx.core.view.ViewCompat.getRootWindowInsets(
                                            a.findViewById(R.id.main_root));
                            keyboard.set(
                                    insets != null
                                            && insets.isVisible(
                                                    androidx.core.view.WindowInsetsCompat.Type
                                                            .ime()));
                        });
                if (!keyboard.get()) SystemClock.sleep(100);
            } while (!keyboard.get() && SystemClock.elapsedRealtime() < until);
            assertTrue("Keyboard must be shown", keyboard.get());
            scenario.onActivity(
                    a -> {
                        int ime =
                                androidx.core.view.ViewCompat.getRootWindowInsets(
                                                a.findViewById(R.id.main_root))
                                        .getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
                                        .bottom;
                        assertEquals(
                                View.GONE, a.findViewById(R.id.bottom_navigation).getVisibility());
                        assertTrue(
                                a.findViewById(R.id.main_fragment_container).getPaddingBottom()
                                        >= ime);
                    });
            androidx.test.uiautomator.UiDevice.getInstance(
                            InstrumentationRegistry.getInstrumentation())
                    .pressBack();
            onView(withText(R.string.oc2_cancel)).perform(click());
            until = SystemClock.elapsedRealtime() + 10000;
            AtomicBoolean returned = new AtomicBoolean();
            do {
                scenario.onActivity(
                        a ->
                                returned.set(
                                        a.findViewById(R.id.bottom_navigation).getVisibility()
                                                == View.VISIBLE));
                if (!returned.get()) SystemClock.sleep(100);
            } while (!returned.get() && SystemClock.elapsedRealtime() < until);
            assertTrue("Navigation returns after editor closes", returned.get());
        }
    }

    @Test
    public void composerObservesVerificationAndRestrictionPresentationChanges() throws Exception {
        AniloveApplication app = (AniloveApplication) context().getApplicationContext();
        long until = SystemClock.elapsedRealtime() + 20000;
        while ((app.auth().state().getValue() == null
                        || !app.auth().state().getValue().communityReady())
                && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100);
        com.gmail.manhle240406.project.auth.AuthState original = app.auth().state().getValue();
        assertNotNull(original);
        androidx.lifecycle.MutableLiveData<com.gmail.manhle240406.project.auth.AuthState>
                presentation =
                        (androidx.lifecycle.MutableLiveData<
                                        com.gmail.manhle240406.project.auth.AuthState>)
                                app.auth().state();
        // Presentation inputs only: never change Firebase identity, token, membership or any
        // document.
        try (ActivityScenario<SocialComposerActivity> screen =
                ActivityScenario.launch(SocialComposerActivity.class)) {
            boolean[] verified = {false, true, true};
            com.gmail.manhle240406.project.foundation.MembershipRepository.State[] membership = {
                com.gmail.manhle240406.project.foundation.MembershipRepository.State.READY,
                com.gmail.manhle240406.project.foundation.MembershipRepository.State.RESTRICTED,
                com.gmail.manhle240406.project.foundation.MembershipRepository.State.READY
            };
            for (int i = 0; i < 3; i++) {
                final int step = i;
                screen.onActivity(
                        a -> {
                            com.gmail.manhle240406.project.auth.AuthState fixture =
                                    new com.gmail.manhle240406.project.auth.AuthState(
                                            originalUid,
                                            null,
                                            false,
                                            true,
                                            verified[step],
                                            true,
                                            false,
                                            true,
                                            com.gmail.manhle240406.project.auth.AuthState.Authority
                                                    .MISSING,
                                            com.gmail.manhle240406.project.auth.AuthState.Notice
                                                    .NONE,
                                            true,
                                            true,
                                            false);
                            fixture.membership = membership[step];
                            presentation.setValue(fixture);
                            assertEquals(
                                    step == 2,
                                    a.findViewById(R.id.social_publish_action).isEnabled());
                        });
            }
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .runOnMainSync(() -> presentation.setValue(original));
        }
    }
}
