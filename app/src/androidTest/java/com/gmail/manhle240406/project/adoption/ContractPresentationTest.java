package com.gmail.manhle240406.project.adoption;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static com.gmail.manhle240406.project.adoption.AdoptionFlowModel.map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.content.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.*;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class ContractPresentationTest {
    private final Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private String uid;

    @Before
    public void begin() {
        uid = FirebaseAuth.getInstance().getUid();
        assertNotNull(uid);
    }

    @After
    public void end() {
        AdoptionApi.testTransport = null;
        assertEquals(uid, FirebaseAuth.getInstance().getUid());
    }

    @Test
    public void storedJsonIsReadableAfterRotationAndAcceptsOriginalHash() throws Exception {
        String readable =
                "Synthetic course agreement\n\n"
                    + "{\"schemaVersion\":2,\"description\":\"Gentle cat\\n"
                    + "Needs daily"
                    + " care\",\"temperament\":\"Friendly\",\"identitySnapshot\":{\"displayName\":\"Milo\",\"marksUnknown\":false},\"careSnapshot\":{\"vaccinations\":[]}}";
        AtomicReference<Map<String, Object>> accepted = new AtomicReference<>();
        String hash = "immutable-original-hash";
        AdoptionApi.testTransport =
                (op, input) -> {
                    if (op.equals("flow")) {
                        accepted.set(new HashMap<>(input));
                        return Tasks.forResult(map("adoptionId", "contract-ui-fixture"));
                    }
                    return Tasks.forResult(
                            map(
                                    "adoption",
                                    map(
                                            "hostUid",
                                            uid,
                                            "adopterUid",
                                            "peer",
                                            "version",
                                            1,
                                            "status",
                                            "CONTRACT_PENDING"),
                                    "contract",
                                    map("readable", readable, "hash", hash, "acceptances", map())));
                };
        try (ActivityScenario<LifecycleActivity> screen =
                ActivityScenario.launch(
                        new Intent(c, LifecycleActivity.class)
                                .putExtra("adoption", "contract-ui-fixture"))) {
            onView(withText("Giới thiệu\nGentle cat\nNeeds daily care"))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withText(containsString("\"description\": "))).check(doesNotExist());
            screen.recreate();
            onView(withText("Tên thú cưng\nMilo"))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withText("Chưa rõ đặc điểm nhận dạng\nKhông"))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withText("Giới thiệu\nGentle cat\nNeeds daily care")).perform(scrollTo());
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .takeScreenshot(
                            new java.io.File(
                                    c.getExternalFilesDir(null), "contract-formatted.png"));
            onView(withTagValue(equalTo("lc_action_lc_accept"))).perform(scrollTo(), click());
            onView(withText(android.R.string.ok)).perform(click());
            assertNotNull(accepted.get());
            assertEquals(hash, accepted.get().get("contractHash"));
            assertEquals("acceptContract", accepted.get().get("command"));
        }
    }

    @Test
    public void plainAndInvalidLegacyTextIsPreserved() {
        for (String text :
                Arrays.asList("Ordinary agreement\nWith a second line", "Terms\n\n{invalid")) {
            List<ContractPresentation.Block> blocks = ContractPresentation.blocks(c, text);
            assertEquals(1, blocks.size());
            assertEquals(text, blocks.get(0).text);
        }
    }
}
