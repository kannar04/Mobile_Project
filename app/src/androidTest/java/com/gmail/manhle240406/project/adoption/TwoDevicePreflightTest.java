package com.gmail.manhle240406.project.adoption;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.firebase.auth.*;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Read-only identity evidence. Never signs out, verifies, links or selects an account. */
@RunWith(AndroidJUnit4.class)
public final class TwoDevicePreflightTest {
    @Test
    public void inspectExistingSession() throws Exception {
        android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        JSONObject out =
                new JSONObject()
                        .put("signedIn", u != null)
                        .put("uid", u == null ? JSONObject.NULL : u.getUid())
                        .put("emailVerified", u != null && u.isEmailVerified())
                        .put("hasPhone", u != null && u.getPhoneNumber() != null)
                        .put(
                                "project",
                                com.google.firebase.FirebaseApp.getInstance()
                                        .getOptions()
                                        .getProjectId());
        Files.write(
                new File(c.getExternalFilesDir(null), "two-device-preflight.json").toPath(),
                out.toString().getBytes(StandardCharsets.UTF_8));
    }
}
