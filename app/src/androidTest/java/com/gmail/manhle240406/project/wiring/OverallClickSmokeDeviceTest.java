package com.gmail.manhle240406.project.wiring;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.firebase.auth.FirebaseAuth;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Read-only startup smoke for every Activity reached by a feature-grid click. */
@RunWith(AndroidJUnit4.class)
public final class OverallClickSmokeDeviceTest {
    @BeforeClass
    public static void signedIn() {
        assertNotNull(
                "Sign in before running the production click smoke",
                FirebaseAuth.getInstance().getCurrentUser());
    }

    private void group(int group) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String uid = FirebaseAuth.getInstance().getUid();
        for (FlowRoutes.Route route : FlowRoutes.ALL) {
            if (route.group != group || route.contextual || route.activity == null) continue;
            android.util.Log.i(
                    "ANILOVE_CLICK_SMOKE",
                    "Opening " + route.feature + " " + route.activity.getSimpleName());
            Intent intent = new Intent(context, route.activity);
            if (route.feature.equals("F01") || route.feature.equals("F42"))
                intent.putExtra("account", true);
            if (route.feature.equals("F13")) intent.putExtra("filters", true);
            try (ActivityScenario<Activity> ignored = ActivityScenario.launch(intent)) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                SystemClock.sleep(250);
                assertEquals(
                        "Startup changed the account at " + route.feature,
                        uid,
                        FirebaseAuth.getInstance().getUid());
            } catch (RuntimeException failure) {
                throw new AssertionError(
                        "Feature entry crashed at "
                                + route.feature
                                + " ("
                                + route.activity.getSimpleName()
                                + ")",
                        failure);
            }
        }
    }

    @Test
    public void discoveryEntriesOpen() {
        group(0);
    }

    @Test
    public void communityEntriesOpen() {
        group(1);
    }

    @Test
    public void adoptionEntriesOpen() {
        group(2);
    }

    @Test
    public void careEntriesOpen() {
        group(3);
    }

    @Test
    public void accountEntriesOpen() {
        group(4);
    }

    @Test
    public void operatorEntriesOpen() {
        group(5);
    }
}
