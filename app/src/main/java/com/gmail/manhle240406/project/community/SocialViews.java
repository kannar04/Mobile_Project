package com.gmail.manhle240406.project.community;

import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.core.view.*;
import com.gmail.manhle240406.project.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.*;

/** Shared native Material equivalents of the observed Figma primitives. */
public final class SocialViews {
  private SocialViews() {}

  public static int dp(Context c, int n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  public static LinearLayout column(Context c) {
    LinearLayout v = new com.gmail.manhle240406.project.auth.BoundedFormLayout(c, null);
    v.setOrientation(LinearLayout.VERTICAL);
    v.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
    return v;
  }

  public static void install(android.app.Activity activity, LinearLayout root) {
    FrameLayout frame = new FrameLayout(activity);
    frame.addView(root, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER_HORIZONTAL));
    activity.setContentView(frame);
  }

  public static TextView text(Context c, String value, boolean heading) {
    TextView t = new TextView(c);
    t.setTextAppearance(
        heading
            ? com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
            : com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
    t.setText(value);
    t.setPadding(0, dp(c, 8), 0, dp(c, 8));
    ViewCompat.setAccessibilityHeading(t, heading);
    privateView(t);
    return t;
  }

  public static MaterialButton button(Context c, String label, Runnable action) {
    MaterialButton b =
        new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
    b.setText(label);
    b.setMinHeight(dp(c, 48));
    b.setOnClickListener(v -> action.run());
    privateView(b);
    return b;
  }

  public static MaterialButton primaryButton(Context c, String label, Runnable action) {
    MaterialButton b = (MaterialButton) LayoutInflater.from(c).inflate(R.layout.pet_button, null);
    b.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
    b.setText(label);
    b.setOnClickListener(v -> action.run());
    privateView(b);
    return b;
  }

  public static TextInputEditText field(Context c, LinearLayout parent, String hint, int lines) {
    TextInputLayout l =
        (TextInputLayout) LayoutInflater.from(c).inflate(R.layout.pet_field, parent, false);
    l.setHint(hint);
    TextInputEditText e = (TextInputEditText) l.getEditText();
    e.setId(View.generateViewId());
    e.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    e.setMinLines(lines);
    e.setGravity(Gravity.TOP);
    e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
    privateView(l);
    parent.addView(l);
    return e;
  }

  public static void privateView(View v) {
    v.setSaveEnabled(false);
    v.setSaveFromParentEnabled(false);
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) privateView(g.getChildAt(i));
    }
  }

  public static void insets(View root) {
    final int left = root.getPaddingLeft(),
        top = root.getPaddingTop(),
        right = root.getPaddingRight(),
        bottom = root.getPaddingBottom();
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, i) -> {
          androidx.core.graphics.Insets
              b =
                  i.getInsets(
                      WindowInsetsCompat.Type.systemBars()
                          | WindowInsetsCompat.Type.displayCutout()),
              k = i.getInsets(WindowInsetsCompat.Type.ime());
          v.setPadding(
              left + b.left, top + b.top, right + b.right, bottom + Math.max(b.bottom, k.bottom));
          return i;
        });
    root.post(() -> ViewCompat.requestApplyInsets(root));
    if (root.getContext() instanceof android.app.Activity) {
      android.app.Activity a = (android.app.Activity) root.getContext();
      boolean dark =
          (root.getResources().getConfiguration().uiMode
                  & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
              == android.content.res.Configuration.UI_MODE_NIGHT_YES;
      androidx.core.view.WindowInsetsControllerCompat bars =
          androidx.core.view.WindowCompat.getInsetsController(
              a.getWindow(), a.getWindow().getDecorView());
      bars.setAppearanceLightStatusBars(!dark);
      bars.setAppearanceLightNavigationBars(!dark);
    }
  }
}
