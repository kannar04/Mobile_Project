package com.gmail.manhle240406.project.wiring;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.adoption.*;
import com.gmail.manhle240406.project.auth.*;
import com.gmail.manhle240406.project.profile.*;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.*;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Real on-device navigation via visible operations, not exported-Activity launch claims. Existing
 * accounts are preserved; only a newly-created synthetic identity is deleted. No valid password
 * change, email delivery, report, publication or adoption mutation is submitted.
 */
@RunWith(AndroidJUnit4.class)
public final class NativeFlowJourneyTest {
    private static FirebaseUser fixture;
    private static File evidence;

    private static AniloveApplication app() {
        return (AniloveApplication)
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getApplicationContext();
    }

    @BeforeClass
    public static void session() throws Exception {
        fixture = null;
        evidence = new File(app().getExternalFilesDir(null), "flow-evidence");
        assertTrue(evidence.mkdirs() || evidence.isDirectory());
        awaitAuthIdle();
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            String key = UUID.randomUUID().toString();
            fixture =
                    Tasks.await(
                                    FirebaseAuth.getInstance()
                                            .createUserWithEmailAndPassword(
                                                    "native-flow-" + key + "@example.invalid",
                                                    key + UUID.randomUUID()),
                                    30,
                                    TimeUnit.SECONDS)
                            .getUser();
            // UID only, retained privately for exact minimal-shell teardown by the owner CLI.
            try (FileWriter writer = new FileWriter(new File(evidence, "fixture-private.json"))) {
                writer.write(
                        new JSONObject()
                                .put("uid", fixture.getUid())
                                .put("createdByThisRun", true)
                                .toString());
            }
        } else {
            File marker = new File(evidence, "fixture-private.json");
            FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
            if (marker.isFile())
                try (FileInputStream input = new FileInputStream(marker)) {
                    JSONObject prior =
                            new JSONObject(
                                    new String(
                                            input.readAllBytes(),
                                            java.nio.charset.StandardCharsets.UTF_8));
                    if (prior.optBoolean("createdByThisRun")
                            && current.getUid().equals(prior.optString("uid"))
                            && current.getEmail() != null
                            && current.getEmail().startsWith("native-flow-")
                            && current.getEmail().endsWith("@example.invalid")) fixture = current;
                }
        }
        if (fixture != null)
            try (FileWriter writer =
                    new FileWriter(new File(evidence, "fixture-history-private.jsonl"), true)) {
                writer.write(
                        new JSONObject()
                                        .put("uid", fixture.getUid())
                                        .put("createdByThisRun", true)
                                        .toString()
                                + "\n");
            }
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> app().auth().refreshVerification());
        awaitAuthIdle();
        assertNotNull(
                "Fixture session must settle before starting UI tests",
                FirebaseAuth.getInstance().getCurrentUser());
        if (fixture != null)
            assertEquals(fixture.getUid(), FirebaseAuth.getInstance().getCurrentUser().getUid());
    }

    private static void awaitAuthIdle() throws Exception {
        java.util.concurrent.CountDownLatch idle = new java.util.concurrent.CountDownLatch(1);
        androidx.lifecycle.Observer<com.gmail.manhle240406.project.auth.AuthState> observer =
                state -> {
                    if (state != null && !state.pending && !state.setupLoading) idle.countDown();
                };
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> app().auth().state().observeForever(observer));
        try {
            assertTrue("Auth startup/refresh must settle", idle.await(35, TimeUnit.SECONDS));
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .runOnMainSync(() -> app().auth().state().removeObserver(observer));
        }
    }

    @Test
    public void androidSearchNormalizerAcceptsUnicodeSpacing() {
        assertEquals("meo do", DiscoveryQuery.normalize(" M\u00e8o\u00a0\u0110\u1ecf ", 32));
        assertEquals("", new DiscoveryQuery("", "available", Collections.emptyMap()).prefix);
    }

    @AfterClass
    public static void cleanup() throws Exception {
        if (fixture != null) {
            FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
            if (current != null) assertEquals(fixture.getUid(), current.getUid());
            boolean deleted = current != null;
            try {
                if (current != null) Tasks.await(fixture.delete(), 30, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException error) {
                if (!(error.getCause() instanceof FirebaseAuthRecentLoginRequiredException))
                    throw error;
                // Long device sweeps can exceed provider recent-login freshness. The owner CLI
                // verifies and tears down ONLY recorded synthetic identities after this run.
                deleted = false;
                FirebaseAuth.getInstance().signOut();
            }
            try (FileWriter writer = new FileWriter(new File(evidence, "auth-cleanup.json"))) {
                writer.write(
                        new JSONObject()
                                .put("syntheticAuthDeleted", deleted)
                                .put("trustedTeardownRequired", !deleted)
                                .toString());
            }
            try (FileWriter writer =
                    new FileWriter(new File(evidence, "auth-cleanup-private.jsonl"), true)) {
                writer.write(
                        new JSONObject()
                                        .put("uid", fixture.getUid())
                                        .put("deleted", deleted)
                                        .toString()
                                + "\n");
            }
            fixture = null;
        }
    }

    @After
    public void closeScreens() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            Set<Activity> screens = new HashSet<>();
                            for (Stage s :
                                    Arrays.asList(
                                            Stage.RESUMED,
                                            Stage.STARTED,
                                            Stage.CREATED,
                                            Stage.PAUSED,
                                            Stage.STOPPED))
                                screens.addAll(
                                        ActivityLifecycleMonitorRegistry.getInstance()
                                                .getActivitiesInStage(s));
                            for (Activity a : screens) a.finish();
                        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void screenshot(String name) throws Exception {
        Bitmap image =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        try (FileOutputStream out = new FileOutputStream(new File(evidence, name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        image.recycle();
    }

    private Class<?> resumed() {
        final Class<?>[] result = {null};
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            for (Activity a :
                                    ActivityLifecycleMonitorRegistry.getInstance()
                                            .getActivitiesInStage(Stage.RESUMED))
                                result[0] = a.getClass();
                        });
        return result[0];
    }

    private void waitForActivity(Class<?> expected) {
        onView(isRoot())
                .perform(
                        new ViewAction() {
                            public org.hamcrest.Matcher<View> getConstraints() {
                                return isRoot();
                            }

                            public String getDescription() {
                                return "wait for expected destination";
                            }

                            public void perform(UiController controller, View view) {
                                long end = SystemClock.uptimeMillis() + 15000;
                                while (SystemClock.uptimeMillis() < end) {
                                    for (Activity a :
                                            ActivityLifecycleMonitorRegistry.getInstance()
                                                    .getActivitiesInStage(Stage.RESUMED))
                                        if (a.getClass() == expected) return;
                                    controller.loopMainThreadForAtLeast(100);
                                }
                                throw new AssertionError(
                                        "Destination not resumed: " + expected.getSimpleName());
                            }
                        });
    }

    @Test
    public void everyApprovedFeatureHasAVisibleOperationSequence() throws Exception {
        JSONArray results = new JSONArray();
        List<String> failures = new ArrayList<>();
        for (FlowRoutes.Route route : FlowRoutes.ALL) {
            JSONObject result =
                    new JSONObject()
                            .put("feature", route.feature)
                            .put("sequence", "Explore > category > task");
            try (ActivityScenario<FlowHubActivity> root =
                    ActivityScenario.launch(FlowHubActivity.class)) {
                onView(withTagValue(is("flow_group_" + route.group))).perform(scrollTo(), click());
                onView(withTagValue(is("flow_" + route.feature))).perform(scrollTo(), click());
                if (route.contextual) {
                    onView(withText(route.title)).inRoot(isDialog()).check(matches(isDisplayed()));
                    result.put("outcome", "contextual_unavailable_dialog");
                } else {
                    Class<?> expected = route.activity;
                    waitForActivity(expected);
                    result.put(
                            "outcome",
                            SparkCapabilities.MANAGED_ROUTES.contains(expected.getSimpleName())
                                    ? "managed_unavailable_state"
                                    : "destination_reached");
                }
                screenshot(route.feature);
                result.put("passed", true);
            } catch (Throwable error) {
                result.put("passed", false).put("failure", error.getClass().getSimpleName());
                failures.add(route.feature + ":" + error.getClass().getSimpleName());
            } finally {
                results.put(result);
                closeScreens();
            }
        }
        try (FileWriter writer = new FileWriter(new File(evidence, "route-results.json"))) {
            writer.write(
                    new JSONObject().put("routes", results).put("approvedCount", 43).toString(2));
        }
        assertEquals("All43 approved feature entries must be reachable", 43, results.length());
        assertTrue(failures.toString(), failures.isEmpty());
    }

    @Test
    public void launcherHomeExploreAndBottomNavigationHaveRealSequences() throws Exception {
        try (ActivityScenario<AuthActivity> screen = ActivityScenario.launch(AuthActivity.class)) {
            waitForActivity(MainActivity.class);
            onView(withId(R.id.home_filter_btn)).perform(click());
            waitForActivity(FlowHubActivity.class);
            onView(withTagValue(is("flow_group_4"))).perform(scrollTo(), click());
            onView(withTagValue(is("flow_F38"))).perform(scrollTo(), click());
            waitForActivity(EnhancedSettingsActivity.class);
            screenshot("settings");
        }
        closeScreens();
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_chat)).perform(click());
            waitForActivity(com.gmail.manhle240406.project.notifications.InboxActivity.class);
            Espresso.pressBack();
            onView(withId(R.id.nav_favorites)).perform(click());
            waitForActivity(FavoritesActivity.class);
            Espresso.pressBack();
            onView(withId(R.id.nav_profile)).perform(click());
            waitForActivity(ProfileActivity.class);
            onView(withId(R.id.profile_tools)).perform(scrollTo(), click());
            waitForActivity(FlowHubActivity.class);
        }
    }

    @Test
    public void passwordValidationAndRotationNeverRetainCredentials() throws Exception {
        try (ActivityScenario<PasswordChangeActivity> screen =
                ActivityScenario.launch(PasswordChangeActivity.class)) {
            onView(withId(R.id.flow_submit)).perform(scrollTo(), click());
            onView(withId(R.id.flow_confirm_password))
                    .check(matches(hasErrorText(app().getString(R.string.flow_password_invalid))));
            onView(withId(R.id.flow_current_password))
                    .perform(scrollTo(), replaceText("transient-test"), closeSoftKeyboard());
            screen.recreate();
            onView(withId(R.id.flow_current_password)).check(matches(withText("")));
            onView(withId(R.id.flow_new_password)).check(matches(withText("")));
            screenshot("password-change");
        }
    }

    @Test
    public void accountDeletionHasExplicitAvailabilityAndSafeExit() throws Exception {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        try (ActivityScenario<AccountDeletionActivity> screen =
                ActivityScenario.launch(AccountDeletionActivity.class)) {
            onView(withId(R.id.flow_submit)).perform(scrollTo(), click());
            onView(withId(R.id.flow_status))
                    .check(matches(withText(R.string.flow_delete_unavailable)));
            assertEquals(uid, FirebaseAuth.getInstance().getCurrentUser().getUid());
            screenshot("delete-availability");
            onView(withText(R.string.flow_keep_account)).perform(scrollTo(), click());
        }
    }

    @Test
    public void discoveryFilterCancelAndApplySurviveRecreation() throws Exception {
        try (ActivityScenario<DiscoveryActivity> screen =
                ActivityScenario.launch(DiscoveryActivity.class)) {
            onView(withId(R.id.flow_filters)).perform(click());
            onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.flow_filter_summary)).check(matches(withText(R.string.flow_newest)));
            onView(withId(R.id.flow_filters)).perform(click());
            onView(withText(R.string.flow_apply_filters)).inRoot(isDialog()).perform(click());
            screen.recreate();
            onView(withId(R.id.flow_filter_summary)).check(matches(withText(R.string.flow_newest)));
            screenshot("discovery");
        }
    }

    @Test
    public void unknownListingFailsClosedWithoutFakePetOrSuccess() throws Exception {
        Intent intent =
                new Intent(app(), ListingDetailActivity.class)
                        .putExtra("listing", "not-a-valid-id");
        try (ActivityScenario<ListingDetailActivity> screen = ActivityScenario.launch(intent)) {
            onView(withText(R.string.wiring_unavailable_item)).check(matches(isDisplayed()));
            onView(withId(R.id.flow_detail_save)).check(doesNotExist());
            screen.recreate();
            onView(withText(R.string.wiring_unavailable_item)).check(matches(isDisplayed()));
        }
    }
}
