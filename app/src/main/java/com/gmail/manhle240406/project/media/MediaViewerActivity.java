package com.gmail.manhle240406.project.media;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.*;

import java.util.*;

public final class MediaViewerActivity extends AppCompatActivity {
    private List<String> ids;
    private int index;
    private ImageView image;
    private TextView position;
    private com.google.android.material.button.MaterialButton previous, next;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        ids = MediaGrid.ids(getIntent().getStringArrayListExtra("assets"));
        if (ids.isEmpty()) {
            finish();
            return;
        }
        index =
                Math.max(
                        0,
                        Math.min(
                                ids.size() - 1,
                                state == null
                                        ? getIntent().getIntExtra("index", 0)
                                        : state.getInt("index")));
        LinearLayout root = SocialSurface.page(this);
        SocialSurface.header(this, root, R.string.media_photos, this::finish);
        position = SocialViews.text(this, "", true);
        position.setGravity(Gravity.CENTER);
        root.addView(position);
        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.MATRIX);
        root.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        ScaleGestureDetector scale =
                new ScaleGestureDetector(
                        this,
                        new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            public boolean onScaleBegin(ScaleGestureDetector d) {
                                matrix.set(image.getImageMatrix());
                                image.setScaleType(ImageView.ScaleType.MATRIX);
                                return true;
                            }

                            public boolean onScale(ScaleGestureDetector d) {
                                float[] values = new float[9];
                                matrix.getValues(values);
                                float current = values[android.graphics.Matrix.MSCALE_X];
                                float proposed = current * d.getScaleFactor();
                                if (proposed > .1f && proposed < 8f) {
                                    matrix.postScale(
                                            d.getScaleFactor(),
                                            d.getScaleFactor(),
                                            d.getFocusX(),
                                            d.getFocusY());
                                    image.setImageMatrix(matrix);
                                }
                                return true;
                            }
                        });
        image.setOnTouchListener(
                (v, event) -> {
                    scale.onTouchEvent(event);
                    if (event.getAction() == android.view.MotionEvent.ACTION_UP) v.performClick();
                    return true;
                });
        LinearLayout controls = new LinearLayout(this);
        previous =
                SocialViews.button(
                        this,
                        getString(R.string.media_previous),
                        () -> {
                            index--;
                            render();
                        });
        next =
                SocialViews.button(
                        this,
                        getString(R.string.media_next),
                        () -> {
                            index++;
                            render();
                        });
        controls.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(controls);
        root.addView(
                SocialSurface.quiet(this, getString(R.string.media_image_retry), this::render));
        render();
    }

    private void render() {
        position.setText(getString(R.string.media_photo_position, index + 1, ids.size()));
        previous.setEnabled(index > 0);
        next.setEnabled(index < ids.size() - 1);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageResource(R.drawable.profile_avatar_default);
        image.setContentDescription(position.getText());
        MediaGrid.load(image, ids.get(index), "display");
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        state.putInt("index", index);
        super.onSaveInstanceState(state);
    }
}
