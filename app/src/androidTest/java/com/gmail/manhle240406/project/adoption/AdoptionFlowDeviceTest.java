package com.gmail.manhle240406.project.adoption;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static com.gmail.manhle240406.project.adoption.AdoptionFlowModel.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.os.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gmail.manhle240406.project.R;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.*;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.concurrent.atomic.*;

@RunWith(AndroidJUnit4.class)
public final class AdoptionFlowDeviceTest {
    private static final String PET = "10000000-0000-0000-0000-000000000001",
            OFFER = "10000000-0000-0000-0000-000000000002";
    private String uid;
    private final Map<String, Integer> calls = new HashMap<>();
    private Map<String, Object> savedCare;
    private final Map<String, Object> root =
            map("petId", PET, "identityVersion", 1, "custodyVersion", 0);
    private Map<String, Object> fields;

    @Before
    public void setup() {
        uid = FirebaseAuth.getInstance().getUid();
        assertNotNull(uid);
        calls.clear();
        fields =
                map(
                        "title",
                        "Synthetic offer for native test",
                        "description",
                        "Fixture only",
                        "regionCode",
                        null,
                        "offerType",
                        "adoption",
                        "mediaRefs",
                        new ArrayList<>());
        AdoptionApi.testTransport =
                (op, input) -> {
                    calls.put(op, calls.getOrDefault(op, 0) + 1);
                    switch (op) {
                        case "workspace":
                            return Tasks.forResult(
                                    map(
                                            "pets",
                                            Arrays.asList(
                                                    map(
                                                            "petId",
                                                            PET,
                                                            "identity",
                                                            map("displayName", "Synthetic cat"))),
                                            "offers",
                                            new ArrayList<>(),
                                            "drafts",
                                            new ArrayList<>(),
                                            "reviewer",
                                            new ArrayList<>()));
                        case "pet":
                            return Tasks.forResult(
                                    map(
                                            "pet",
                                            root,
                                            "identity",
                                            map("displayName", "Synthetic cat"),
                                            "claim",
                                            map("reviewState", "accepted_for_demo"),
                                            "care",
                                            null,
                                            "revision",
                                            null,
                                            "privateCare",
                                            null));
                        case "care":
                            savedCare = new HashMap<>(input);
                            return Tasks.forResult(
                                    map(
                                            "careVersion",
                                            1,
                                            "careDigest",
                                            String.join("", Collections.nCopies(64, "a"))));
                        case "saveOffer":
                            return Tasks.forResult(map("contentVersion", 1, "listingId", OFFER));
                        case "submitReview":
                            return Tasks.forResult(map("status", "queued"));
                        case "offer":
                            return Tasks.forResult(
                                    map(
                                            "listing",
                                            map(
                                                    "listingId",
                                                    OFFER,
                                                    "petId",
                                                    PET,
                                                    "schemaVersion",
                                                    2,
                                                    "contentVersion",
                                                    1,
                                                    "controlVersion",
                                                    1,
                                                    "lifecycle",
                                                    "draft",
                                                    "visibility",
                                                    "hidden"),
                                            "revision",
                                            map("editableFields", fields),
                                            "review",
                                            map("state", "queued"),
                                            "publication",
                                            null));
                        case "applications":
                            return Tasks.forResult(map("rows", new ArrayList<>()));
                        default:
                            return Tasks.forResult(map());
                    }
                };
    }

