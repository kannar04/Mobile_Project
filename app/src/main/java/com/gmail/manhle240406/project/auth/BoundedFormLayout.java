package com.gmail.manhle240406.project.auth;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import com.gmail.manhle240406.project.R;

/** A form stays readable in wide windows and uses every available pixel in narrow ones. */
public final class BoundedFormLayout extends LinearLayout {
  public BoundedFormLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onMeasure(int width, int height) {
    int max = getResources().getDimensionPixelSize(R.dimen.auth_form_max_width);
    int mode = MeasureSpec.getMode(width);
    int available =
        mode == MeasureSpec.UNSPECIFIED ? max : Math.min(max, MeasureSpec.getSize(width));
    super.onMeasure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.EXACTLY), height);
  }
}
