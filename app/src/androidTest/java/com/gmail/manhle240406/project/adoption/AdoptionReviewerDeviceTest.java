package com.gmail.manhle240406.project.adoption;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.firebase.auth.FirebaseAuth;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicitly selected disposable fixture only; retains the device's signed-in reviewer. */
@RunWith(AndroidJUnit4.class)
public final class AdoptionReviewerDeviceTest {
    private void clickTag(String tag) {
        onView(withTagValue(equalTo(tag))).perform(scrollTo(), click());
    }

    private void idle(ActivityScenario<AdoptionFlowActivity> scenario) {
        long until = SystemClock.uptimeMillis() + 25000;
        AtomicBoolean ready = new AtomicBoolean();
        do {
            scenario.onActivity(a -> ready.set(!a.m.busy));
            if (ready.get()) return;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < until);
        fail("Managed adoption response timed out");
    }

    @Test
    public void designatedReviewerCanReviewOnlyTheSelectedSyntheticFixture() throws Exception {
        var args = InstrumentationRegistry.getArguments();
        String pet = args.getString("petId"),
                listing = args.getString("listingId"),
                hash = args.getString("reviewerHash"),
                phase = args.getString("phase");
        org.junit.Assume.assumeTrue(
                pet != null && listing != null && hash != null && phase != null);
        assertNotNull(FirebaseAuth.getInstance().getCurrentUser());
        String uid = FirebaseAuth.getInstance().getUid();
        byte[] digest =
                MessageDigest.getInstance("SHA-256").digest(uid.getBytes(StandardCharsets.UTF_8));
        StringBuilder actual = new StringBuilder();
        for (byte b : digest) actual.append(String.format("%02x", b));
        assertEquals(hash, actual.toString());
        Intent intent =
                new Intent(
                                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                                AdoptionFlowActivity.class)
                        .putExtra("mode", "review");
        try (ActivityScenario<AdoptionFlowActivity> scenario = ActivityScenario.launch(intent)) {
            idle(scenario);
            clickTag(
                    "claim".equals(phase)
                            ? "adoption_review_claim_" + pet
                            : "adoption_review_offer_" + listing);
            idle(scenario);
            if (!"claim".equals(phase)) {
                onView(withTagValue(equalTo("adoption_assign"))).perform(click());
                idle(scenario);
            }
            onView(withTagValue(equalTo("ad_review_reason")))
                    .perform(
                            scrollTo(),
                            replaceText("Synthetic isolated QA fixture reviewed; no real animal."),
                            closeSoftKeyboard());
            onView(withTagValue(equalTo("adoption_approve"))).perform(click());
            idle(scenario);
            scenario.onActivity(
                    a -> {
                        assertEquals("", a.m.error);
                        assertEquals("reviewQueue", a.m.page);
                    });
        }
        assertEquals(uid, FirebaseAuth.getInstance().getUid());
    }
}
