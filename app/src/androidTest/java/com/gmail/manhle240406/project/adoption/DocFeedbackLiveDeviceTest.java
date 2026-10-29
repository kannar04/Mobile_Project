package com.gmail.manhle240406.project.adoption;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static com.gmail.manhle240406.project.adoption.AdoptionFlowModel.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.content.Intent;
import android.os.*;
import android.widget.*;

import androidx.lifecycle.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.*;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import com.google.firebase.functions.*;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.concurrent.*;

/** Explicit disposable two-party live test. Never replaces the default Google identity. */
@RunWith(AndroidJUnit4.class)
public final class DocFeedbackLiveDeviceTest {
    private final List<String> operations = new ArrayList<>();
    private String owner,
            pet = UUID.randomUUID().toString(),
            post = UUID.randomUUID().toString(),
            application = UUID.randomUUID().toString(),
            reservation = UUID.randomUUID().toString();

    private Map<String, Object> call(String op, Map<String, Object> input) throws Exception {
        if (!Arrays.asList(
                        "pet",
                        "petWorkspace",
                        "publicDetail",
                        "offerSource",
                        "application",
                        "applications",
                        "receipt")
                .contains(op)) {
            String id = UUID.randomUUID().toString();
            input.put("operationId", id);
            operations.add(id);
        }
        return Tasks.await(AdoptionApi.call(op, input), 35, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> reader(FirebaseApp app, String op, Map<String, Object> input)
            throws Exception {
        HttpsCallableReference ref =
                FirebaseFunctions.getInstance(app, "asia-east2")
                        .getHttpsCallable("adoptionOperations");
        ref.setTimeout(30, TimeUnit.SECONDS);
        return (Map<String, Object>)
                Tasks.await(ref.call(map("operation", op, "input", input)), 35, TimeUnit.SECONDS)
                        .getData();
    }

    private void until(java.util.function.BooleanSupplier condition) throws Exception {
        long end = SystemClock.elapsedRealtime() + 35000;
        while (SystemClock.elapsedRealtime() < end) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(80);
        }
        fail("Timed out waiting for live operation");
    }

    @Test
    public void nativePostSecondaryApplicationOwnerReviewAndPrivateConversation() throws Exception {
        android.content.Context context =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        JSONObject fixture =
                new JSONObject(
                        new String(
                                java.nio.file.Files.readAllBytes(
                                        new java.io.File(
                                                        context.getExternalFilesDir(null),
                                                        "feedback-live-reader.json")
                                                .toPath()),
                                java.nio.charset.StandardCharsets.UTF_8));
        owner = FirebaseAuth.getInstance().getUid();
        assertEquals(fixture.getString("ownerUid"), owner);
        assertTrue(FirebaseAuth.getInstance().getCurrentUser().isEmailVerified());
        AdoptionApi.testTransport = null;
        OwnedPets.clear();
        FirebaseApp secondary =
                FirebaseApp.initializeApp(
                        context,
                        FirebaseApp.getInstance().getOptions(),
                        "feedback-live-" + UUID.randomUUID());
        try {
            Tasks.await(
                    FirebaseAuth.getInstance(secondary)
                            .signInWithEmailAndPassword(
                                    fixture.getString("email"), fixture.getString("password")),
                    30,
                    TimeUnit.SECONDS);
            call(
                    "registerPet",
                    map(
                            "petId",
                            pet,
                            "identity",
                            map(
                                    "displayName",
                                    "QA Doc Feedback — disposable",
                                    "species",
                                    "cat",
                                    "breed",
                                    "",
                                    "sex",
                                    "unknown",
                                    "ageMonths",
                                    null,
                                    "ageObservedAt",
                                    null,
                                    "marks",
                                    "",
                                    "marksUnknown",
                                    true,
                                    "illustrationKey",
                                    "cat"),
                            "claim",
                            map(
                                    "basis",
                                    "claimed_owner",
                                    "reportedCare",
                                    false,
                                    "explanation",
                                    "Disposable fictional test. No real animal."),
                            "mediaAssetIds",
                            new ArrayList<>()));
            call(
                    "claimDecision",
                    map(
                            "petId",
                            pet,
                            "ownerUid",
                            owner,
                            "expectedClaimVersion",
                            1,
                            "expectedIdentityVersion",
                            1,
                            "decision",
                            "approve",
                            "reason",
                            "Disposable course-test registration reviewed, no real ownership"
                                    + " assertion."));
            OwnedPets.clear();
            QuerySnapshot notifications =
                    Tasks.await(
                            FirebaseFirestore.getInstance()
                                    .collection("users/" + owner + "/notificationInbox")
                                    .whereEqualTo("targetId", pet)
                                    .limit(1)
                                    .get(Source.SERVER),
                            20,
                            TimeUnit.SECONDS);
            assertEquals(1, notifications.size());
            assertEquals(
                    "pet_approved", notifications.getDocuments().get(0).getString("templateKey"));
            try (ActivityScenario<SocialComposerActivity> scenario =
                    ActivityScenario.launch(SocialComposerActivity.class)) {
                final DailyViewModel[] model = {null};
                scenario.onActivity(
                        a -> {
                            a.getWindow()
                                    .addFlags(
                                            android.view.WindowManager.LayoutParams
                                                    .FLAG_KEEP_SCREEN_ON);
                            model[0] = new ViewModelProvider(a).get(DailyViewModel.class);
                        });
                until(() -> !model[0].busy);
                post = model[0].postId;
                onView(withId(R.id.social_body_input))
                        .perform(
                                scrollTo(),
                                replaceText(
                                        "QA Doc Feedback — disposable adoption post. No real"
                                                + " animal."),
                                androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
                onView(withId(R.id.post_type_picker)).perform(scrollTo(), click());
                onView(withId(R.id.adoption_listing_action)).perform(click());
                assertTrue(
                        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                                .wait(
                                        Until.hasObject(By.text("QA Doc Feedback — disposable")),
                                        30000));
                onView(withText("QA Doc Feedback — disposable")).perform(click());
                onView(withId(R.id.social_publish_action)).perform(scrollTo(), click());
                onView(withId(android.R.id.button1)).perform(click());
                until(() -> scenario.getState() == Lifecycle.State.DESTROYED);
            }
            DocumentSnapshot posted =
                    Tasks.await(
                            FirebaseFirestore.getInstance()
                                    .document("communityPosts/" + post)
                                    .get(Source.SERVER),
                            20,
                            TimeUnit.SECONDS);
            operations.add(posted.getString("lastOperationId"));
            assertEquals(Long.valueOf(6), posted.getLong("schemaVersion"));
            assertEquals(post, posted.getString("listingId"));
            assertEquals(owner, posted.getString("authorUid"));
            Map<String, Object> source = reader(secondary, "offerSource", map("listingId", post));
            assertNotNull(source.get("template"));
            reader(
                    secondary,
                    "placement",
                    map(
                            "command",
                            "submit",
                            "operationId",
                            UUID.randomUUID().toString(),
                            "expectedVersion",
                            0,
                            "applicationId",
                            application,
                            "listingId",
                            post,
                            "sourceHash",
                            source.get("sourceHash"),
                            "templateHash",
                            source.get("templateHash"),
                            "answers",
                            map(
                                    "care",
                                    "needs_discussion",
                                    "routine",
                                    "Synthetic daily care discussion"),
                            "message",
                            "Disposable feedback test"));
            Intent intent =
                    new Intent(context, AdoptionFlowActivity.class)
                            .putExtra("mode", "hostApplications")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try (ActivityScenario<AdoptionFlowActivity> scenario =
                    ActivityScenario.launch(intent)) {
                until(
                        () -> {
                            final boolean[] ready = {false};
                            scenario.onActivity(a -> ready[0] = !a.m.busy);
                            return ready[0];
                        });
                onView(withTagValue(equalTo("adoption_application_" + application)))
                        .perform(scrollTo(), click());
                until(
                        () -> {
                            final boolean[] ready = {false};
                            scenario.onActivity(
                                    a -> ready[0] = !a.m.busy && a.m.page.equals("application"));
                            return ready[0];
                        });
                onView(withTagValue(equalTo("adoption_select"))).perform(click());
                onView(
                                isAssignableFrom(
                                        com.google.android.material.textfield.TextInputEditText
                                                .class))
                        .perform(
                                replaceText("Synthetic participant selection"),
                                androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
                onView(withId(android.R.id.button1)).perform(click());
                until(
                        () -> {
                            final boolean[] ready = {false};
                            scenario.onActivity(
                                    a ->
                                            ready[0] =
                                                    !a.m.busy
                                                            && object(a.m.data.get("application"))
                                                                            .get(
                                                                                    "selectedReservationId")
                                                                    != null);
                            return ready[0];
                        });
                scenario.onActivity(
                        a -> {
                            reservation =
                                    String.valueOf(
                                            object(a.m.data.get("application"))
                                                    .get("selectedReservationId"));
                        });
            }
            DocumentSnapshot grant =
                    Tasks.await(
                            FirebaseFirestore.getInstance(secondary)
                                    .document("reservationCommunication/" + reservation)
                                    .get(Source.SERVER),
                            20,
                            TimeUnit.SECONDS);
            assertTrue(grant.getBoolean("textEnabled"));
            SessionGateway readerSession = new SessionGateway(FirebaseAuth.getInstance(secondary));
            try (ParticipantCommunicationRepository communication =
                    new ParticipantCommunicationRepository(
                            FirebaseFirestore.getInstance(secondary), readerSession)) {
                Tasks.await(
                        communication.send(
                                readerSession.capture(),
                                reservation,
                                UUID.randomUUID().toString(),
                                UUID.randomUUID().toString(),
                                0,
                                "Synthetic message from selected applicant"),
                        20,
                        TimeUnit.SECONDS);
            } finally {
                readerSession.close();
            }
            try (ActivityScenario<MainActivity> scenario =
                    ActivityScenario.launch(
                            new Intent(context, MainActivity.class)
                                    .putExtra(MainActivity.PAGE, "conversation:" + reservation)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                assertTrue(
                        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                                .wait(
                                        Until.hasObject(
                                                By.textContains(
                                                        "Synthetic message from selected"
                                                                + " applicant")),
                                        30000));
                onView(withTagValue(equalTo("chat_message_input")))
                        .perform(
                                replaceText("Synthetic owner reply"),
                                androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
                onView(withTagValue(equalTo("chat_send"))).perform(click());
                assertTrue(
                        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                                .wait(
                                        Until.hasObject(By.textContains("Synthetic owner reply")),
                                        30000));
            }
            assertEquals(
                    2,
                    Tasks.await(
                                    FirebaseFirestore.getInstance(secondary)
                                            .collection(
                                                    "conversations/" + reservation + "/messages")
                                            .limit(20)
                                            .get(Source.SERVER),
                                    20,
                                    TimeUnit.SECONDS)
                            .size());
            assertEquals(owner, FirebaseAuth.getInstance().getUid());
        } finally {
            JSONObject result = new JSONObject();
            result.put("ownerUid", owner);
            result.put("petId", pet);
            result.put("postId", post);
            result.put("applicationId", application);
            result.put("reservationId", reservation);
            result.put("operationIds", new JSONArray(operations));
            java.nio.file.Files.write(
                    new java.io.File(
                                    context.getExternalFilesDir(null), "feedback-live-results.json")
                            .toPath(),
                    result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            FirebaseAuth.getInstance(secondary).signOut();
            secondary.delete();
        }
    }
}
