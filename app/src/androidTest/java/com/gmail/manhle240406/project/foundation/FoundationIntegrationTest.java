package com.gmail.manhle240406.project.foundation;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.lifecycle.ViewModelProvider;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.MainActivity;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.MemoryCacheSettings;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Requires local Auth/Firestore emulators and an Android emulator. Never cloud. */
@RunWith(AndroidJUnit4.class)
public class FoundationIntegrationTest {
    private FirebaseEnvironment environment;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Before public void setup() {
        AniloveApplication app = ApplicationProvider.getApplicationContext();
        environment = app.foundation();
        assertEquals("local_emulator", environment.alias);
        assertNotNull(environment.repository);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        environment.sessions.logout();
    }
    @After public void cleanup() throws Exception {
        Tasks.await(db.enableNetwork(), 10, TimeUnit.SECONDS);
        environment.sessions.logout();
    }
    private String signInFixture() throws Exception {
        Tasks.await(auth.createUserWithEmailAndPassword("fixture-" + UUID.randomUUID() + "@example.test",
                "synthetic-pass-123"), 15, TimeUnit.SECONDS);
        String uid = auth.getCurrentUser().getUid();
        seed("securitySubjects/" + uid, "{\"fields\":{\"status\":{\"stringValue\":\"active\"},"
                + "\"roles\":{\"arrayValue\":{\"values\":[{\"stringValue\":\"member\"}]}},"
                + "\"updatedAt\":{\"timestampValue\":\"2026-10-12T00:00:00Z\"}}}");
        return uid;
    }
    private void seed(String path, String json) throws Exception {
        // Emulator-only fixture operator token; never an administrative credential.
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://10.0.2.2:8180/v1/projects/demo-anilove/databases/(default)/documents/" + path).openConnection();
        connection.setRequestMethod("PATCH");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Authorization", "Bearer owner");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        try {
            try (java.io.OutputStream out = connection.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            assertEquals(200, connection.getResponseCode());
        } finally { connection.disconnect(); }
    }
    @Test public void startupAndActivityRecreationKeepContainerAndDiagnosticIntent() {
        assertTrue(db.getFirestoreSettings().getCacheSettings() instanceof MemoryCacheSettings);
        AtomicReference<FoundationViewModel> original = new AtomicReference<>();
        AtomicReference<String> id = new AtomicReference<>();
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            activity.onActivity(a -> {
                original.set(new ViewModelProvider(a).get(FoundationViewModel.class));
                id.set(original.get().operationId());
            });
            activity.recreate();
            activity.onActivity(a -> {
                FoundationViewModel restored = new ViewModelProvider(a).get(FoundationViewModel.class);
                assertSame(original.get(), restored);
                assertEquals(id.get(), restored.operationId());
                assertSame(environment, ((AniloveApplication) a.getApplication()).foundation());
                assertNull(restored.result().getValue()); // recreation did not auto-submit
            });
        }
    }
    @Test public void configServerAndCacheHaveDifferentResultLabels() throws Exception {
        seed("system/config", "{\"fields\":{\"schemaVersion\":{\"integerValue\":\"1\"},"
                + "\"environmentLabel\":{\"stringValue\":\"local_emulator\"},"
                + "\"featureMode\":{\"stringValue\":\"course_demo\"},"
                + "\"updatedAt\":{\"timestampValue\":\"2026-10-12T00:00:00Z\"}}}");
        assertEquals(FoundationResult.Status.SERVER, Tasks.await(environment.repository.readConfig(
                FoundationRepository.ReadSource.SERVER), 15, TimeUnit.SECONDS).status);
        assertEquals(FoundationResult.Status.CACHED, Tasks.await(environment.repository.readConfig(
                FoundationRepository.ReadSource.CACHE), 15, TimeUnit.SECONDS).status);
    }
    @Test public void androidRepositoryRetriesSameIdAndDeletesIdempotently() throws Exception {
        signInFixture();
        String id = UUID.randomUUID().toString();
        FoundationResult<FoundationDocuments.Diagnostic> first = Tasks.await(environment.repository.runDiagnostic(id), 15, TimeUnit.SECONDS);
        FoundationResult<FoundationDocuments.Diagnostic> retry = Tasks.await(environment.repository.runDiagnostic(id), 15, TimeUnit.SECONDS);
        assertEquals(FoundationResult.Status.COMMITTED, first.status);
        assertEquals(first.value.createdAt, retry.value.createdAt);
        assertEquals(1, Tasks.await(environment.repository.listMyDiagnostics(null), 15, TimeUnit.SECONDS).value.size());
        assertEquals(FoundationResult.Status.COMMITTED, Tasks.await(environment.repository.clearMyDiagnostic(id), 15, TimeUnit.SECONDS).status);
        assertEquals(FoundationResult.Status.COMMITTED, Tasks.await(environment.repository.clearMyDiagnostic(id), 15, TimeUnit.SECONDS).status);
    }
    @Test public void accountSwitchInvalidatesCallbacksAndUsesOnlyNewOwnerPath() throws Exception {
        String uidA = signInFixture(), id = UUID.randomUUID().toString();
        Tasks.await(environment.repository.runDiagnostic(id), 15, TimeUnit.SECONDS);
        SessionEpoch.Ticket oldA = environment.sessions.capture();
        environment.sessions.logout();
        assertFalse(environment.sessions.current(oldA));
        assertEquals(FoundationResult.Status.SIGN_IN_REQUIRED,
                Tasks.await(environment.repository.listMyDiagnostics(null), 15, TimeUnit.SECONDS).status);
        signInFixture();
        assertEquals(0, Tasks.await(environment.repository.listMyDiagnostics(null), 15, TimeUnit.SECONDS).value.size());
        try {
            Tasks.await(db.document("foundationChecks/" + uidA + "/operations/" + id).get(
                    com.google.firebase.firestore.Source.SERVER), 15, TimeUnit.SECONDS);
            fail("Other account read succeeded");
        } catch (java.util.concurrent.ExecutionException expected) {
            assertTrue(expected.getCause() instanceof com.google.firebase.firestore.FirebaseFirestoreException);
        }
    }
    @Test public void offlineTransactionDoesNotClaimCommitAndRetryKeepsId() throws Exception {
        signInFixture();
        String id = UUID.randomUUID().toString();
        Tasks.await(db.disableNetwork(), 10, TimeUnit.SECONDS);
        FoundationResult<FoundationDocuments.Diagnostic> offline = Tasks.await(
                environment.repository.runDiagnostic(id), 15, TimeUnit.SECONDS);
        assertNotEquals(FoundationResult.Status.COMMITTED, offline.status);
        Tasks.await(db.enableNetwork(), 10, TimeUnit.SECONDS);
        assertEquals(id, Tasks.await(environment.repository.runDiagnostic(id), 15, TimeUnit.SECONDS).value.operationId);
    }
}
