package com.gmail.manhle240406.project.profile;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static org.junit.Assert.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;

import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Oc2ProfileDeviceTest {
    @Test
    public void cancelledEditorWarnsBeforeDiscardAndRoleTabsAreDistinct() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> a.navigate("profile"));
            onView(withId(R.id.profile_edit)).perform(click());
            onView(withId(R.id.profile_name))
                    .perform(
                            scrollTo(),
                            replaceText("QA discarded edit"),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            onView(withText(R.string.oc2_cancel)).perform(click());
            assertTrue(
                    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                            .wait(
                                    Until.hasObject(
                                            By.text(
                                                    InstrumentationRegistry.getInstrumentation()
                                                            .getTargetContext()
                                                            .getString(R.string.oc2_unsaved))),
                                    10000));
            onView(withText(R.string.feedback_continue_editing)).perform(click());
            onView(withId(R.id.profile_name)).check(matches(withText("QA discarded edit")));
            onView(withText(R.string.oc2_cancel)).perform(click());
            onView(withText(R.string.feedback_discard)).perform(click());
            onView(withId(R.id.profile_edit)).check(matches(isDisplayed()));
            onView(withId(R.id.profile_review)).perform(scrollTo(), click());
            onView(withText(R.string.oc2_received)).check(matches(isDisplayed()));
            onView(withText(R.string.oc2_sent)).check(matches(isDisplayed()));
        }
    }
}
