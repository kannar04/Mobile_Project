package com.gmail.manhle240406.project.wiring;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.adoption.*;
import com.gmail.manhle240406.project.community.*;
import com.gmail.manhle240406.project.notifications.InboxActivity;
import com.gmail.manhle240406.project.profile.*;

import org.junit.*;
import org.junit.runner.RunWith;

import java.io.*;

/**
 * Real native browsing/notification probe. No publication, synthetic remote notification or push
 * claim.
 */
@RunWith(AndroidJUnit4.class)
public final class MainNavigationDeviceTest {
    @BeforeClass
    public static void session() throws Exception {
        NativeFlowJourneyTest.session();
    }

    @AfterClass
    public static void cleanup() throws Exception {
        NativeFlowJourneyTest.cleanup();
    }

    private Context app() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(300, 5000);
        File dir = new File(app().getExternalFilesDir(null), "navigation-evidence");
        assertTrue(dir.mkdirs() || dir.isDirectory());
        Bitmap image =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        image.recycle();
    }

    @Test
    public void mainBrowsingSurfacesRenderInCanonicalMainActivity() throws Exception {
        String[] pages = {
            "discovery", "favorites", "notifications", "chat", "tools", "profile", "home"
        };
        for (String page : pages)
            try (ActivityScenario<MainActivity> screen =
                    ActivityScenario.launch(
                            new Intent(app(), MainActivity.class)
                                    .putExtra(MainActivity.PAGE, page))) {
                onView(withId(R.id.bottom_navigation)).check(matches(isDisplayed()));
                screen.onActivity(
                        a -> {
                            assertEquals(page, a.destination());
                            View bar = a.findViewById(R.id.bottom_navigation);
                            assertTrue(bar.getHeight() > 0);
                            View root = a.findViewById(android.R.id.content);
                            assertTrue(bar.getBottom() <= root.getHeight());
                        });
                capture("MainActivity-" + page);
            }
    }

    @Test
    public void canonicalCommunityFeedKeepsOneBarAndReturnsHome() {
        try (ActivityScenario<CommunityFeedActivity> screen =
                ActivityScenario.launch(CommunityFeedActivity.class)) {
            onView(withId(R.id.bottom_navigation)).check(matches(isDisplayed()));
            screen.onActivity(
                    a -> {
                        View bar = a.findViewById(R.id.bottom_navigation);
                        assertNotNull(bar);
                        assertTrue(bar.getHeight() > 0);
                    });
            onView(withId(R.id.nav_home)).perform(click());
            onView(withId(R.id.home_filter_btn)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void publicReaderAndExplicitAudienceAreReachable() throws Exception {
        try (ActivityScenario<GuestStoriesActivity> screen =
                ActivityScenario.launch(GuestStoriesActivity.class)) {
            onView(withId(R.id.public_feed_list)).check(matches(isDisplayed()));
            onView(withId(R.id.bottom_navigation)).check(matches(isDisplayed()));
            capture("guest-reader");
        }
        try (ActivityScenario<SocialComposerActivity> screen =
                ActivityScenario.launch(SocialComposerActivity.class)) {
            onView(withText(R.string.community_everyone)).check(matches(isDisplayed()));
            capture("composer-audience");
        }
        try (ActivityScenario<ModerationNoticesActivity> screen =
                ActivityScenario.launch(ModerationNoticesActivity.class)) {
            onView(withText(R.string.community_notices)).check(matches(isDisplayed()));
            capture("own-moderation-notices");
        }
    }

    @Test
    public void homeHeartAndCommentHaveDistinctLabels() throws Exception {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            onView(isRoot())
                    .perform(
                            new ViewAction() {
                                public org.hamcrest.Matcher<View> getConstraints() {
                                    return isRoot();
                                }

                                public String getDescription() {
                                    return "wait for seeded Home story";
                                }

                                public void perform(UiController ui, View root) {
                                    long end = SystemClock.uptimeMillis() + 15000;
                                    while (SystemClock.uptimeMillis() < end) {
                                        if (root.findViewById(R.id.post_like_btn) != null) return;
                                        ui.loopMainThreadForAtLeast(100);
                                    }
                                    throw new AssertionError("No live story reached Home");
                                }
                            });
            screen.onActivity(
                    a -> {
                        TextView heart = a.findViewById(R.id.post_like_btn),
                                comment = a.findViewById(R.id.post_comment_btn);
                        assertEquals(a.getString(R.string.social_like_action), heart.getText());
                        assertEquals(a.getString(R.string.social_open), comment.getText());
                        assertNotEquals(heart.getText(), comment.getText());
                    });
            capture("home-distinct-actions");
        }
    }

    private void awaitIntent(CommunityOutbox queue, String operation) throws Exception {
        queue.refresh();
        long end = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < end) {
            java.util.List<CommunityOutbox.Entry> rows = queue.entries().getValue();
            if (rows != null
                    && rows.stream().anyMatch(e -> operation.equals(e.command.operationId))) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Acknowledged local intent not restored");
    }

    @Test
    public void explicitQueuePersistsEncryptedAndCanBeCanceled() throws Exception {
        String prior = com.google.firebase.auth.FirebaseAuth.getInstance().getUid(),
                key = java.util.UUID.randomUUID().toString();
        com.google.firebase.FirebaseApp secondary =
                com.google.firebase.FirebaseApp.initializeApp(
                        app(),
                        com.google.firebase.FirebaseApp.getInstance().getOptions(),
                        "outbox-test-" + key);
        com.google.firebase.auth.FirebaseUser fixture = null;
        com.gmail.manhle240406.project.foundation.SessionGateway session = null;
        try {
            fixture =
                    com.google.android.gms.tasks.Tasks.await(
                                    com.google.firebase.auth.FirebaseAuth.getInstance(secondary)
                                            .createUserWithEmailAndPassword(
                                                    "native-flow-" + key + "@example.invalid",
                                                    key + java.util.UUID.randomUUID()),
                                    30,
                                    java.util.concurrent.TimeUnit.SECONDS)
                            .getUser();
            assertNotNull(fixture);
            session =
                    new com.gmail.manhle240406.project.foundation.SessionGateway(
                            com.google.firebase.auth.FirebaseAuth.getInstance(secondary));
            Context isolated =
                    new ContextWrapper(app()) {
                        @Override
                        public Context getApplicationContext() {
                            return this;
                        }

                        @Override
                        public File getNoBackupFilesDir() {
                            File dir =
                                    new File(
                                            super.getNoBackupFilesDir(),
                                            "community-queue-device-test");
                            if (!dir.isDirectory() && !dir.mkdirs())
                                throw new IllegalStateException("test_directory");
                            return dir;
                        }
                    };
            CommunityOutbox.Scheduler scheduler =
                    new CommunityOutbox.Scheduler() {
                        public void schedule(long delay) {}

                        public void cancel() {}
                    };
            CommunityOutbox queue = new CommunityOutbox(isolated, session, scheduler);
            com.gmail.manhle240406.project.foundation.SessionEpoch.Ticket ticket =
                    session.capture();
            String post = "cb2de5c3-c41d-4341-ab4a-afa9b9751a6a",
                    op = java.util.UUID.randomUUID().toString(),
                    target = java.util.UUID.randomUUID().toString(),
                    candidate = "Unsent device-test comment " + target;
            com.google.android.gms.tasks.Tasks.await(
                    queue.accept(
                            ticket,
                            new SocialCommand(
                                    "add_comment",
                                    post,
                                    target,
                                    1,
                                    0,
                                    0,
                                    SocialRepository.map("body", candidate),
                                    op)),
                    15,
                    java.util.concurrent.TimeUnit.SECONDS);
            awaitIntent(queue, op);
            CommunityOutbox reopened = new CommunityOutbox(isolated, session, scheduler);
            awaitIntent(reopened, op);
            assertEquals(candidate, reopened.entries().getValue().get(0).command.input.get("body"));
            String first = java.util.UUID.randomUUID().toString(),
                    last = java.util.UUID.randomUUID().toString();
            com.google.android.gms.tasks.Tasks.await(
                    queue.accept(
                            ticket,
                            new SocialCommand(
                                    "set_like",
                                    post,
                                    null,
                                    1,
                                    0,
                                    0,
                                    SocialRepository.map("active", false),
                                    first)),
                    15,
                    java.util.concurrent.TimeUnit.SECONDS);
            com.google.android.gms.tasks.Tasks.await(
                    queue.accept(
                            ticket,
                            new SocialCommand(
                                    "set_like",
                                    post,
                                    null,
                                    1,
                                    0,
                                    0,
                                    SocialRepository.map("active", true),
                                    last)),
                    15,
                    java.util.concurrent.TimeUnit.SECONDS);
            awaitIntent(queue, last);
            assertEquals(2, queue.entries().getValue().size());
            assertFalse(
                    queue.entries().getValue().stream()
                            .anyMatch(e -> first.equals(e.command.operationId)));
            assertTrue((Boolean) queue.pendingLike(post).command.input.get("active"));
            for (File database : isolated.getNoBackupFilesDir().listFiles())
                if (database.getName().startsWith("community-intents.db"))
                    assertFalse(
                            new String(
                                            java.nio.file.Files.readAllBytes(database.toPath()),
                                            java.nio.charset.StandardCharsets.ISO_8859_1)
                                    .contains(candidate));
            com.google.android.gms.tasks.Tasks.await(
                    queue.cancel(op), 15, java.util.concurrent.TimeUnit.SECONDS);
            com.google.android.gms.tasks.Tasks.await(
                    queue.cancel(last), 15, java.util.concurrent.TimeUnit.SECONDS);
            reopened.refresh();
            long until = SystemClock.uptimeMillis() + 5000;
            while (SystemClock.uptimeMillis() < until && !reopened.entries().getValue().isEmpty())
                SystemClock.sleep(50);
            assertTrue(reopened.entries().getValue().isEmpty());
            assertEquals(prior, com.google.firebase.auth.FirebaseAuth.getInstance().getUid());
        } finally {
            if (session != null) session.close();
            if (fixture != null)
                com.google.android.gms.tasks.Tasks.await(
                        fixture.delete(), 30, java.util.concurrent.TimeUnit.SECONDS);
            secondary.delete();
            assertEquals(prior, com.google.firebase.auth.FirebaseAuth.getInstance().getUid());
        }
        File evidence = new File(app().getExternalFilesDir(null), "navigation-evidence");
        assertTrue(evidence.mkdirs() || evidence.isDirectory());
        try (FileWriter record = new FileWriter(new File(evidence, "outbox-probe.json"))) {
            record.write(
                    "{\"encryptedAtRest\":true,\"reopened\":true,\"likeCoalescing\":true,\"canceled\":true,\"authFixtureDeleted\":true,\"defaultSessionUnchanged\":true}");
        }
    }

    @Test
    public void demoShareRequiresFreshSourceAndShowsHonestPreview() throws Exception {
        Intent story =
                new Intent(app(), SocialDetailActivity.class)
                        .putExtra("post", "cb2de5c3-c41d-4341-ab4a-afa9b9751a6a");
        try (ActivityScenario<SocialDetailActivity> screen = ActivityScenario.launch(story)) {
            onView(withText(R.string.flow_share))
                    .perform(androidx.test.espresso.action.ViewActions.scrollTo(), click());
            onView(withText(R.string.demo_share_help))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .check(matches(isDisplayed()));
            capture("demo-share-preview");
            onView(withText(android.R.string.cancel)).perform(click());
        }
    }

    @Test
    public void localNotificationDisplaysAndItsActionOpensInbox() throws Exception {
        Assume.assumeFalse(BuildConfig.FIREBASE_EMULATORS);
        String permission = "android.permission.POST_NOTIFICATIONS",
                channel = "anilove_device_probe";
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.GRANT_RUNTIME_PERMISSIONS");
        boolean granted =
                app().checkSelfPermission(permission)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
        File evidence = new File(app().getExternalFilesDir(null), "navigation-evidence");
        assertTrue(evidence.mkdirs() || evidence.isDirectory());
        try (FileWriter record =
                new FileWriter(new File(evidence, "notification-permission-original.json"))) {
            record.write("{\"granted\":" + granted + "}");
        }
        NotificationManager manager = app().getSystemService(NotificationManager.class);
        try {
            if (!granted)
                InstrumentationRegistry.getInstrumentation()
                        .getUiAutomation()
                        .grantRuntimePermission(app().getPackageName(), permission);
            manager.createNotificationChannel(
                    new NotificationChannel(
                            channel,
                            "ANILOVE device test",
                            NotificationManager.IMPORTANCE_DEFAULT));
            PendingIntent tap =
                    PendingIntent.getActivity(
                            app(),
                            9142,
                            new Intent(app(), InboxActivity.class),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            manager.notify(
                    9142,
                    new Notification.Builder(app(), channel)
                            .setSmallIcon(R.drawable.ic_flow_notifications)
                            .setContentTitle("ANILOVE notification test")
                            .setContentText("Local device test only. No new account activity.")
                            .setContentIntent(tap)
                            .setAutoCancel(true)
                            .setVisibility(Notification.VISIBILITY_PRIVATE)
                            .build());
            assertTrue(manager.areNotificationsEnabled());
            assertTrue(
                    java.util.Arrays.stream(manager.getActiveNotifications())
                            .anyMatch(n -> n.getId() == 9142));
            try (android.os.ParcelFileDescriptor command =
                    InstrumentationRegistry.getInstrumentation()
                            .getUiAutomation()
                            .executeShellCommand("cmd statusbar expand-notifications")) {}
            SystemClock.sleep(600);
            capture("notification-shade");
            try (android.os.ParcelFileDescriptor command =
                    InstrumentationRegistry.getInstrumentation()
                            .getUiAutomation()
                            .executeShellCommand("cmd statusbar collapse")) {}
            SystemClock.sleep(300);
            tap.send();
            onView(withId(R.id.inbox_root)).check(matches(isDisplayed()));
            capture("notification-open-inbox");
        } finally {
            try (android.os.ParcelFileDescriptor close =
                    InstrumentationRegistry.getInstrumentation()
                            .getUiAutomation()
                            .executeShellCommand("cmd statusbar collapse")) {}
            manager.cancel(9142);
            manager.deleteNotificationChannel(
                    channel); /* Host restores original permission after instrumentation exits: revoking here can kill the runner. */
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }
}
