package com.gmail.manhle240406.project.community;

import static org.junit.Assert.*;

import android.view.*;

import androidx.lifecycle.*;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.auth.AuthState;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.*;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real native recreation/session assertions. Requires an Android device + isolated emulators. */
@RunWith(AndroidJUnit4.class)
public final class DailyUseUiIntegrationTest {
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
                                            "daily-" + UUID.randomUUID() + "@example.test",
                                            "synthetic-password-123",
                                            true);
                        });
        try {
            assertTrue(ready.await(30, TimeUnit.SECONDS));
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .runOnMainSync(() -> app.auth().state().removeObserver(observer));
        }
        return app;
    }

    private TextInputEditText field(View v) {
        if (v instanceof TextInputEditText) return (TextInputEditText) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextInputEditText e = field(g.getChildAt(i));
                if (e != null) return e;
            }
        }
        return null;
    }

    @Test
    public void composerRotationKeepsMemoryButNoPrivateHierarchyOrSavedState() throws Exception {
        AniloveApplication app = account();
        AtomicReference<DailyViewModel> model = new AtomicReference<>();
        try (ActivityScenario<SocialComposerActivity> scenario =
                ActivityScenario.launch(SocialComposerActivity.class)) {
            scenario.onActivity(
                    a -> {
                        DailyViewModel m = new ViewModelProvider(a).get(DailyViewModel.class);
                        model.set(m);
                        TextInputEditText input = field(a.findViewById(android.R.id.content));
                        assertNotNull(input);
                        input.setText("Synthetic unsaved private moment");
                        assertFalse(input.isSaveEnabled());
                        assertEquals("Synthetic unsaved private moment", m.body);
                        assertFalse(m.saved.contains("body"));
                        assertFalse(m.saved.contains("reportText"));
                    });
            scenario.recreate();
            scenario.onActivity(
                    a -> {
                        assertSame(model.get(), new ViewModelProvider(a).get(DailyViewModel.class));
                        assertEquals(
                                "Synthetic unsaved private moment",
                                field(a.findViewById(android.R.id.content)).getText().toString());
                    });
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.auth().logout());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertTrue(model.get().lost);
            assertEquals("", model.get().body);
            assertNull(model.get().pending);
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.auth().logout());
        }
    }

    @Test
    public void contextualReportRotationKeepsTransientStatementAndLogoutClearsIt()
            throws Exception {
        AniloveApplication app = account();
        AtomicReference<DailyViewModel> model = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(
                    a -> {
                        model.set(new ViewModelProvider(a).get(DailyViewModel.class));
                        ReportSheet.target("post", UUID.randomUUID().toString(), null, 1, null)
                                .showNow(a.getSupportFragmentManager(), "report");
                        ReportSheet sheet =
                                (ReportSheet)
                                        a.getSupportFragmentManager().findFragmentByTag("report");
                        TextInputEditText details = field(sheet.requireView());
                        assertNotNull(details);
                        details.setText("Synthetic private concern");
                        assertFalse(details.isSaveEnabled());
                    });
            scenario.recreate();
            scenario.onActivity(
                    a -> {
                        ReportSheet sheet =
                                (ReportSheet)
                                        a.getSupportFragmentManager().findFragmentByTag("report");
                        assertNotNull(sheet);
                        assertEquals(
                                "Synthetic private concern",
                                field(sheet.requireView()).getText().toString());
                        assertFalse(model.get().saved.contains("reportText"));
                    });
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.auth().logout());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals("", model.get().reportText);
            assertTrue(model.get().lost);
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.auth().logout());
        }
    }

    @Test
    public void coldPendingStateContainsIdsAndNeverRestoresOrReplaysStatement() throws Exception {
        AniloveApplication app = account();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            Map<String, Object> values = new HashMap<>();
                            values.put("owner_uid", app.foundation().sessions.capture().uid);
                            values.put("pending_op", UUID.randomUUID().toString());
                            values.put("pending_kind", "report");
                            values.put("report_id", UUID.randomUUID().toString());
                            DailyViewModel m =
                                    new DailyViewModel(app, new SavedStateHandle(values));
                            assertTrue(m.restored);
                            assertEquals("", m.reportText);
                            assertNull(m.pendingReport);
                            assertNull(m.pending);
                            m.retry();
                            assertNull(m.pendingReport);
                            assertNull(m.pending);
                        });
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.auth().logout());
    }
}
