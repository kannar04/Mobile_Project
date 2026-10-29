package com.gmail.manhle240406.project.profile;

import static org.junit.Assert.*;

import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.MainActivity;
import com.gmail.manhle240406.project.R;
import com.google.android.gms.tasks.Tasks;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class LiveProfileRepairDeviceTest {
    @Test
    public void repairedProfileLoadsAndCanBeEdited() throws Exception {
        AniloveApplication app =
                (AniloveApplication)
                        InstrumentationRegistry.getInstrumentation()
                                .getTargetContext()
                                .getApplicationContext();
        com.gmail.manhle240406.project.foundation.SessionEpoch.Ticket ticket =
                app.foundation().sessions.capture();
        assertNotNull(ticket.uid);
        ProfileRepository repository =
                new ProfileRepository(
                        com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                        app.foundation().sessions);
        ProfileRepository.Result<ProfileRepository.Snapshot> result =
                Tasks.await(repository.load(ticket), 25, TimeUnit.SECONDS);
        assertEquals(ProfileRepository.Code.OK, result.code);
        assertNotNull(result.value.draft);
        assertNull(result.value.contact);
        if (!"public".equals(result.value.control.get("visibility"))) {
            long
                    publication =
                            result.value.published == null
                                    ? 0
                                    : (Long) result.value.published.get("publicationVersion"),
                    moderation = (Long) result.value.moderation.get("moderationVersion");
            ProfileRepository.Result<Map<String, Object>> published =
                    Tasks.await(
                            repository.publish(
                                    ticket,
                                    java.util.UUID.randomUUID().toString(),
                                    ProfileSchema.version(result.value.draft),
                                    ProfileSchema.version(result.value.control),
                                    publication,
                                    moderation,
                                    ProfileSchema.publicContent(result.value.draft)),
                            30,
                            TimeUnit.SECONDS);
            assertEquals(ProfileRepository.Code.OK, published.code);
            result = Tasks.await(repository.load(ticket), 25, TimeUnit.SECONDS);
        }
        assertEquals("public", result.value.control.get("visibility"));
        assertEquals(
                result.value.draft.get("version"),
                result.value.published.get("sourceDraftVersion"));
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            AtomicBoolean enabled = new AtomicBoolean();
            long until = SystemClock.elapsedRealtime() + 25000;
            do {
                scenario.onActivity(
                        a -> {
                            a.navigate("profile");
                            View edit = a.findViewById(R.id.profile_edit);
                            enabled.set(edit != null && edit.isEnabled());
                        });
                if (!enabled.get()) SystemClock.sleep(200);
            } while (!enabled.get() && SystemClock.elapsedRealtime() < until);
            assertTrue("Valid profile must expose editing", enabled.get());
        }
    }
}
