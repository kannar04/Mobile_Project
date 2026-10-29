package com.gmail.manhle240406.project.adoption;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.gmail.manhle240406.project.adoption.AdoptionFlowModel.map;
import static com.gmail.manhle240406.project.adoption.AdoptionFlowModel.object;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.DailyViewModel;
import com.gmail.manhle240406.project.community.SocialComposerActivity;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Continues the real photo registration fixture into a visible adoption listing and social post.
 */
@RunWith(AndroidJUnit4.class)
public final class TwoDevicePetPostLiveTest {
    private Context context;
    private UiDevice device;

    private Map<String, Object> call(String operation, Map<String, Object> values)
            throws Exception {
        values.put("operationId", UUID.randomUUID().toString());
        return Tasks.await(AdoptionApi.call(operation, values), 35, TimeUnit.SECONDS);
    }

    private Map<String, Object> read(String operation, Map<String, Object> values)
            throws Exception {
        return Tasks.await(AdoptionApi.call(operation, values), 35, TimeUnit.SECONDS);
    }

    private void waitFor(AdoptionFlowActivity activity) {
        long deadline = SystemClock.uptimeMillis() + 35_000;
        while (activity.m.busy && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
        if (activity.m.busy) fail("Timed out waiting for adoption operation");
        assertEquals("", activity.m.error);
    }

    private <T extends AdoptionFlowActivity> void waitFor(ActivityScenario<T> scenario) {
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 35_000;
        do {
            scenario.onActivity(a -> ready.set(!a.m.busy));
            if (!ready.get()) SystemClock.sleep(100);
        } while (!ready.get() && SystemClock.uptimeMillis() < deadline);
        assertTrue("Timed out waiting for adoption screen", ready.get());
        scenario.onActivity(this::waitFor);
    }

    private void clickTag(String tag) {
        onView(withTagValue(equalTo(tag))).perform(scrollTo(), click());
    }

    private void approve(
            ActivityScenario<ListingReviewActivity> scenario, String tag, boolean listing) {
        clickTag(tag);
        waitFor(scenario);
        if (listing) {
            clickTag("adoption_assign");
            waitFor(scenario);
        }
        onView(withTagValue(equalTo("ad_review_reason")))
                .perform(
                        scrollTo(),
                        replaceText("Two-device disposable QA fixture."),
                        closeSoftKeyboard());
        clickTag("adoption_approve");
        onView(withText(R.string.ad_confirm_demo_approve)).perform(click());
        waitFor(scenario);
    }

    @Test
    public void registeredPhotoPetBecomesVisibleAdoptionPost() throws Exception {
        assumeTrue(
                "true"
                        .equals(
                                InstrumentationRegistry.getArguments()
                                        .getString("runTwoDevicePetPost")));
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String uid = FirebaseAuth.getInstance().getUid();
        assertNotNull(uid);

        File registration = new File(context.getExternalFilesDir(null), "oc3-pet-results.json");
        assertTrue("Run Oc3PetRegistrationLiveTest first", registration.exists());
        JSONObject registered =
                new JSONObject(
                        new String(
                                Files.readAllBytes(registration.toPath()), StandardCharsets.UTF_8));
        assertEquals(uid, registered.getString("uid"));
        String petId = registered.getString("petId");
        String petName = registered.getString("petName");
        String suffix = uid.substring(0, Math.min(6, uid.length()));
        String foundListing = null;
        for (Map<String, Object> row :
                AdoptionFlowModel.rows(
                        read("workspace", map("petsAfter", null, "offersAfter", null))
                                .get("offers")))
            if (petId.equals(row.get("petId")) && "open".equals(row.get("lifecycle")))
                foundListing = String.valueOf(row.get("listingId"));
        final String listingId = foundListing == null ? UUID.randomUUID().toString() : foundListing;
        Map<String, Object> readiness = read("flowRead", map("kind", "readiness"));
        if (foundListing == null)
            call(
                    "publishCourse",
                    map(
                            "listingId",
                            listingId,
                            "petId",
                            petId,
                            "expectedContentVersion",
                            0,
                            "expectedControlVersion",
                            0,
                            "fields",
                            map(
                                    "title",
                                    "Bé cần mái ấm QA " + suffix,
                                    "description",
                                    "Bài kiểm thử hai thiết bị; ảnh và hồ sơ được tạo qua ứng"
                                            + " dụng.",
                                    "regionCode",
                                    null,
                                    "offerType",
                                    "adoption",
                                    "mediaRefs",
                                    new ArrayList<>()),
                            "requirements",
                            map(
                                    "description",
                                    "Hồ sơ nhận nuôi QA",
                                    "temperament",
                                    "Thân thiện",
                                    "careRequirements",
                                    "Chăm sóc hằng ngày",
                                    "adoptionRequirements",
                                    "Nơi ở an toàn"),
                            "consent",
                            true,
                            "participationConsent",
                            map(
                                    "termsVersion",
                                    readiness.get("termsVersion"),
                                    "termsHash",
                                    readiness.get("termsHash"))));

        final String[] postId = {null};
        try (ActivityScenario<SocialComposerActivity> composer =
                ActivityScenario.launch(
                        new Intent(context, SocialComposerActivity.class)
                                .putExtra("adoption", true))) {
            composer.onActivity(
                    a -> postId[0] = new ViewModelProvider(a).get(DailyViewModel.class).postId);
            onView(withId(R.id.adoption_listing_action)).perform(click());
            assertTrue(device.wait(Until.hasObject(By.textContains(petName)), 35_000));
            onView(withText(org.hamcrest.Matchers.startsWith(petName)))
                    .inRoot(isDialog())
                    .perform(click());
            composer.onActivity(
                    a -> {
                        Map<String, Object> selected =
                                new ViewModelProvider(a).get(DailyViewModel.class).selectedListing;
                        assertNotNull(selected);
                        assertEquals(petId, selected.get("petId"));
                        assertEquals(listingId, selected.get("listingId"));
                        assertTrue(selected.get("listingContentVersion") instanceof Number);
                        assertTrue(selected.get("listingPublicationVersion") instanceof Number);
                    });
            onView(withId(R.id.social_body_input))
                    .perform(
                            scrollTo(),
                            replaceText("Bài đăng nhận nuôi QA từ thiết bị " + suffix),
                            closeSoftKeyboard());
            onView(withId(R.id.social_publish_action)).perform(scrollTo(), click());
            if (device.wait(
                    Until.hasObject(
                            By.text(context.getString(R.string.permissions_agree_continue))),
                    2_000))
                onView(withText(R.string.permissions_agree_continue))
                        .inRoot(isDialog())
                        .perform(click());
            assertTrue(
                    device.wait(
                            Until.hasObject(By.text(context.getString(R.string.social_publish))),
                            15_000));
            onView(withText(R.string.social_publish)).inRoot(isDialog()).perform(click());
            long deadline = SystemClock.uptimeMillis() + 35_000;
            while (composer.getState() != androidx.lifecycle.Lifecycle.State.DESTROYED
                    && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
            AtomicBoolean committed = new AtomicBoolean();
            java.util.concurrent.atomic.AtomicReference<String> state =
                    new java.util.concurrent.atomic.AtomicReference<>("");
            java.util.concurrent.atomic.AtomicReference<DailyViewModel> live =
                    new java.util.concurrent.atomic.AtomicReference<>();
            if (composer.getState() != androidx.lifecycle.Lifecycle.State.DESTROYED)
                composer.onActivity(
                        a -> {
                            DailyViewModel m = new ViewModelProvider(a).get(DailyViewModel.class);
                            live.set(m);
                            committed.set(m.committed);
                            state.set(
                                    m.notice
                                            + "; busy="
                                            + m.busy
                                            + "; pending="
                                            + (m.pending != null));
                        });
            if (live.get() != null && live.get().pending != null)
                try {
                    Tasks.await(
                            live.get().social.execute(live.get().ticket, live.get().pending),
                            35,
                            TimeUnit.SECONDS);
                } catch (Exception failed) {
                    Throwable cause = failed;
                    while (cause.getCause() != null) cause = cause.getCause();
                    state.set(
                            state.get()
                                    + "; cause="
                                    + cause.getClass().getSimpleName()
                                    + ":"
                                    + cause.getMessage());
                }
            assertEquals(
                    "Composer must close after commit; " + state.get(),
                    androidx.lifecycle.Lifecycle.State.DESTROYED,
                    composer.getState());
        }

        DocumentSnapshot post =
                Tasks.await(
                        FirebaseFirestore.getInstance()
                                .document("communityFeed/" + postId[0])
                                .get(),
                        25,
                        TimeUnit.SECONDS);
        assertTrue(post.exists());
        assertEquals(uid, post.getString("authorUid"));
        assertEquals("adoption", post.getString("type"));
        assertEquals(listingId, post.getString("listingId"));
        Map<String, Object> offer = read("offer", map("listingId", listingId));
        assertEquals("shown", object(offer.get("listing")).get("visibility"));
        JSONObject result =
                new JSONObject()
                        .put("passed", true)
                        .put("uid", uid)
                        .put("petId", petId)
                        .put("listingId", listingId)
                        .put("postId", postId[0]);
        Files.write(
                new File(context.getExternalFilesDir(null), "two-device-pet-post-result.json")
                        .toPath(),
                result.toString().getBytes(StandardCharsets.UTF_8));
    }
}
