package com.gmail.manhle240406.project.community;

import android.app.Activity;
import android.view.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.foundation.*;
import com.gmail.manhle240406.project.media.ManagedMediaClient;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.*;

import java.util.*;

public final class DirectChats {
    private DirectChats() {}

    public static void start(Activity activity, String uid, View feedback) {
        start(activity, uid, feedback, null);
    }

    private static void start(Activity activity, String uid, View feedback, Runnable opened) {
        SessionGateway s = ((AniloveApplication) activity.getApplication()).foundation().sessions;
        SessionEpoch.Ticket ticket = s.capture();
        feedback.setEnabled(false);
        ManagedMediaClient.call(
                        "directStart",
                        ManagedMediaClient.map(
                                "operationId", UUID.randomUUID().toString(), "recipientUid", uid))
                .addOnCompleteListener(
                        t -> {
                            if (activity.isDestroyed() || !s.current(ticket)) return;
                            feedback.setEnabled(true);
                            if (t.isSuccessful()) {
                                if (opened != null) opened.run();
                                MainActivity.open(
                                        activity, "direct:" + t.getResult().get("conversationId"));
                            } else
                                com.google.android.material.snackbar.Snackbar.make(
                                                feedback,
                                                R.string.oc3_chat_failed,
                                                com.google.android.material.snackbar.Snackbar
                                                        .LENGTH_LONG)
                                        .show();
                        });
    }

    public static void people(AppCompatActivity activity) {
        SessionGateway sessions =
                ((AniloveApplication) activity.getApplication()).foundation().sessions;
        SessionEpoch.Ticket ticket = sessions.capture();
        LinearLayout panel = SocialViews.column(activity);
        TextInputLayout box =
                new TextInputLayout(
                        activity, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        box.setHint(R.string.oc3_people_hint);
        TextInputEditText query = new TextInputEditText(box.getContext());
        query.setSingleLine(true);
        box.addView(query);
        panel.addView(box);
        LinearLayout results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(results);
        panel.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        SocialViews.dp(
                                activity,
                                Math.min(
                                        320,
                                        Math.max(
                                                100,
                                                (int)
                                                        (activity.getResources()
                                                                        .getDisplayMetrics()
                                                                        .heightPixels
                                                                / activity.getResources()
                                                                        .getDisplayMetrics()
                                                                        .density
                                                                * .35f))))));
        TextView status = SocialSurface.muted(activity, "");
        panel.addView(status);
        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.oc3_new_message)
                        .setView(panel)
                        .setPositiveButton(R.string.oc3_find, null)
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();
        final boolean[] busy = {false};
        Runnable invalidated = dialog::dismiss;
        sessions.onInvalidated(invalidated);
        dialog.setOnDismissListener(d -> sessions.removeInvalidation(invalidated));
        Runnable search =
                () -> {
                    if (busy[0]) return;
                    busy[0] = true;
                    status.setText(R.string.wiring_loading);
                    ManagedMediaClient.call(
                                    "directPeople",
                                    ManagedMediaClient.map(
                                            "prefix", query.getText().toString().trim()))
                            .addOnCompleteListener(
                                    t -> {
                                        if (!dialog.isShowing()
                                                || !sessions.current(ticket)
                                                || activity.isDestroyed()) return;
                                        busy[0] = false;
                                        results.removeAllViews();
                                        if (!t.isSuccessful()) {
                                            status.setText(R.string.oc3_chat_failed);
                                            return;
                                        }
                                        List<Map<String, Object>> rows =
                                                com.gmail.manhle240406.project.adoption
                                                        .AdoptionFlowModel.rows(
                                                        t.getResult().get("rows"));
                                        status.setText(
                                                rows.isEmpty()
                                                        ? R.string.oc3_no_people
                                                        : R.string.oc3_chat_help);
                                        for (Map<String, Object> person : rows) {
                                            com.google.android.material.button.MaterialButton row =
                                                    SocialSurface.quiet(
                                                            activity,
                                                            String.valueOf(
                                                                    person.get("displayName")),
                                                            () -> {});
                                            row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                                            row.setIconResource(R.drawable.ic_nav_chat);
                                            results.addView(
                                                    row, new LinearLayout.LayoutParams(-1, -2));
                                            row.setOnClickListener(
                                                    v ->
                                                            start(
                                                                    activity,
                                                                    String.valueOf(
                                                                            person.get("uid")),
                                                                    row,
                                                                    dialog::dismiss));
                                        }
                                    });
                };
        dialog.setOnShowListener(
                d -> {
                    dialog.getButton(-1).setOnClickListener(v -> search.run());
                    search.run();
                });
        dialog.show();
    }
}
