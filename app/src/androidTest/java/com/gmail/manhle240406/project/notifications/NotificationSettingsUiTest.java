package com.gmail.manhle240406.project.notifications;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.gmail.manhle240406.project.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.anyOf;

@RunWith(AndroidJUnit4.class)
public final class NotificationSettingsUiTest {
 @Test public void statusAndAdvancedControlsAreVisible(){try(ActivityScenario<NotificationSettingsActivity> ignored=ActivityScenario.launch(NotificationSettingsActivity.class)){
  onView(withId(R.id.notification_enabled_switch)).check(matches(isDisplayed()));
  onView(anyOf(withText(R.string.notification_status_on),withText(R.string.notification_status_off))).check(matches(isDisplayed()));
  onView(withText(R.string.notification_advanced)).check(matches(isDisplayed()));
 }}
}