    @After
    public void cleanup() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () ->
                                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                                        androidx.appcompat.app.AppCompatDelegate
                                                .MODE_NIGHT_FOLLOW_SYSTEM));
        AdoptionApi.testTransport = null;
        assertEquals(uid, FirebaseAuth.getInstance().getUid());
    }

    private void clickTag(String tag) {
        onView(withTagValue(equalTo(tag))).perform(click());
    }

    private void care() {
        clickTag("adoption_new");
        clickTag("adoption_pet_" + PET);
    }

    @Test
    public void ownerCarePreviewSubmitUsesOneWindowAndNeverAutoShows() {
        try (ActivityScenario<com.gmail.manhle240406.project.listing.ListingActivity> s =
                ActivityScenario.launch(
                        com.gmail.manhle240406.project.listing.ListingActivity.class)) {
            care();
            onView(withTagValue(equalTo("ad_medication")))
                    .perform(
                            scrollTo(),
                            replaceText("synthetic private instruction"),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            clickTag("adoption_save_care");
            onView(withTagValue(equalTo("ad_offer_title")))
                    .perform(
                            replaceText("A synthetic adoption offer"),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            clickTag("adoption_preview");
            onView(withText("synthetic private instruction")).check(doesNotExist());
            clickTag("adoption_submit");
            onView(withText(R.string.ad_pending)).check(matches(isDisplayed()));
            assertEquals(1, (int) calls.get("care"));
            assertEquals("unknown", object(savedCare.get("payload")).get("healthState"));
            assertEquals("synthetic private instruction", savedCare.get("medicationNotes"));
            assertFalse(calls.containsKey("show"));
            capture("listing-success");
        }
    }

    @Test
    public void rotationRetainsPrivateCareInMemoryAndBackReusesWorkspace() {
        try (ActivityScenario<com.gmail.manhle240406.project.listing.ListingActivity> s =
                ActivityScenario.launch(
                        com.gmail.manhle240406.project.listing.ListingActivity.class)) {
            care();
            onView(withTagValue(equalTo("ad_medication")))
                    .perform(
                            scrollTo(),
                            replaceText("synthetic rotation note"),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            s.recreate();
            onView(withTagValue(equalTo("ad_medication")))
                    .check(matches(withText("synthetic rotation note")));
            androidx.test.espresso.Espresso.pressBack();
            androidx.test.espresso.Espresso.pressBack();
            onView(withText(R.string.ad_continue)).perform(click());
            assertEquals(1, (int) calls.get("workspace"));
        }
    }

    @Test
    public void ordinaryWorkspaceDoesNotExposeReviewerShortcut() {
        try (ActivityScenario<com.gmail.manhle240406.project.listing.ListingActivity> s =
                ActivityScenario.launch(
                        com.gmail.manhle240406.project.listing.ListingActivity.class)) {
            onView(withTagValue(equalTo("adoption_review"))).check(doesNotExist());
            clickTag("adoption_my_applications");
            onView(withText(R.string.ad_no_applications)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void nativeCareSurfaceFitsPhoneVariants() throws Exception {
        String variant = InstrumentationRegistry.getArguments().getString("variant", "portrait");
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () ->
                                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                                        variant.equals("dark")
                                                ? androidx.appcompat.app.AppCompatDelegate
                                                        .MODE_NIGHT_YES
                                                : androidx.appcompat.app.AppCompatDelegate
                                                        .MODE_NIGHT_NO));
        try (ActivityScenario<com.gmail.manhle240406.project.listing.ListingActivity> s =
                ActivityScenario.launch(
                        com.gmail.manhle240406.project.listing.ListingActivity.class)) {
            s.onActivity(
                    a -> {
                        a.setRequestedOrientation(
                                variant.equals("landscape")
                                        ? android.content.pm.ActivityInfo
                                                .SCREEN_ORIENTATION_LANDSCAPE
                                        : android.content.pm.ActivityInfo
                                                .SCREEN_ORIENTATION_PORTRAIT);
                    });
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .waitForIdleSync();
            care();
            s.onActivity(
                    a -> {
                        ArrayList<android.view.View> buttons = new ArrayList<>();
                        a.getWindow()
                                .getDecorView()
                                .findViewsWithText(
                                        buttons,
                                        a.getString(R.string.ad_save_continue),
                                        android.view.View.FIND_VIEWS_WITH_TEXT);
                        assertEquals(1, buttons.size());
                        android.graphics.Rect bounds = new android.graphics.Rect();
                        assertTrue(buttons.get(0).getGlobalVisibleRect(bounds));
                        assertTrue(
                                bounds.height()
                                        >= Math.round(
                                                48 * a.getResources().getDisplayMetrics().density));
                        assertTrue(a.getWindow().getDecorView().getHeight() > 0);
                    });
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .waitForIdleSync();
            android.graphics.Bitmap bitmap =
                    InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            assertNotNull(bitmap);
            java.io.File dir =
                    new java.io.File(
                            InstrumentationRegistry.getInstrumentation()
                                    .getTargetContext()
                                    .getExternalFilesDir(null),
                            "adoption-ui-audit");
            assertTrue(dir.mkdirs() || dir.isDirectory());
            try (java.io.FileOutputStream out =
                    new java.io.FileOutputStream(new java.io.File(dir, variant + ".png"))) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            }
            bitmap.recycle();
        }
    }

    @Test
    public void declarationSubmissionShowsReceiptAndPreservesPrivateDraft() {
        java.util.function.BiFunction<
                        String,
                        Map<String, Object>,
                        com.google.android.gms.tasks.Task<Map<String, Object>>>
                original = AdoptionApi.testTransport;
        AtomicBoolean submitted = new AtomicBoolean();
        AdoptionApi.testTransport =
                (op, input) -> {
                    if (op.equals("pet"))
                        return Tasks.forResult(
                                map(
                                        "pet",
                                        root,
                                        "identity",
                                        map("displayName", "Synthetic cat"),
                                        "claim",
                                        map(
                                                "reviewState",
                                                submitted.get() ? "submitted" : "draft",
                                                "claimVersion",
                                                submitted.get() ? 2 : 1)));
                    if (op.equals("submitClaim")) {
                        submitted.set(true);
                        return Tasks.forResult(map("status", "submitted"));
                    }
                    return original.apply(op, input);
                };
        try (ActivityScenario<com.gmail.manhle240406.project.listing.ListingActivity> scenario =
                ActivityScenario.launch(
                        com.gmail.manhle240406.project.listing.ListingActivity.class)) {
            care();
            onView(withTagValue(equalTo("ad_medication")))
                    .perform(
                            scrollTo(),
                            replaceText("unsent private draft"),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            onView(withTagValue(equalTo("adoption_submit_claim"))).perform(scrollTo(), click());
            onView(withTagValue(equalTo("adoption_feedback")))
                    .check(
                            matches(
                                    allOf(
                                            isDisplayed(),
                                            withText(R.string.ad_claim_sent_feedback))));
            onView(withTagValue(equalTo("ad_medication")))
                    .check(matches(withText("unsent private draft")));
            onView(withTagValue(equalTo("adoption_submit_claim"))).check(doesNotExist());
            scenario.recreate();
            onView(withTagValue(equalTo("adoption_feedback"))).check(matches(isDisplayed()));
            assertTrue(submitted.get());
            assertFalse(calls.containsKey("saveOffer"));
            capture("claim-success");
        }
    }

    @Test
    public void pendingDeclarationCannotPretendToSubmitListing() {
        java.util.function.BiFunction<
                        String,
                        Map<String, Object>,
                        com.google.android.gms.tasks.Task<Map<String, Object>>>
                original = AdoptionApi.testTransport;
        AdoptionApi.testTransport =
                (op, input) ->
                        op.equals("pet")
                                ? Tasks.forResult(
                                        map(
                                                "pet",
                                                root,
                                                "identity",
                                                map("displayName", "Synthetic cat"),
                                                "claim",
                                                map("reviewState", "submitted", "claimVersion", 2)))
                                : original.apply(op, input);
        try (ActivityScenario<com.gmail.manhle240406.project.listing.ListingActivity> scenario =
                ActivityScenario.launch(
                        com.gmail.manhle240406.project.listing.ListingActivity.class)) {
            care();
            clickTag("adoption_save_care");
            onView(withTagValue(equalTo("ad_offer_title")))
                    .perform(
                            replaceText("An unsent synthetic offer"),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            clickTag("adoption_preview");
            onView(withTagValue(equalTo("adoption_submit"))).check(doesNotExist());
            onView(withTagValue(equalTo("adoption_check_claim"))).check(matches(isDisplayed()));
            assertFalse(calls.containsKey("saveOffer"));
        }
    }

    @Test
    public void submissionFailureStaysVisibleAtPrimaryAction() {
        java.util.function.BiFunction<
                        String,
                        Map<String, Object>,
                        com.google.android.gms.tasks.Task<Map<String, Object>>>
                original = AdoptionApi.testTransport;
        AdoptionApi.testTransport =
                (op, input) ->
                        op.equals("saveOffer")
                                ? Tasks.forException(
                                        new com.google.firebase.functions
                                                .FirebaseFunctionsException(
                                                "authority_pending",
                                                com.google.firebase.functions
                                                        .FirebaseFunctionsException.Code
                                                        .FAILED_PRECONDITION,
                                                null))
                                : original.apply(op, input);
        try (ActivityScenario<com.gmail.manhle240406.project.listing.ListingActivity> scenario =
                ActivityScenario.launch(
                        com.gmail.manhle240406.project.listing.ListingActivity.class)) {
            care();
            clickTag("adoption_save_care");
            onView(withTagValue(equalTo("ad_offer_title")))
                    .perform(
                            replaceText("An unsent synthetic offer"),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            clickTag("adoption_preview");
            clickTag("adoption_submit");
            onView(withTagValue(equalTo("adoption_feedback")))
                    .check(matches(allOf(isDisplayed(), withText(R.string.ad_authority_pending))));
            onView(withTagValue(equalTo("adoption_check_claim"))).check(matches(isEnabled()));
            onView(withTagValue(equalTo("adoption_submit"))).check(doesNotExist());
            capture("submit-failure");
        }
    }

    @Test
    public void savedOfferWithFailedReviewHasExplicitRetryInsteadOfRepeatingSave() {
        java.util.function.BiFunction<
                        String,
                        Map<String, Object>,
                        com.google.android.gms.tasks.Task<Map<String, Object>>>
                original = AdoptionApi.testTransport;
        AdoptionApi.testTransport =
                (op, input) -> {
                    if (op.equals("submitReview"))
                        return Tasks.forException(
                                new com.google.firebase.functions.FirebaseFunctionsException(
                                        "conflict",
                                        com.google.firebase.functions.FirebaseFunctionsException
                                                .Code.FAILED_PRECONDITION,
                                        null));
                    if (op.equals("offer"))
                        return Tasks.forResult(
                                map(
                                        "listing",
                                        map(
                                                "listingId",
                                                OFFER,
                                                "petId",
                                                PET,
                                                "schemaVersion",
                                                2,
                                                "contentVersion",
                                                1,
                                                "controlVersion",
                                                1,
                                                "lifecycle",
                                                "draft",
                                                "visibility",
                                                "hidden"),
                                        "revision",
                                        map("editableFields", fields),
                                        "review",
                                        null,
                                        "publication",
                                        null));
                    return original.apply(op, input);
                };
        try (ActivityScenario<com.gmail.manhle240406.project.listing.ListingActivity> scenario =
                ActivityScenario.launch(
                        com.gmail.manhle240406.project.listing.ListingActivity.class)) {
            care();
            clickTag("adoption_save_care");
            onView(withTagValue(equalTo("ad_offer_title")))
                    .perform(
                            replaceText("A saved synthetic offer"),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            clickTag("adoption_preview");
            clickTag("adoption_submit");
            onView(withTagValue(equalTo("adoption_feedback")))
                    .check(
                            matches(
                                    allOf(
                                            isDisplayed(),
                                            withText(
                                                    containsString(
                                                            InstrumentationRegistry
                                                                    .getInstrumentation()
                                                                    .getTargetContext()
                                                                    .getString(
                                                                            R.string
                                                                                    .ad_saved_not_sent))))));
            onView(withTagValue(equalTo("adoption_submit_existing"))).check(matches(isEnabled()));
            assertEquals(1, (int) calls.get("saveOffer"));
            capture("saved-not-sent");
        }
    }

    @Test
    public void adminCategoryExposesOneScopedReviewQueue() {
        try (ActivityScenario<com.gmail.manhle240406.project.MainActivity> scenario =
                ActivityScenario.launch(com.gmail.manhle240406.project.MainActivity.class)) {
            scenario.onActivity(a -> a.navigate("tools:5"));
            onView(withTagValue(equalTo("adoption_admin_review"))).check(doesNotExist());
            onView(withTagValue(equalTo("flow_F10")))
                    .check(matches(isDisplayed()))
                    .perform(scrollTo(), click());
            onView(withText(R.string.ad_queue_self_note)).check(matches(isDisplayed()));
            capture("admin-queue");
        }
    }

    private void capture(String name) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(250, 3000);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError(e);
        }
        android.graphics.Bitmap bitmap =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        java.io.File dir =
                new java.io.File(
                        InstrumentationRegistry.getInstrumentation()
                                .getTargetContext()
                                .getExternalFilesDir(null),
                        "adoption-submit-audit");
        assertTrue(dir.mkdirs() || dir.isDirectory());
        try (java.io.FileOutputStream out =
                new java.io.FileOutputStream(new java.io.File(dir, name + ".png"))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        } finally {
            bitmap.recycle();
        }
    }

    @Test
    public void timeoutShowsUnknownOutcomeAndReceiptRecoveryWithoutFalseSuccess() {
        java.util.function.BiFunction<
                        String,
                        Map<String, Object>,
                        com.google.android.gms.tasks.Task<Map<String, Object>>>
                original = AdoptionApi.testTransport;
        AdoptionApi.testTransport =
                (op, input) ->
                        op.equals("saveOffer")
                                ? Tasks.forException(
                                        new com.google.firebase.functions
                                                .FirebaseFunctionsException(
                                                "unavailable",
                                                com.google.firebase.functions
                                                        .FirebaseFunctionsException.Code
                                                        .UNAVAILABLE,
                                                null))
                                : original.apply(op, input);
        try (ActivityScenario<com.gmail.manhle240406.project.listing.ListingActivity> scenario =
                ActivityScenario.launch(
                        com.gmail.manhle240406.project.listing.ListingActivity.class)) {
            care();
            clickTag("adoption_save_care");
            onView(withTagValue(equalTo("ad_offer_title")))
                    .perform(
                            replaceText("An uncertain synthetic offer"),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            clickTag("adoption_preview");
            clickTag("adoption_submit");
            onView(withTagValue(equalTo("adoption_feedback")))
                    .check(matches(allOf(isDisplayed(), withText(R.string.ad_unknown_outcome))));
            onView(withText(R.string.ad_check_result)).check(matches(isDisplayed()));
            onView(withText(R.string.ad_listing_sent_feedback)).check(doesNotExist());
            capture("unknown-outcome");
        }
    }

    @Test
    public void currentQueuedReviewWinsOverDuplicateRequestConflict() {
        java.util.function.BiFunction<
                        String,
                        Map<String, Object>,
                        com.google.android.gms.tasks.Task<Map<String, Object>>>
                original = AdoptionApi.testTransport;
        AdoptionApi.testTransport =
                (op, input) ->
                        op.equals("submitReview")
                                ? Tasks.forException(
                                        new com.google.firebase.functions
                                                .FirebaseFunctionsException(
                                                "conflict",
                                                com.google.firebase.functions
                                                        .FirebaseFunctionsException.Code
                                                        .FAILED_PRECONDITION,
                                                null))
                                : original.apply(op, input);
        try (ActivityScenario<com.gmail.manhle240406.project.listing.ListingActivity> scenario =
                ActivityScenario.launch(
                        com.gmail.manhle240406.project.listing.ListingActivity.class)) {
            care();
            clickTag("adoption_save_care");
            onView(withTagValue(equalTo("ad_offer_title")))
                    .perform(
                            replaceText("An already queued synthetic offer"),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            clickTag("adoption_preview");
            clickTag("adoption_submit");
            onView(withTagValue(equalTo("adoption_feedback")))
                    .check(
                            matches(
                                    allOf(
                                            isDisplayed(),
                                            withText(R.string.ad_listing_sent_feedback))));
            onView(withTagValue(equalTo("adoption_submit_existing"))).check(doesNotExist());
        }
    }

    @Test
    public void ownDemoReviewRequiresConfirmationAndRetainsReasonWhenCancelled() {
        java.util.function.BiFunction<
                        String,
                        Map<String, Object>,
                        com.google.android.gms.tasks.Task<Map<String, Object>>>
                original = AdoptionApi.testTransport;
        AtomicInteger decisions = new AtomicInteger();
        AdoptionApi.testTransport =
                (op, input) -> {
                    if (op.equals("reviewQueue"))
                        return Tasks.forResult(
                                map(
                                        "claims",
                                        decisions.get() == 0
                                                ? Arrays.asList(
                                                        map(
                                                                "petId",
                                                                PET,
                                                                "claimantUid",
                                                                uid,
                                                                "displayName",
                                                                "Synthetic cat"))
                                                : new ArrayList<>(),
                                        "offers",
                                        new ArrayList<>(),
                                        "selfReviewAllowed",
                                        true));
                    if (op.equals("claimCandidate"))
                        return Tasks.forResult(
                                map(
                                        "pet",
                                        root,
                                        "claim",
                                        map(
                                                "claimantUid",
                                                uid,
                                                "claimVersion",
                                                2,
                                                "basis",
                                                "claimed_owner",
                                                "reportedCare",
                                                false,
                                                "explanation",
                                                "Synthetic declaration"),
                                        "identity",
                                        map("displayName", "Synthetic cat"),
                                        "reviewMode",
                                        "demo_self_review"));
                    if (op.equals("claimDecision")) {
                        decisions.incrementAndGet();
                        return Tasks.forResult(map("status", "accepted_for_demo"));
                    }
                    return original.apply(op, input);
                };
        try (ActivityScenario<ListingReviewActivity> scenario =
                ActivityScenario.launch(ListingReviewActivity.class)) {
            onView(withTagValue(equalTo("adoption_review_claim_" + PET)))
                    .perform(scrollTo(), click());
            onView(withTagValue(equalTo("ad_review_reason")))
                    .perform(
                            scrollTo(),
                            replaceText("Explicit synthetic own review"),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            clickTag("adoption_approve");
            onView(withText(R.string.ad_demo_review_confirmation)).check(matches(isDisplayed()));
            capture("demo-confirmation");
            assertEquals(0, decisions.get());
            onView(withText(android.R.string.cancel)).perform(click());
            onView(withTagValue(equalTo("ad_review_reason")))
                    .check(matches(withText("Explicit synthetic own review")));
            clickTag("adoption_approve");
            onView(withText(R.string.ad_confirm_demo_approve)).perform(click());
            assertEquals(1, decisions.get());
        }
    }

    @Test
    public void demoReaderDetailDisclosesSelfReviewBeforeApply() {
        AdoptionApi.testTransport =
                (op, input) ->
                        Tasks.forResult(
                                map(
                                        "detail",
                                        map(
                                                "identitySnapshot",
                                                map("displayName", "Synthetic cat"),
                                                "title",
                                                "Synthetic adoption offer",
                                                "description",
                                                "Synthetic record",
                                                "careSnapshot",
                                                map(
                                                        "healthState",
                                                        "unknown",
                                                        "sterilization",
                                                        "unknown"),
                                                "availability",
                                                "available"),
                                        "isOwner",
                                        false,
                                        "reviewMode",
                                        "demo_self_review"));
        android.content.Intent intent =
                new android.content.Intent(
                                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                                AdoptionFlowActivity.class)
                        .putExtra("mode", "detail")
                        .putExtra("listing", OFFER);
        try (ActivityScenario<AdoptionFlowActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withText(R.string.ad_demo_reader_notice))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withTagValue(equalTo("adoption_apply"))).check(matches(isDisplayed()));
            capture("demo-reader");
        }
    }
}
