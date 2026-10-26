package com.gmail.manhle240406.project.media;

import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;

import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.SocialViews;

import java.util.*;

/**
 * Four visual tiles, full manifest retained for the viewer and backed by the bounded session cache.
 */
public final class MediaGrid extends LinearLayout {
    private List<String> ids = Collections.emptyList();
    private boolean failed;
    private String accessTarget;

    public MediaGrid(Context context) {
        this(context, null);
    }

    public MediaGrid(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setSaveEnabled(false);
        setSaveFromParentEnabled(false);
    }

    public static List<String> ids(Object value) {
        if (!(value instanceof List)) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        for (Object id : (List<?>) value) {
            if (!(id instanceof String)) return Collections.emptyList();
            try {
                MediaContract.id((String) id);
            } catch (IllegalArgumentException bad) {
                return Collections.emptyList();
            }
            if (ids.contains(id)) return Collections.emptyList();
            ids.add((String) id);
        }
        return ids.size() <= 100 ? ids : Collections.emptyList();
    }

    public void bind(Object manifest) {
        List<String> next = ids(manifest);
        if (next.equals(ids) && getChildCount() > 0 && !failed) return;
        ids = new ArrayList<>(next);
        failed = false;
        removeAllViews();
        setVisibility(ids.isEmpty() ? GONE : VISIBLE);
        int count = MediaContract.visibleTiles(ids.size());
        if (count == 3) {
            LinearLayout line = new LinearLayout(getContext());
            line.setOrientation(HORIZONTAL);
            addView(line, new LayoutParams(-1, SocialViews.dp(getContext(), 244)));
            line.addView(tile(0), new LayoutParams(0, -1, 1));
            LinearLayout side = new LinearLayout(getContext());
            side.setOrientation(VERTICAL);
            line.addView(side, new LayoutParams(0, -1, 1));
            for (int i = 1; i < 3; i++) side.addView(tile(i), new LayoutParams(-1, 0, 1));
            return;
        }
        for (int row = 0; row < (count + 1) / 2; row++) {
            LinearLayout line = new LinearLayout(getContext());
            line.setOrientation(HORIZONTAL);
            addView(
                    line,
                    new LayoutParams(-1, SocialViews.dp(getContext(), count == 1 ? 240 : 156)));
            for (int column = 0; column < 2; column++) {
                int index = row * 2 + column;
                if (index < count) line.addView(tile(index), new LayoutParams(0, -1, 1));
            }
        }
    }

    public void bind(Object manifest, String target) {
        accessTarget = target;
        bind(manifest);
    }

    private FrameLayout tile(int index) {
        FrameLayout tile = new FrameLayout(getContext());
        tile.setPadding(2, 2, 2, 2);
        ImageView image = new ImageView(getContext());
        image.setScaleType(ImageView.ScaleType.CENTER);
        image.setBackgroundColor(
                com.google.android.material.color.MaterialColors.getColor(
                        image, com.google.android.material.R.attr.colorSurfaceVariant));
        image.setImageResource(R.drawable.ic_shell_photo);
        image.setContentDescription(
                getContext().getString(R.string.media_photo_position, index + 1, ids.size()));
        tile.addView(image, new FrameLayout.LayoutParams(-1, -1));
        ProgressBar loading =
                new ProgressBar(getContext(), null, android.R.attr.progressBarStyleSmall);
        loading.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(
                loading,
                new FrameLayout.LayoutParams(
                        SocialViews.dp(getContext(), 40),
                        SocialViews.dp(getContext(), 40),
                        Gravity.CENTER));
        TextView error = new TextView(getContext());
        error.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        error.setText(R.string.media_image_unavailable);
        error.setGravity(Gravity.CENTER);
        error.setVisibility(GONE);
        tile.addView(error, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        load(
                image,
                ids.get(index),
                "thumbnail",
                accessTarget,
                ok -> {
                    loading.setVisibility(GONE);
                    error.setVisibility(ok ? GONE : VISIBLE);
                    if (ok) image.setOnClickListener(v -> open(index));
                    else {
                        failed = true;
                        image.setOnClickListener(v -> bind(new ArrayList<>(ids)));
                        error.setOnClickListener(v -> bind(new ArrayList<>(ids)));
                    }
                });
        if (index == 3 && ids.size() > 4) {
            image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            TextView overlay = new TextView(getContext());
            overlay.setText(
                    getContext()
                            .getString(
                                    R.string.media_overflow,
                                    MediaContract.overflowCount(ids.size())));
            overlay.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium);
            overlay.setTextColor(android.graphics.Color.WHITE);
            overlay.setBackgroundColor(0x99000000);
            overlay.setGravity(Gravity.CENTER);
            overlay.setContentDescription(
                    getContext()
                            .getString(
                                    R.string.media_more_photos,
                                    MediaContract.overflowCount(ids.size())));
            overlay.setOnClickListener(v -> open(index));
            tile.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        }
        return tile;
    }

    private void open(int index) {
        getContext()
                .startActivity(
                        new Intent(getContext(), MediaViewerActivity.class)
                                .putStringArrayListExtra("assets", new ArrayList<>(ids))
                                .putExtra("index", index));
    }

    public static void load(ImageView image, String id, String variant) {
        load(image, id, variant, null, ok -> {});
    }

    private static final class ImageBinding {
        final java.util.function.Consumer<Boolean> complete;

        ImageBinding(java.util.function.Consumer<Boolean> c) {
            complete = c;
        }
    }

    private static void load(
            ImageView image,
            String id,
            String variant,
            String accessTarget,
            java.util.function.Consumer<Boolean> complete) {
        ImageBinding binding = new ImageBinding(complete);
        image.setTag(binding);
        java.lang.ref.WeakReference<ImageView> target = new java.lang.ref.WeakReference<>(image);
        java.lang.ref.WeakReference<ImageBinding> bindingRef =
                new java.lang.ref.WeakReference<>(binding);
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        long epoch = SessionImageCache.epoch();
        SessionImageCache.get(uid, id, variant, accessTarget)
                .addOnCompleteListener(
                        result -> {
                            ImageView view = target.get();
                            if (view == null
                                    || bindingRef.get() == null
                                    || view.getTag() != bindingRef.get()
                                    || !Objects.equals(
                                            uid,
                                            com.google.firebase.auth.FirebaseAuth.getInstance()
                                                    .getUid())) return;
                            if (epoch != SessionImageCache.epoch()) {
                                ((ImageBinding) view.getTag()).complete.accept(false);
                                return;
                            }
                            boolean ok = result.isSuccessful() && result.getResult() != null;
                            if (ok) {
                                if ("thumbnail".equals(variant))
                                    view.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                view.setImageBitmap(result.getResult());
                            } else {
                                view.setContentDescription(
                                        view.getContext().getString(R.string.media_image_retry));
                                android.util.Log.w(
                                        "MediaGrid",
                                        "Image load failed: " + id + "/" + variant,
                                        result.getException());
                            }
                            ((ImageBinding) view.getTag()).complete.accept(ok);
                        });
    }
}
