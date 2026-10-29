package com.gmail.manhle240406.project.community;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.*;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.concurrent.atomic.*;

/** Preserves identity/profile. One scoped reaction on a disposable post is removed in finally. */
@RunWith(AndroidJUnit4.class)
public final class FeedUxDeviceTest {
    private String uid;

    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Before
    public void identity() {
        assertNotNull(FirebaseAuth.getInstance().getCurrentUser());
        uid = FirebaseAuth.getInstance().getUid();
    }

    @After
    public void unchanged() {
        assertEquals(uid, FirebaseAuth.getInstance().getUid());
    }

    private void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        long until = SystemClock.elapsedRealtime() + 30000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < until)
            Thread.sleep(100);
        assertTrue("Expected native condition within deadline", condition.getAsBoolean());
    }

    private void ready(ActivityScenario<MainActivity> s) throws Exception {
        AtomicBoolean value = new AtomicBoolean();
        waitFor(
                () -> {
                    s.onActivity(
                            a ->
                                    value.set(
                                            a.hasWindowFocus()
                                                    && a.findViewById(R.id.home_notifications_btn)
                                                            != null
                                                    && a.findViewById(R.id.bottom_navigation)
                                                            .isShown()));
                    return value.get();
                });
    }

    private FeedViewModel model(MainActivity a) {
        return new ViewModelProvider(a.getSupportFragmentManager().findFragmentByTag("home"))
                .get(FeedViewModel.class);
    }

    @Test
    public void favoritesSwitchesIndependentLibrariesInSameWindow() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            ready(s);
            s.onActivity(a -> a.navigate("favorites"));
            onView(withText(R.string.feed_saved_pets)).perform(click());
            AtomicReference<androidx.fragment.app.Fragment> pets = new AtomicReference<>();
            s.onActivity(
                    a -> {
                        androidx.fragment.app.Fragment parent =
                                a.getSupportFragmentManager().findFragmentByTag("favorites");
                        pets.set(parent.getChildFragmentManager().getPrimaryNavigationFragment());
                        assertTrue(
                                pets.get()
                                        instanceof
                                        com.gmail.manhle240406.project.adoption.SavedPetsFragment);
                    });
            onView(withText(R.string.feed_liked_posts)).perform(click());
            s.onActivity(
                    a ->
                            assertTrue(
                                    a.getSupportFragmentManager()
                                                    .findFragmentByTag("favorites")
                                                    .getChildFragmentManager()
                                                    .getPrimaryNavigationFragment()
                                            instanceof LikedPostsFragment));
            onView(withText(R.string.feed_saved_pets)).perform(click());
            s.onActivity(
                    a -> {
                        assertSame(
                                pets.get(),
                                a.getSupportFragmentManager()
                                        .findFragmentByTag("favorites")
                                        .getChildFragmentManager()
                                        .getPrimaryNavigationFragment());
                        assertTrue(a.findViewById(R.id.bottom_navigation).isShown());
                    });
        }
    }

    @Test
    public void recommendationShortcutUsesInlineDiscovery() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            ready(s);
            s.onActivity(a -> a.navigate("tools:0"));
            onView(withTagValue(org.hamcrest.Matchers.is("flow_F37"))).perform(scrollTo(), click());
            onView(withId(android.R.id.button1))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(click());
            s.onActivity(
                    a -> {
                        assertEquals("discovery", a.destination());
                        assertTrue(a.findViewById(R.id.bottom_navigation).isShown());
                    });
            androidx.test.espresso.Espresso.pressBack();
            s.onActivity(a -> assertEquals("tools:0", a.destination()));
        }
    }

    @Test
    public void settingsExploreShortcutReturnsToExistingMainHost() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        MainActivity original =
                (MainActivity)
                        instrumentation.startActivitySync(
                                new Intent(context(), MainActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicReference<MainActivity> resumed = new AtomicReference<>(original);
        AtomicBoolean ready = new AtomicBoolean();
        try {
            waitFor(
                    () -> {
                        instrumentation.runOnMainSync(
                                () -> {
                                    for (Activity a :
                                            androidx.test.runner.lifecycle
                                                    .ActivityLifecycleMonitorRegistry.getInstance()
                                                    .getActivitiesInStage(
                                                            androidx.test.runner.lifecycle.Stage
                                                                    .RESUMED))
                                        if (a instanceof MainActivity) {
                                            resumed.set((MainActivity) a);
                                            ready.set(
                                                    a.hasWindowFocus()
                                                            && a.findViewById(
                                                                            R.id
                                                                                    .home_notifications_btn)
                                                                    != null);
                                        }
                                });
                        return ready.get();
                    });
            int task = original.getTaskId();
            instrumentation.runOnMainSync(
                    () ->
                            resumed.get()
                                    .startActivity(
                                            new Intent(
                                                    context(),
                                                    com.gmail.manhle240406.project.profile
                                                            .EnhancedSettingsActivity.class)));
            UiDevice device = UiDevice.getInstance(instrumentation);
            assertTrue(
                    device.wait(
                            androidx.test.uiautomator.Until.hasObject(
                                    androidx.test.uiautomator.By.res(
                                            context().getPackageName(), "enhanced_settings_root")),
                            15000));
            onView(withText(R.string.flow_explore)).perform(scrollTo(), click());
            ready.set(false);
            waitFor(
                    () -> {
                        instrumentation.runOnMainSync(
                                () -> {
                                    for (Activity a :
                                            androidx.test.runner.lifecycle
                                                    .ActivityLifecycleMonitorRegistry.getInstance()
                                                    .getActivitiesInStage(
                                                            androidx.test.runner.lifecycle.Stage
                                                                    .RESUMED))
                                        if (a instanceof MainActivity) {
                                            resumed.set((MainActivity) a);
                                            ready.set(
                                                    a.hasWindowFocus()
                                                            && "tools"
                                                                    .equals(
                                                                            ((MainActivity) a)
                                                                                    .destination()));
                                        }
                                });
                        return ready.get();
                    });
            instrumentation.runOnMainSync(
                    () -> {
                        assertEquals(task, resumed.get().getTaskId());
                        assertTrue(resumed.get().findViewById(R.id.bottom_navigation).isShown());
                    });
        } finally {
            instrumentation.runOnMainSync(
                    () -> {
                        if (resumed.get() != null && !resumed.get().isFinishing())
                            resumed.get().finish();
                    });
        }
    }

    @Test
    public void explicitHomeEntryIntentDoesNotOverrideRestoredDestination() throws Exception {
        try (ActivityScenario<MainActivity> s =
                ActivityScenario.launch(
                        new Intent(context(), MainActivity.class)
                                .putExtra(MainActivity.PAGE, "home"))) {
            ready(s);
            s.onActivity(a -> a.navigate("tools"));
            s.recreate();
            AtomicBoolean restored = new AtomicBoolean();
            waitFor(
                    () -> {
                        s.onActivity(
                                a ->
                                        restored.set(
                                                a.hasWindowFocus()
                                                        && "tools".equals(a.destination())));
                        return restored.get();
                    });
        }
    }

    @Test
    public void adoptionComposerRequiresCurrentOwnedListingAndCommunityAudience() throws Exception {
        try (ActivityScenario<SocialComposerActivity> s =
                ActivityScenario.launch(SocialComposerActivity.class)) {
            onView(withId(R.id.post_type_picker)).perform(click());
            onData(org.hamcrest.Matchers.is(context().getString(R.string.feed_adoption)))
                    .perform(click());
            onView(withId(R.id.adoption_listing_action))
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
            s.onActivity(
                    a -> {
                        DailyViewModel m = new ViewModelProvider(a).get(DailyViewModel.class);
                        assertTrue(m.adoption);
                        assertNull(m.selectedListing);
                    });
            onView(withId(R.id.social_publish_action)).perform(scrollTo(), click());
            onView(withText(R.string.feed_listing_required))
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
        }
    }

    @Test
    public void adoptionPickerOpensCurrentOwnQueryAndCanCancel() throws Exception {
        try (ActivityScenario<SocialComposerActivity> s =
                ActivityScenario.launch(SocialComposerActivity.class)) {
            onView(withId(R.id.post_type_picker)).perform(click());
            onData(org.hamcrest.Matchers.is(context().getString(R.string.feed_adoption)))
                    .perform(click());
            onView(withId(R.id.adoption_listing_action)).perform(click());
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            assertTrue(
                    "Current own-listing picker must finish its bounded query",
                    device.wait(
                            androidx.test.uiautomator.Until.hasObject(
                                    androidx.test.uiautomator.By.res("android", "button2")),
                            25000));
            onView(withId(android.R.id.button2))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(click());
            s.onActivity(
                    a ->
                            assertNull(
                                    new ViewModelProvider(a)
                                            .get(DailyViewModel.class)
                                            .selectedListing));
        }
    }

    @Test
    public void actualLikeAppearsInLibraryAndUiUnlikeRemovesCanonicalReference() throws Exception {
        String post = InstrumentationRegistry.getArguments().getString("uxPost");
        assertNotNull(post);
        com.google.firebase.firestore.FirebaseFirestore db =
                com.google.firebase.firestore.FirebaseFirestore.getInstance();
        com.gmail.manhle240406.project.foundation.SessionGateway sessions =
                ((AniloveApplication) context().getApplicationContext()).foundation().sessions;
        com.gmail.manhle240406.project.foundation.SessionEpoch.Ticket ticket = sessions.capture();
        SocialRepository repo = new SocialRepository(db, sessions);
        String own = "communityPosts/" + post + "/likes/" + uid;
        try {
            Map<String, Object> previous =
                    com.google.android.gms.tasks.Tasks.await(
                                    db.document(own)
                                            .get(com.google.firebase.firestore.Source.SERVER),
                                    20,
                                    java.util.concurrent.TimeUnit.SECONDS)
                            .getData();
            assertFalse(previous != null && Boolean.TRUE.equals(previous.get("active")));
            com.google.android.gms.tasks.Tasks.await(
                    repo.execute(
                            ticket,
                            new SocialCommand(
                                    "set_like",
                                    post,
                                    null,
                                    1,
                                    0,
                                    SocialRepository.number(previous, "likeVersion"),
                                    SocialRepository.map("active", true),
                                    UUID.randomUUID().toString())),
                    25,
                    java.util.concurrent.TimeUnit.SECONDS);
            try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
                ready(s);
                s.onActivity(a -> a.navigate("favorites"));
                AtomicBoolean found = new AtomicBoolean();
                waitFor(
                        () -> {
                            s.onActivity(
                                    a -> {
                                        ArrayList<View> views = new ArrayList<>();
                                        a.getWindow()
                                                .getDecorView()
                                                .findViewsWithText(
                                                        views,
                                                        "[UX TEST] Temporary scrolling fixture 01",
                                                        View.FIND_VIEWS_WITH_TEXT);
                                        found.set(!views.isEmpty());
                                    });
                            return found.get();
                        });
                onView(
                                org.hamcrest.Matchers.allOf(
                                        withText(R.string.social_unlike),
                                        isDescendantOfA(
                                                withTagValue(org.hamcrest.Matchers.is(post)))))
                        .perform(click());
            }
            long end = SystemClock.elapsedRealtime() + 30000;
            boolean absent = false;
            while (!absent && SystemClock.elapsedRealtime() < end) {
                absent =
                        !com.google.android.gms.tasks.Tasks.await(
                                        db.document("users/" + uid + "/likedPosts/" + post)
                                                .get(com.google.firebase.firestore.Source.SERVER),
                                        20,
                                        java.util.concurrent.TimeUnit.SECONDS)
                                .exists();
                if (!absent) Thread.sleep(200);
            }
            assertTrue("UI Unlike must commit canonical mirror removal", absent);
        } finally {
            Map<String, Object> current =
                    com.google.android.gms.tasks.Tasks.await(
                                    db.document(own)
                                            .get(com.google.firebase.firestore.Source.SERVER),
                                    20,
                                    java.util.concurrent.TimeUnit.SECONDS)
                            .getData();
            if (current != null && Boolean.TRUE.equals(current.get("active")))
                com.google.android.gms.tasks.Tasks.await(
                        repo.execute(
                                ticket,
                                new SocialCommand(
                                        "set_like",
                                        post,
                                        null,
                                        1,
                                        0,
                                        SocialRepository.number(current, "likeVersion"),
                                        SocialRepository.map("active", false),
                                        UUID.randomUUID().toString())),
                        25,
                        java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    public void exploreToolsAndDiscoveryReuseMainWindowAndReturnToOrigin() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            ready(s);
            AtomicReference<View> bar = new AtomicReference<>();
            s.onActivity(a -> bar.set(a.findViewById(R.id.bottom_navigation)));
            onView(withId(R.id.home_filter_btn)).perform(click());
            s.onActivity(
                    a -> {
                        assertEquals("tools", a.destination());
                        assertSame(bar.get(), a.findViewById(R.id.bottom_navigation));
                    });
            onView(withTagValue(org.hamcrest.Matchers.is("flow_group_0"))).perform(click());
            onView(withTagValue(org.hamcrest.Matchers.is("flow_F12"))).perform(click());
            s.onActivity(
                    a -> {
                        assertEquals("discovery", a.destination());
                        assertSame(bar.get(), a.findViewById(R.id.bottom_navigation));
                        assertNotNull(a.findViewById(R.id.discovery_search));
                    });
            androidx.test.espresso.Espresso.pressBack();
            s.onActivity(a -> assertEquals("tools:0", a.destination()));
            androidx.test.espresso.Espresso.pressBack();
            s.onActivity(a -> assertEquals("tools", a.destination()));
            androidx.test.espresso.Espresso.pressBack();
            s.onActivity(a -> assertEquals("home", a.destination()));
            s.onActivity(a -> a.navigate("profile"));
            onView(withId(R.id.profile_tools)).perform(scrollTo(), click());
            androidx.test.espresso.Espresso.pressBack();
            s.onActivity(a -> assertEquals("profile", a.destination()));
        }
    }

    @Test
    public void discoverySearchSurvivesMainRecreation() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            ready(s);
            s.onActivity(a -> a.discovery("Bong", false));
            onView(withId(R.id.discovery_search))
                    .perform(
                            replaceText("Meo"),
                            pressImeActionButton(),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            s.recreate();
            AtomicBoolean found = new AtomicBoolean();
            waitFor(
                    () -> {
                        s.onActivity(a -> found.set(a.findViewById(R.id.discovery_search) != null));
                        return found.get();
                    });
            onView(withId(R.id.discovery_search))
                    .check(
                            androidx.test.espresso.assertion.ViewAssertions.matches(
                                    withText("Meo")));
            s.onActivity(a -> assertEquals("discovery", a.destination()));
        }
    }

    @Test
    public void homeAutomaticallyLoadsSecondPageWithoutMoreButton() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            ready(s);
            AtomicBoolean loaded = new AtomicBoolean();
            waitFor(
                    () -> {
                        s.onActivity(
                                a ->
                                        loaded.set(
                                                !model(a).busy
                                                        && model(a).window.rows().size() >= 20));
                        return loaded.get();
                    });
            s.onActivity(
                    a -> {
                        RecyclerView list = a.findViewById(R.id.home_posts_recycler);
                        list.scrollToPosition(list.getAdapter().getItemCount() - 1);
                    });
            waitFor(
                    () -> {
                        s.onActivity(a -> loaded.set(model(a).window.rows().size() > 20));
                        return loaded.get();
                    });
            s.onActivity(
                    a -> {
                        RecyclerView list = a.findViewById(R.id.home_posts_recycler);
                        assertTrue(
                                list.getAdapter()
                                        instanceof androidx.recyclerview.widget.ConcatAdapter);
                        for (int i = 0; i < list.getChildCount(); i++) {
                            ArrayList<View> more = new ArrayList<>();
                            list.getChildAt(i)
                                    .findViewsWithText(
                                            more,
                                            a.getString(R.string.wiring_more),
                                            View.FIND_VIEWS_WITH_TEXT);
                            assertTrue(more.isEmpty());
                        }
                    });
        }
    }

    @Test
    public void pullGestureRefreshesAndRetainsServerRows() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            ready(s);
            AtomicBoolean done = new AtomicBoolean(), observed = new AtomicBoolean();
            AtomicReference<androidx.lifecycle.Observer<Long>> watch = new AtomicReference<>();
            waitFor(
                    () -> {
                        s.onActivity(
                                a -> done.set(!model(a).busy && !model(a).window.rows().isEmpty()));
                        return done.get();
                    });
            s.onActivity(
                    a -> {
                        model(a).changes
                                .observeForever(
                                        watch.updateAndGet(
                                                old ->
                                                        n -> {
                                                            if (model(a).refreshing)
                                                                observed.set(true);
                                                        }));
                        ((RecyclerView) a.findViewById(R.id.home_posts_recycler))
                                .scrollToPosition(0);
                    });
            onView(withId(R.id.home_refresh)).perform(swipeDown());
            waitFor(observed::get);
            waitFor(
                    () -> {
                        s.onActivity(
                                a ->
                                        done.set(
                                                !model(a).busy
                                                        && !model(a).refreshing
                                                        && !model(a).window.rows().isEmpty()));
                        return done.get();
                    });
            s.onActivity(a -> model(a).changes.removeObserver(watch.get()));
        }
    }

    @Test
    public void publishedAuthorNameAndActualAvatarRenderOnHome() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            ready(s);
            AtomicBoolean loaded = new AtomicBoolean();
            waitFor(
                    () -> {
                        s.onActivity(
                                a ->
                                        loaded.set(
                                                !model(a).busy
                                                        && !model(a).window.rows().isEmpty()));
                        return loaded.get();
                    });
            s.onActivity(
                    a ->
                            ((RecyclerView) a.findViewById(R.id.home_posts_recycler))
                                    .scrollToPosition(1));
            waitFor(
                    () -> {
                        s.onActivity(
                                a -> {
                                    ArrayList<View> names = new ArrayList<>();
                                    a.getWindow()
                                            .getDecorView()
                                            .findViewsWithText(
                                                    names,
                                                    "UX Fixture Author",
                                                    View.FIND_VIEWS_WITH_TEXT);
                                    boolean actual = false;
                                    for (View name : names) {
                                        View row = (View) name.getParent().getParent();
                                        ImageView image = row.findViewById(R.id.post_author_avatar);
                                        if (image != null
                                                && image.getDrawable()
                                                        instanceof
                                                        android.graphics.drawable.BitmapDrawable)
                                            actual = true;
                                    }
                                    loaded.set(actual);
                                });
                        return loaded.get();
                    });
        }
    }

    @Test
    public void detailRendersPublishedAvatar() throws Exception {
        String post = InstrumentationRegistry.getArguments().getString("uxPost");
        assertNotNull("Controlled fixture required", post);
        try (ActivityScenario<SocialDetailActivity> s =
                ActivityScenario.launch(
                        new Intent(context(), SocialDetailActivity.class).putExtra("post", post))) {
            AtomicBoolean loaded = new AtomicBoolean();
            waitFor(
                    () -> {
                        s.onActivity(
                                a -> {
                                    ImageView image = a.findViewById(R.id.post_author_avatar);
                                    loaded.set(
                                            image != null
                                                    && image.getDrawable()
                                                            instanceof
                                                            android.graphics.drawable
                                                                    .BitmapDrawable);
                                });
                        return loaded.get();
                    });
        }
    }

    @Test
    public void acknowledgedPublicationClosesComposerWithoutAnotherBack() throws Exception {
        try (ActivityScenario<SocialComposerActivity> s =
                ActivityScenario.launch(SocialComposerActivity.class)) {
            AtomicBoolean finished = new AtomicBoolean();
            s.onActivity(
                    a -> {
                        DailyViewModel m = new ViewModelProvider(a).get(DailyViewModel.class);
                        m.lastCommittedCommand = "publish";
                        m.committed = true;
                        m.dirty = false;
                        m.changed();
                        finished.set(a.isFinishing());
                    });
            assertTrue("Committed publication should dismiss composer", finished.get());
        }
    }
}
