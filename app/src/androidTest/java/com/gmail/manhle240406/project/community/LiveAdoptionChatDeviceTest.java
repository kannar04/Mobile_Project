package com.gmail.manhle240406.project.community;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.gmail.manhle240406.project.MainActivity;
import com.gmail.manhle240406.project.adoption.AdoptionApi;
import com.gmail.manhle240406.project.adoption.AdoptionFlowModel;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Read-only live check for the signed-in participant's current adoption chat. */
@RunWith(AndroidJUnit4.class)
public final class LiveAdoptionChatDeviceTest {
    @SuppressWarnings("unchecked")
    @Test
    public void currentAdoptionConversationLoads() throws Exception {
        assertNotNull(
                "Sign in before running live chat verification",
                FirebaseAuth.getInstance().getCurrentUser());
        Map<String, Object> result =
                Tasks.await(
                        AdoptionApi.call(
                                "flowRead",
                                AdoptionFlowModel.map("kind", "chatInbox", "after", null)),
                        35,
                        TimeUnit.SECONDS);
        String id = null;
        Object raw = result.get("rows");
        if (raw instanceof List)
            for (Object item : (List<?>) raw)
                if (item instanceof Map) {
                    Map<String, Object> row = (Map<String, Object>) item;
                    if (!java.util.Arrays.asList(
                                    "FINALIZED",
                                    "FINALIZED_EARLY",
                                    "CANCELLED_BEFORE_HANDOVER",
                                    "RETURNED")
                            .contains(String.valueOf(row.get("status")))) {
                        id = String.valueOf(row.get("adoptionId"));
                        break;
                    }
                }
        assertNotNull("Signed-in account needs one active adoption conversation", id);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent =
                new Intent(context, MainActivity.class)
                        .putExtra(MainActivity.PAGE, "adoption-chat:" + id);
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(intent)) {
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            assertTrue(device.wait(Until.hasObject(By.text("Trao đổi nhận nuôi")), 20000));
            device.waitForIdle();
            Thread.sleep(2000);
            assertFalse(
                    "Conversation load must not end in the retry state",
                    device.hasObject(By.textContains("Chưa tải được cuộc trò chuyện")));
        }
    }
}
