package com.gmail.manhle240406.project.community;

import static org.junit.Assert.*;

import androidx.lifecycle.*;
import androidx.lifecycle.Observer;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.auth.AuthState;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class CommunityUiIntegrationTest {
    private AniloveApplication account() throws Exception {
        AniloveApplication app = ApplicationProvider.getApplicationContext();
        assertEquals("local_emulator", app.foundation().alias);
        CountDownLatch ready = new CountDownLatch(1);
        Observer<AuthState> observer =
                s -> {
                    if (s.uid != null && s.setupReady && !s.pending) ready.countDown();
                };
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            app.auth().logout();
                            app.auth().state().observeForever(observer);
                            app.auth()
                                    .email(
                                            "community-" + UUID.randomUUID() + "@example.test",
                                            "synthetic-password-123",
                                            true);
                        });
        try {
            assertTrue(ready.await(25, TimeUnit.SECONDS));
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .runOnMainSync(() -> app.auth().state().removeObserver(observer));
        }
        return app;
    }

    @Test
    public void actualComposerRotationRetainsMemoryButLogoutClearsBodyAndIdentifiers()
            throws Exception {
        AniloveApplication app = account();
        try (ActivityScenario<CommunityActivity> scenario =
                ActivityScenario.launch(CommunityActivity.class)) {
            AtomicReference<CommunityViewModel> m = new AtomicReference<>();
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < end) {
                scenario.onActivity(
                        a -> m.set(new ViewModelProvider(a).get(CommunityViewModel.class)));
                if (!m.get().busy) break;
                Thread.sleep(50);
            }
            scenario.onActivity(
                    a -> {
                        m.get().newPost();
                        m.get().changed("Synthetic unsaved private story", 2);
                    });
            String id = m.get().postId;
            scenario.recreate();
            scenario.onActivity(
                    a -> {
                        CommunityViewModel kept =
                                new ViewModelProvider(a).get(CommunityViewModel.class);
                        assertSame(m.get(), kept);
                        assertEquals(id, kept.postId);
                        assertEquals("Synthetic unsaved private story", kept.body);
                        assertFalse(a.findViewById(R.id.community_root).isSaveEnabled());
                    });
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.auth().logout());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals("", m.get().body);
            assertNull(m.get().postId);
            assertNull(m.get().pending);
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.auth().logout());
        }
    }

    @Test
    public void coldRestoreCarriesSafePendingIdsWithoutReplayingOrRestoringBody() throws Exception {
        AniloveApplication app = account();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            Map<String, Object> state = new HashMap<>();
                            state.put("community_uid", app.foundation().sessions.capture().uid);
                            state.put("community_post", UUID.randomUUID().toString());
                            state.put("community_operation", UUID.randomUUID().toString());
                            state.put("community_command", "create");
                            state.put("community_content", 0L);
                            state.put("community_control", 0L);
                            CommunityViewModel model =
                                    new CommunityViewModel(app, new SavedStateHandle(state));
                            assertTrue(model.restored);
                            assertEquals("", model.body);
                            assertNull(model.pending);
                            assertFalse(model.writePending);
                            model.retry();
                            assertFalse(model.writePending);
                        });
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.auth().logout());
    }
}
