package com.gmail.manhle240406.project.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.*;
import com.gmail.manhle240406.project.media.*;

import java.util.*;

public final class PublicProfileActivity extends AppCompatActivity {
    private LinearLayout content, posts;
    private String uid, after;
    private boolean busy;
    private View next;
    private int selected;
    private final List<SocialPostCard> cards = new ArrayList<>();
    private AuthorLookup authors;
    private DailyViewModel actions;
    private Map<String, Object> author;
    private Spinner filter;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        uid = getIntent().getStringExtra("uid");
        actions = new ViewModelProvider(this).get(DailyViewModel.class);
        authors = new AuthorLookup(((AniloveApplication) getApplication()).foundation().sessions);
        LinearLayout root = SocialSurface.page(this);
        SocialSurface.header(this, root, R.string.oc2_public_profile, this::finish);
        content = SocialSurface.content(this, root);
        load();
    }

    private void filter() {
        for (SocialPostCard card : cards) {
            boolean adoption = Boolean.TRUE.equals(card.getTag(R.id.post_type_picker));
            card.setVisibility(
                    selected == 0 || adoption == (selected == 2) ? View.VISIBLE : View.GONE);
        }
    }

    private void load() {
        if (busy || uid == null || !actions.current()) return;
        busy = true;
        if (next != null) content.removeView(next);
        TextView status = SocialSurface.muted(this, getString(R.string.profile_loading));
        content.addView(status);
        ManagedMediaClient.call(
                        "publicProfilePage", ManagedMediaClient.map("uid", uid, "after", after))
                .addOnCompleteListener(
                        t -> {
                            if (isDestroyed() || isFinishing() || !actions.current()) return;
                            busy = false;
                            content.removeView(status);
                            if (!t.isSuccessful()) {
                                status.setText(R.string.social_unavailable);
                                content.addView(status);
                                content.addView(
                                        SocialSurface.quiet(
                                                this,
                                                getString(R.string.social_retry),
                                                this::load));
                                return;
                            }
                            Map<String, Object> result = t.getResult();
                            if (author == null) {
                                author = (Map<String, Object>) result.get("profile");
                                ImageView avatar = new ImageView(this);
                                avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                content.addView(
                                        avatar,
                                        new LinearLayout.LayoutParams(
                                                SocialViews.dp(this, 72),
                                                SocialViews.dp(this, 72)));
                                TextView name = SocialViews.text(this, "", true);
                                content.addView(name);
                                AuthorIdentity.apply(author, name, avatar);
                                content.addView(
                                        SocialSurface.muted(
                                                this,
                                                String.valueOf(author.getOrDefault("bio", ""))));
                                if (!uid.equals(actions.ticket.uid)) {
                                    com.google.android.material.button.MaterialButton message =
                                            SocialSurface.quiet(
                                                    this,
                                                    getString(R.string.oc3_message),
                                                    () -> {});
                                    message.setIconResource(R.drawable.ic_nav_chat);
                                    message.setOnClickListener(
                                            v -> DirectChats.start(this, uid, message));
                                    content.addView(message);
                                }
                                filter = new Spinner(this);
                                filter.setId(R.id.oc3_post_filter);
                                filter.setMinimumHeight(SocialViews.dp(this, 48));
                                filter.setAdapter(
                                        new ArrayAdapter<>(
                                                this,
                                                android.R.layout.simple_spinner_dropdown_item,
                                                new String[] {
                                                    getString(R.string.oc3_all_posts),
                                                    getString(R.string.oc3_social_posts),
                                                    getString(R.string.oc3_adoption_posts)
                                                }));
                                content.addView(filter);
                                filter.setOnItemSelectedListener(
                                        new AdapterView.OnItemSelectedListener() {
                                            public void onItemSelected(
                                                    AdapterView<?> p, View v, int n, long id) {
                                                selected = n;
                                                filter();
                                            }

                                            public void onNothingSelected(AdapterView<?> p) {}
                                        });
                                posts = new LinearLayout(this);
                                posts.setOrientation(LinearLayout.VERTICAL);
                                content.addView(posts);
                            }
                            for (Map<String, Object> post :
                                    com.gmail.manhle240406.project.adoption.AdoptionFlowModel.rows(
                                            result.get("posts"))) {
                                SocialPostCard card = new SocialPostCard(this);
                                card.setTag(
                                        R.id.post_type_picker, "adoption".equals(post.get("type")));
                                String id = String.valueOf(post.get("postId"));
                                card.bind(
                                        post,
                                        id,
                                        authors,
                                        author,
                                        actions,
                                        false,
                                        () ->
                                                startActivity(
                                                        new Intent(this, SocialDetailActivity.class)
                                                                .putExtra("post", id)));
                                posts.addView(card);
                                cards.add(card);
                            }
                            while (cards.size() > 60) {
                                SocialPostCard oldest = cards.remove(0);
                                posts.removeView(oldest);
                            }
                            filter();
                            after = (String) result.get("next");
                            if (after != null) {
                                next =
                                        SocialSurface.quiet(
                                                this, getString(R.string.social_more), this::load);
                                content.addView(next);
                            }
                        });
    }

    @Override
    protected void onDestroy() {
        if (authors != null) authors.close();
        super.onDestroy();
    }
}
