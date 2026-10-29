package com.gmail.manhle240406.project.wiring;

import static org.junit.Assert.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.gmail.manhle240406.project.MainActivity;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class AdminToolsLiveTest {
    @Test
    public void signedInOperatorSeesToolsEntry() throws Exception {
        String uid = FirebaseAuth.getInstance().getUid();
        assertNotNull(uid);
        assertEquals(
                true,
                com.google.android.gms.tasks.Tasks.await(
                                com.gmail.manhle240406.project.adoption.AdoptionApi.call(
                                        "flowRead",
                                        com.gmail.manhle240406.project.adoption.AdoptionFlowModel
                                                .map("kind", "operatorAccess")),
                                20,
                                TimeUnit.SECONDS)
                        .get("allowed"));
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            screen.onActivity(a -> a.navigate("tools"));
            AtomicBoolean visible = new AtomicBoolean();
            long deadline = android.os.SystemClock.uptimeMillis() + 15000;
            do {
                screen.onActivity(
                        a ->
                                visible.set(
                                        a.findViewById(android.R.id.content)
                                                        .findViewWithTag("flow_group_operator")
                                                != null));
                if (!visible.get()) android.os.SystemClock.sleep(100);
            } while (!visible.get() && android.os.SystemClock.uptimeMillis() < deadline);
            assertTrue("Admin tools entry must appear for the signed-in operator", visible.get());
        }
    }
}
