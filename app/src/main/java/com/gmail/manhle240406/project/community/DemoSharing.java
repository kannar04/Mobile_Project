package com.gmail.manhle240406.project.community;

import android.app.Activity;
import android.content.*;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.adoption.*;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.FirebaseFirestore;

public final class DemoSharing {
    private DemoSharing() {}

    public static void open(Activity a, String kind, String id) {
        if (!BuildConfig.DEBUG || a.isFinishing() || a.isDestroyed()) return;
        final String payload;
        try {
            payload = DemoShareLinks.text(kind, id);
        } catch (IllegalArgumentException invalid) {
            return;
        }
        SessionGateway sessions = ((AniloveApplication) a.getApplication()).foundation().sessions;
        SessionEpoch.Ticket t = sessions == null ? null : sessions.capture();
        if (t == null) return;
        com.google.android.gms.tasks.Task<Boolean> eligible;
        if ("p".equals(kind) && t.uid == null)
            eligible =
                    FirebaseFirestore.getInstance()
                            .document("communityPublicFeed/" + id)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .continueWith(
                                    done ->
                                            done.isSuccessful()
                                                    && done.getResult().exists()
                                                    && PublicStorySchema.valid(
                                                            done.getResult().getData(), id));
        else if ("p".equals(kind))
            eligible =
                    new SocialRepository(FirebaseFirestore.getInstance(), sessions)
                            .read("communityFeed/" + id)
                            .continueWith(
                                    done ->
                                            done.isSuccessful()
                                                    && done.getResult().exists()
                                                    && SocialSchema.feed(
                                                            done.getResult().getData(), id));
        else {
            if (t.uid == null) return;
            ListingDiscoveryRepository reader =
                    new ListingDiscoveryRepository(FirebaseFirestore.getInstance(), sessions);
            eligible =
                    reader.detail(t, id)
                            .continueWith(
                                    done -> {
                                        try {
                                            return done.isSuccessful() && done.getResult().exists();
                                        } finally {
                                            reader.close();
                                        }
                                    });
        }
        eligible.addOnCompleteListener(
                done -> {
                    if (a.isDestroyed() || a.isFinishing() || !sessions.current(t)) return;
                    if (!done.isSuccessful() || !Boolean.TRUE.equals(done.getResult())) {
                        Snackbar.make(
                                        a.findViewById(android.R.id.content),
                                        R.string.demo_share_unavailable,
                                        Snackbar.LENGTH_LONG)
                                .show();
                        return;
                    }
                    new MaterialAlertDialogBuilder(a)
                            .setTitle(R.string.demo_share_title)
                            .setMessage(R.string.demo_share_help)
                            .setPositiveButton(
                                    R.string.demo_share_action,
                                    (dialog, which) -> {
                                        if (!sessions.current(t)) return;
                                        try {
                                            a.startActivity(
                                                    Intent.createChooser(
                                                            new Intent(Intent.ACTION_SEND)
                                                                    .setType("text/plain")
                                                                    .putExtra(
                                                                            Intent.EXTRA_TEXT,
                                                                            payload),
                                                            a.getString(
                                                                    R.string.demo_share_title)));
                                        } catch (ActivityNotFoundException unavailable) {
                                            Snackbar.make(
                                                            a.findViewById(android.R.id.content),
                                                            R.string.demo_share_unavailable,
                                                            Snackbar.LENGTH_LONG)
                                                    .show();
                                        }
                                    })
                            .setNeutralButton(
                                    R.string.demo_share_copy,
                                    (dialog, which) -> {
                                        if (sessions.current(t)) {
                                            ClipData copy =
                                                    ClipData.newPlainText(
                                                            a.getString(R.string.demo_share_title),
                                                            payload);
                                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                                android.os.PersistableBundle flags =
                                                        new android.os.PersistableBundle();
                                                flags.putBoolean(
                                                        ClipDescription.EXTRA_IS_SENSITIVE, true);
                                                copy.getDescription().setExtras(flags);
                                            }
                                            a.getSystemService(ClipboardManager.class)
                                                    .setPrimaryClip(copy);
                                        }
                                    })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
    }
}
