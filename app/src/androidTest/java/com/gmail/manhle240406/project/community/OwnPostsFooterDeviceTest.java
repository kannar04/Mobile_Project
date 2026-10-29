package com.gmail.manhle240406.project.community;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.gmail.manhle240406.project.R;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class OwnPostsFooterDeviceTest {
    @Test
    public void newPostActionStaysOutsideTheScrollingHistory() {
        Intent intent =
                new Intent(
                                ApplicationProvider.getApplicationContext(),
                                SocialComposerActivity.class)
                        .putExtra("own", true);
        try (ActivityScenario<SocialComposerActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(
                    activity -> {
                        View footer = activity.findViewById(R.id.own_posts_footer);
                        View action = activity.findViewById(R.id.own_new_post_action);
                        assertTrue(footer != null && action != null);
                        assertTrue(footer.getParent() instanceof LinearLayout);
                        LinearLayout root = (LinearLayout) footer.getParent();
                        int footerIndex = root.indexOfChild(footer);
                        assertTrue(footerIndex > 0);
                        assertTrue(root.getChildAt(footerIndex - 1) instanceof ScrollView);
                        LinearLayout.LayoutParams scrollParams =
                                (LinearLayout.LayoutParams)
                                        root.getChildAt(footerIndex - 1).getLayoutParams();
                        assertEquals(1f, scrollParams.weight, 0f);
                        assertEquals(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                footer.getLayoutParams().height);
                    });
        }
    }
}
