package com.gmail.manhle240406.project.community;

import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.foundation.*;
import com.google.firebase.firestore.*;

/** Own minimized author notices only. No reporter identity or privileged workbench. */
public final class ModerationNoticesActivity extends AppCompatActivity {
    private SessionGateway sessions;
    private SessionEpoch.Ticket ticket;
    private LinearLayout content;
    private long generation;
    private final Runnable invalidation =
            () -> {
                generation++;
                if (content != null) content.removeAllViews();
                finish();
            };

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        androidx.activity.EdgeToEdge.enable(this);
        LinearLayout root = SocialSurface.page(this);
        SocialSurface.header(this, root, R.string.community_notices, this::finish);
        content = SocialSurface.content(this, root);
        sessions = ((AniloveApplication) getApplication()).foundation().sessions;
        ticket = sessions == null ? null : sessions.capture();
        if (ticket == null || ticket.uid == null) {
            finish();
            return;
        }
        sessions.onInvalidated(invalidation);
        load();
    }

    private void load() {
        long g = ++generation;
        content.removeAllViews();
        content.addView(SocialSurface.muted(this, getString(R.string.wiring_loading)));
        FirebaseFirestore.getInstance()
                .collection("users/" + ticket.uid + "/moderationNotices")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get(Source.SERVER)
                .addOnCompleteListener(
                        done -> {
                            if (g != generation || isDestroyed() || !sessions.current(ticket))
                                return;
                            content.removeAllViews();
                            if (!done.isSuccessful()) {
                                content.addView(
                                        SocialViews.button(
                                                this,
                                                getString(R.string.social_retry),
                                                this::load));
                                return;
                            }
                            if (done.getResult().isEmpty())
                                content.addView(
                                        SocialSurface.muted(
                                                this, getString(R.string.community_notices_empty)));
                            for (DocumentSnapshot row : done.getResult())
                                if (Long.valueOf(1).equals(row.getLong("schemaVersion"))
                                        && row.get("reason") instanceof String) {
                                    content.addView(
                                            SocialViews.text(
                                                    this,
                                                    getString(R.string.community_removed_notice),
                                                    true));
                                    content.addView(
                                            SocialViews.text(this, row.getString("reason"), false));
                                    content.addView(
                                            SocialSurface.muted(
                                                    this,
                                                    getString(R.string.community_reconsider)));
                                }
                        });
    }

    @Override
    protected void onDestroy() {
        generation++;
        if (sessions != null) sessions.removeInvalidation(invalidation);
        super.onDestroy();
    }
}
