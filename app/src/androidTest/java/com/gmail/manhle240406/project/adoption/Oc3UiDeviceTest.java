package com.gmail.manhle240406.project.adoption;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static com.gmail.manhle240406.project.adoption.AdoptionFlowModel.map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.content.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.*;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.*;
import org.junit.runner.RunWith;

import java.util.*;

@RunWith(AndroidJUnit4.class)
public class Oc3UiDeviceTest {
    private static final String CURSOR = "10000000-0000-0000-0000-000000000090",
            APP = "10000000-0000-0000-0000-000000000091";
    private String uid;
    private final List<Map<String, Object>> reads = new ArrayList<>();

    @Before
    public void setup() {
        uid = FirebaseAuth.getInstance().getUid();
        assertNotNull(uid);
        AdoptionApi.testTransport =
                (op, input) -> {
                    if (op.equals("applications")) {
                        reads.add(new HashMap<>(input));
                        return Tasks.forResult(
                                map(
                                        "rows",
                                        Arrays.asList(
                                                map(
                                                        "applicationId",
                                                        APP,
                                                        "title",
                                                        input.get("role")
                                                                + " "
                                                                + (input.get("after") == null
                                                                        ? "first"
                                                                        : "second"),
                                                        "status",
                                                        "pending")),
                                        "next",
                                        input.get("after") == null ? CURSOR : null));
                    }
                    if (op.equals("application"))
                        return Tasks.forResult(
                                map(
                                        "application",
                                        map(
                                                "applicationId",
                                                APP,
                                                "status",
                                                "pending",
                                                "applicantUid",
                                                uid,
                                                "hostUid",
                                                "synthetic-host"),
                                        "submission",
                                        map(
                                                "snapshot",
                                                map("offer", map("title", "Synthetic request")))));
                    return Tasks.forResult(
                            map(
                                    "pets",
                                    Collections.emptyList(),
                                    "offers",
                                    Collections.emptyList(),
                                    "drafts",
                                    Collections.emptyList(),
                                    "reviewer",
                                    Collections.emptyList()));
                };
    }

    @After
    public void cleanup() {
        AdoptionApi.testTransport = null;
        assertEquals(uid, FirebaseAuth.getInstance().getUid());
    }

    private String canonicalPackage() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
    }

    private void waitText(String text) {
        assertTrue(
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                        .wait(Until.hasObject(By.text(text)), 15000));
    }

    @Test
    public void tabsKeepIndependentPaginationAndBackReturnsToSameRole() {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<AdoptionFlowActivity> scenario =
                ActivityScenario.launch(
                        new Intent(c, AdoptionFlowActivity.class).putExtra("mode", "management"))) {
            waitText("applicant first");
            onView(withTagValue(equalTo("adoption_next_page"))).perform(scrollTo(), click());
            waitText("applicant second");
            onView(withTagValue(equalTo("adoption_application_" + APP)))
                    .perform(scrollTo(), click());
            waitText("Synthetic request");
            androidx.test.espresso.Espresso.pressBack();
            waitText("applicant second");
            onView(
                            allOf(
                                    withText(R.string.oc3_rehoming),
                                    isDescendantOfA(withTagValue(equalTo("adoption_role_tabs")))))
                    .perform(scrollTo(), click());
            waitText("host first");
            onView(
                            allOf(
                                    withText(R.string.oc3_adopting),
                                    isDescendantOfA(withTagValue(equalTo("adoption_role_tabs")))))
                    .perform(scrollTo(), click());
            waitText("applicant second");
            assertEquals(CURSOR, reads.get(reads.size() - 1).get("after"));
            androidx.test.espresso.Espresso.pressBackUnconditionally();
            for (int n = 0;
                    n < 100 && scenario.getState() != androidx.lifecycle.Lifecycle.State.DESTROYED;
                    n++) android.os.SystemClock.sleep(50);
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.getState());
        }
    }

    @Test
    public void previewUsesFeedCardAndDoesNotPublish() {
        try (ActivityScenario<SocialComposerActivity> scenario =
                ActivityScenario.launch(SocialComposerActivity.class)) {
            onView(withId(R.id.social_body_input))
                    .perform(
                            scrollTo(),
                            replaceText("Synthetic OC3 preview only"),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            onView(withId(R.id.social_publish_action)).perform(scrollTo());
            assertTrue(
                    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                            .wait(
                                    Until.hasObject(
                                            By.res(canonicalPackage(), "social_publish_action")
                                                    .enabled(true)),
                                    30000));
            onView(withId(R.id.social_publish_action)).perform(scrollTo(), click());
            onView(withId(R.id.post_body)).check(matches(withText("Synthetic OC3 preview only")));
            onView(withId(R.id.post_author_name)).check(matches(isDisplayed()));
            onView(withText(android.R.string.cancel)).perform(click());
        }
    }

    @Test
    public void profileLogoutIsVisibleAndSettingsHasNoOutline() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> a.navigate("profile"));
            onView(withId(R.id.profile_logout)).check(matches(isDisplayed()));
            onView(withId(R.id.nav_home)).check(matches(isDisplayed()));
            onView(withId(R.id.profile_options))
                    .check(
                            (view, error) -> {
                                if (error != null) throw error;
                                assertEquals(
                                        0,
                                        ((com.google.android.material.button.MaterialButton) view)
                                                .getStrokeWidth());
                            });
        }
    }
}
