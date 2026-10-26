package com.gmail.manhle240406.project.media;

import static org.junit.Assert.*;

import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.community.*;
import com.google.firebase.auth.FirebaseAuth;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class MediaSurfaceAuditDeviceTest {
    private String variant;
    private File directory;
    private JSONArray facts;

    private void configure(androidx.appcompat.app.AppCompatActivity a) {
        if (variant.contains("landscape"))
            a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        a.getDelegate()
                .setLocalNightMode(
                        variant.contains("dark")
                                ? AppCompatDelegate.MODE_NIGHT_YES
                                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void capture(String page) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(400, 4000);
        Bitmap b = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(b);
        try (FileOutputStream out =
                new FileOutputStream(new File(directory, variant + "-" + page + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        b.recycle();
    }

    private int decoded(View root) {
        int count =
                root instanceof android.widget.ImageView
                                && ((android.widget.ImageView) root).getDrawable()
                                        instanceof android.graphics.drawable.BitmapDrawable
                        ? 1
                        : 0;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) count += decoded(group.getChildAt(i));
        }
        return count;
    }

    @Test
    public void mediaSurfacesFitNativeVariants() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        variant = args.getString("variant", "portrait");
        String post = args.getString("mediaPost");
        assertNotNull(post);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String uid = FirebaseAuth.getInstance().getUid();
        assertNotNull(uid);
        directory = new File(context.getExternalFilesDir(null), "private-media-audit");
        assertTrue(directory.mkdirs() || directory.isDirectory());
        facts = new JSONArray();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(this::configure);
            AtomicBoolean ready = new AtomicBoolean();
            long end = SystemClock.elapsedRealtime() + 20000;
            do {
                scenario.onActivity(
                        a ->
                                ready.set(
                                        a.findViewById(R.id.home_notifications_btn) != null
                                                && a.findViewById(R.id.bottom_navigation)
                                                                .getVisibility()
                                                        == View.VISIBLE));
                if (!ready.get()) Thread.sleep(100);
            } while (!ready.get() && SystemClock.elapsedRealtime() < end);
            assertTrue(ready.get());
            scenario.onActivity(a -> a.navigate("profile"));
            Thread.sleep(500);
            scenario.onActivity(
                    a -> {
                        try {
                            assertEquals("profile", a.destination());
                            View bar = a.findViewById(R.id.bottom_navigation);
                            assertTrue(bar.isShown());
                            facts.put(
                                    new JSONObject()
                                            .put("page", "profile")
                                            .put("barVisible", true)
                                            .put(
                                                    "fontScale",
                                                    a.getResources().getConfiguration().fontScale));
                        } catch (JSONException e) {
                            throw new AssertionError(e);
                        }
                    });
            capture("profile");
        }
        try (ActivityScenario<SocialComposerActivity> scenario =
                ActivityScenario.launch(SocialComposerActivity.class)) {
            scenario.onActivity(this::configure);
            Thread.sleep(500);
            scenario.onActivity(
                    a -> {
                        try {
                            assertNotNull(a.findViewById(R.id.social_publish_action));
                            facts.put(
                                    new JSONObject()
                                            .put("page", "composer")
                                            .put("sendPresent", true)
                                            .put(
                                                    "fontScale",
                                                    a.getResources().getConfiguration().fontScale));
                        } catch (JSONException e) {
                            throw new AssertionError(e);
                        }
                    });
            capture("composer");
        }
        try (ActivityScenario<SocialDetailActivity> scenario =
                ActivityScenario.launch(
                        new Intent(context, SocialDetailActivity.class).putExtra("post", post))) {
            scenario.onActivity(this::configure);
            AtomicBoolean loaded = new AtomicBoolean();
            long end = SystemClock.elapsedRealtime() + 20000;
            do {
                scenario.onActivity(
                        a -> {
                            ArrayList<View> v = new ArrayList<>();
                            a.getWindow()
                                    .getDecorView()
                                    .findViewsWithText(v, "+2", View.FIND_VIEWS_WITH_TEXT);
                            loaded.set(!v.isEmpty() && decoded(a.getWindow().getDecorView()) >= 4);
                        });
                if (!loaded.get()) Thread.sleep(100);
            } while (!loaded.get() && SystemClock.elapsedRealtime() < end);
            assertTrue("Six images need four visible tiles and+2", loaded.get());
            facts.put(new JSONObject().put("page", "detail").put("overflow", 2));
            capture("detail");
        }
        com.google.firebase.firestore.DocumentSnapshot source =
                com.google.android.gms.tasks.Tasks.await(
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .document("communityFeed/" + post)
                                .get(com.google.firebase.firestore.Source.SERVER),
                        20,
                        java.util.concurrent.TimeUnit.SECONDS);
        try (ActivityScenario<MediaViewerActivity> viewer =
                ActivityScenario.launch(
                        new Intent(context, MediaViewerActivity.class)
                                .putStringArrayListExtra(
                                        "assets",
                                        new ArrayList<>(MediaGrid.ids(source.get("mediaAssetIds"))))
                                .putExtra("index", 3))) {
            viewer.onActivity(this::configure);
            AtomicBoolean pixels = new AtomicBoolean();
            long end = SystemClock.elapsedRealtime() + 20000;
            do {
                viewer.onActivity(a -> pixels.set(decoded(a.getWindow().getDecorView()) >= 1));
                if (!pixels.get()) Thread.sleep(100);
            } while (!pixels.get() && SystemClock.elapsedRealtime() < end);
            assertTrue("Full viewer must show real image bytes", pixels.get());
            facts.put(
                    new JSONObject()
                            .put("page", "viewer")
                            .put("index", 3)
                            .put("actualImageRendered", true));
            capture("viewer");
        }
        assertEquals(uid, FirebaseAuth.getInstance().getUid());
        try (FileOutputStream out =
                new FileOutputStream(new File(directory, variant + "-facts.json"))) {
            out.write(facts.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
