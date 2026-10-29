package com.gmail.manhle240406.project.auth;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * Read-only verification of the user's own explicitly selected Google session; no identity output.
 */
@RunWith(AndroidJUnit4.class)
public final class GoogleFirebaseSessionDeviceTest {
    @Test
    public void selectedGoogleAccountHasFirebaseSessionAndMinimalBootstrap() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        long until = SystemClock.elapsedRealtime() + 15000;
        while (auth.getCurrentUser() == null && SystemClock.elapsedRealtime() < until)
            SystemClock.sleep(100);
        FirebaseUser user = auth.getCurrentUser();
        assertNotNull("User-selected sign-in must restore a Firebase session", user);
        assertTrue(
                "Google provider must be linked by Firebase",
                user.getProviderData().stream()
                        .anyMatch(p -> "google.com".equals(p.getProviderId())));
        GetTokenResult token = Tasks.await(user.getIdToken(false), 20, TimeUnit.SECONDS);
        assertTrue(
                "Current Firebase sign-in provider must be Google",
                "google.com".equals(token.getSignInProvider()));
        assertTrue("Google email must be verified", user.isEmailVerified());
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentSnapshot shell = null, member = null;
        until = SystemClock.elapsedRealtime() + 25000;
        do {
            shell =
                    Tasks.await(
                            db.document("users/" + user.getUid()).get(Source.SERVER),
                            15,
                            TimeUnit.SECONDS);
            member =
                    Tasks.await(
                            db.document("communityMemberships/" + user.getUid()).get(Source.SERVER),
                            15,
                            TimeUnit.SECONDS);
            if (shell.exists() && member.exists()) break;
            SystemClock.sleep(300);
        } while (SystemClock.elapsedRealtime() < until);
        assertTrue("Minimal account shell must exist", shell.exists());
        assertTrue(
                "Account bootstrap must keep exact minimal fields",
                shell.getData().size() == 3
                        && user.getUid().equals(shell.getString("uid"))
                        && Long.valueOf(1).equals(shell.getLong("schemaVersion"))
                        && shell.getTimestamp("createdAt") != null);
        assertTrue(
                "Basic community membership must be ready",
                com.gmail.manhle240406.project.foundation.MembershipRepository.valid(
                                member.getData(), user.getUid())
                        && "active".equals(member.getString("status")));
        JSONObject facts =
                new JSONObject()
                        .put("firebaseGoogleSessionVerified", true)
                        .put("googleProviderLinked", true)
                        .put("verifiedEmail", true)
                        .put("minimalUidBootstrapVerified", true)
                        .put("ordinaryMembershipReady", true)
                        .put("accountDataModifiedByTest", false)
                        .put("personalIdentifiersExported", false);
        File dir = new File(app.getExternalFilesDir(null), "google-login-evidence");
        assertTrue(dir.mkdirs() || dir.isDirectory());
        try (FileOutputStream out = new FileOutputStream(new File(dir, "session-result.json"))) {
            out.write(facts.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
