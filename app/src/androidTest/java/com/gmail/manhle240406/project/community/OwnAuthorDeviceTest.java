package com.gmail.manhle240406.project.community;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import org.junit.*;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@RunWith(AndroidJUnit4.class)
public final class OwnAuthorDeviceTest {
    @Test
    public void currentOwnersPostIdentityMatchesSavedNameWithoutPublishing() throws Exception {
        AniloveApplication app =
                (AniloveApplication)
                        InstrumentationRegistry.getInstrumentation()
                                .getTargetContext()
                                .getApplicationContext();
        String uid = FirebaseAuth.getInstance().getUid();
        assertNotNull(uid);
        DocumentSnapshot draft =
                Tasks.await(
                        FirebaseFirestore.getInstance()
                                .document("users/" + uid + "/profileDraft/current")
                                .get(Source.SERVER),
                        15,
                        TimeUnit.SECONDS);
        assertTrue(draft.exists());
        assertNotNull(draft.getString("displayName"));
        String expected = InstrumentationRegistry.getArguments().getString("expectedName");
        if (expected != null)
            assertTrue(
                    "Saved name must match the name reported by its owner",
                    expected.equalsIgnoreCase(draft.getString("displayName")));
        AtomicReference<AuthorLookup> lookup = new AtomicReference<>();
        AtomicReference<Task<Map<String, Object>>> task = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            lookup.set(new AuthorLookup(app.foundation().sessions));
                            lookup.get().clear();
                            task.set(lookup.get().get(uid));
                        });
        try {
            Map<String, Object> alias = Tasks.await(task.get(), 20, TimeUnit.SECONDS);
            assertNotNull("Own post must resolve saved identity without public visibility", alias);
            assertEquals(draft.getString("displayName"), alias.get("displayName"));
            assertFalse(alias.containsKey("phoneE164"));
            assertEquals("", alias.get("bio"));
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> lookup.get().close());
        }
    }
}
