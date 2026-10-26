package com.gmail.manhle240406.project.community;

import static com.gmail.manhle240406.project.community.SocialViews.*;

import android.content.Context;
import android.view.*;
import android.widget.*;

import com.gmail.manhle240406.project.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

/** Presentation-only primitives for the writing and discussion surfaces. */
public final class SocialSurface {
    private SocialSurface() {}

    public static LinearLayout page(android.app.Activity a) {
        LinearLayout root = a.findViewById(R.id.social_page_root);
        if (root == null) {
            root =
                    (LinearLayout)
                            LayoutInflater.from(a)
                                    .inflate(R.layout.layout_social_page, null, false);
            a.setContentView(root);
            insets(root);
        } else while (root.getChildCount() > 2) root.removeViewAt(root.getChildCount() - 1);
        return root;
    }

    public static MaterialToolbar header(Context c, LinearLayout root, int title, Runnable back) {
        MaterialToolbar bar = root.findViewById(R.id.social_header);
        if (bar == null) {
            bar =
                    (MaterialToolbar)
                            LayoutInflater.from(c)
                                    .inflate(R.layout.view_social_header, root, false);
            root.addView(bar, 0);
        }
        bar.setTitle(title);
        bar.setNavigationContentDescription(R.string.auth_back);
        bar.setNavigationOnClickListener(v -> back.run());
        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (child instanceof ImageButton) {
                android.graphics.drawable.GradientDrawable circle =
                        new android.graphics.drawable.GradientDrawable();
                circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                circle.setColor(
                        androidx.core.content.ContextCompat.getColor(c, R.color.anilove_chip_bg));
                android.util.TypedValue ripple = new android.util.TypedValue();
                c.getTheme().resolveAttribute(android.R.attr.colorControlHighlight, ripple, true);
                child.setBackground(
                        new android.graphics.drawable.RippleDrawable(
                                android.content.res.ColorStateList.valueOf(ripple.data),
                                circle,
                                null));
            }
        }
        return bar;
    }

    public static LinearLayout content(Context c, LinearLayout root) {
        ScrollView scroll = root.findViewById(R.id.social_scroll);
        LinearLayout content = root.findViewById(R.id.social_content);
        if (scroll == null || content == null) {
            scroll =
                    (ScrollView)
                            LayoutInflater.from(c)
                                    .inflate(R.layout.view_social_content, root, false);
            root.addView(scroll);
            content = scroll.findViewById(R.id.social_content);
        }
        int y = scroll.getScrollY();
        content.removeAllViews();
        final ScrollView s = scroll;
        s.post(() -> s.scrollTo(0, y));
        return content;
    }

    public static void gap(LinearLayout p, int n) {
        View space = new View(p.getContext());
        space.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        p.addView(space, new LinearLayout.LayoutParams(1, dp(p.getContext(), n)));
    }

    public static MaterialButton quiet(Context c, String label, Runnable action) {
        MaterialButton b =
                (MaterialButton)
                        LayoutInflater.from(c).inflate(R.layout.view_social_button, null, false);
        b.setStrokeWidth(0);
        b.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        b.setText(label);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    public static TextView muted(Context c, String s) {
        TextView v = text(c, s, false);
        v.setTextColor(
                MaterialColors.getColor(
                        v, com.google.android.material.R.attr.colorOnSurfaceVariant));
        return v;
    }

    public static MaterialButton more(Context c, Runnable action) {
        MaterialButton b = quiet(c, "", action);
        b.setIconResource(R.drawable.ic_social_more);
        b.setIconPadding(0);
        b.setContentDescription(c.getString(R.string.social_menu));
        b.setMinWidth(dp(c, 48));
        b.setMinimumWidth(dp(c, 48));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48)));
        b.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        return b;
    }

    public static void hideEmpty(TextView v) {
        v.setVisibility(v.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }
}
