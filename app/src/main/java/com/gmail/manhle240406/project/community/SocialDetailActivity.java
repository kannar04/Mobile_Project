package com.gmail.manhle240406.project.community;

import static com.gmail.manhle240406.project.community.SocialRepository.*;
import static com.gmail.manhle240406.project.community.SocialViews.*;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.*;
import java.util.*;

public final class SocialDetailActivity extends AppCompatActivity {
  private final PendingReactionTracker reactionTracker = new PendingReactionTracker();
  private long reactionGeneration;
  private String consumedSocial, consumedReport;
  private AuthorLookup authors;
  private String authorLabel;
  private ImageView authorAvatar;
  private com.gmail.manhle240406.project.media.MediaGrid gallery;
  private AdoptionPostCard adoptionCard;
  private DailyViewModel model;
  private LinearLayout col, comments;
  private TextView story, status, byline, postContext;
  private com.google.android.material.button.MaterialButton send,
      like,
      moreComments,
      retry,
      reconcile;
  private TextInputEditText reply;
  private androidx.appcompat.app.AlertDialog dialog;
  private ListenerRegistration detailListener, commentListener;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    EdgeToEdge.enable(this);
    model = new ViewModelProvider(this).get(DailyViewModel.class);
    if (model.lost) {
      finish();
      return;
    }
    authors = new AuthorLookup(model.sessions);
    if (model.postId == null) model.identify(getIntent().getStringExtra("post"));
    LinearLayout outer = SocialSurface.page(this);
    SocialSurface.header(this, outer, R.string.ui_comments, this::finish);
    col = SocialSurface.content(this, outer);
    LinearLayout authorRow = new LinearLayout(this);
    authorRow.setGravity(Gravity.CENTER_VERTICAL);
    authorAvatar = new ImageView(this);
    authorAvatar.setId(R.id.post_author_avatar);
    authorRow.addView(authorAvatar, new LinearLayout.LayoutParams(dp(this, 48), dp(this, 48)));
    byline = text(this, getString(R.string.social_member), true);
    authorRow.addView(byline, new LinearLayout.LayoutParams(0, -2, 1));
    byline.setOnClickListener(
        v -> {
          if (model.projection != null)
            startActivity(
                new Intent(this, com.gmail.manhle240406.project.profile.PublicProfileActivity.class)
                    .putExtra("uid", (String) model.projection.get("authorUid")));
        });
    authorRow.addView(SocialSurface.more(this, this::postMenu));
    col.addView(authorRow);
    postContext = SocialSurface.muted(this, "");
    col.addView(postContext);
    story = text(this, "", false);
    story.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
    col.addView(story);
    gallery = new com.gmail.manhle240406.project.media.MediaGrid(this);
    col.addView(gallery);
    adoptionCard = new AdoptionPostCard(this);
    col.addView(adoptionCard);
    status = SocialSurface.muted(this, "");
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    col.addView(status);
    like =
        SocialSurface.quiet(
            this,
            getString(R.string.ui_like),
            () -> {
              CommunityOutbox.Entry queued =
                  ((AniloveApplication) getApplication())
                      .communityOutbox()
                      .pendingLike(model.postId);
              boolean desired =
                  queued != null
                      ? !Boolean.TRUE.equals(queued.command.input.get("active"))
                      : model.like == null || !Boolean.TRUE.equals(model.like.get("active"));
              if (model.projection == null && desired) return;
              model.send(
                  new SocialCommand(
                      "set_like",
                      model.postId,
                      null,
                      number(model.projection, "contentVersion"),
                      0,
                      number(model.like, "likeVersion"),
                      map("active", desired),
                      UUID.randomUUID().toString()));
            });
    like.setIconResource(R.drawable.ic_nav_favorite);
    like.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);
    like.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
    col.addView(like);
    if (com.gmail.manhle240406.project.BuildConfig.DEBUG)
      col.addView(
          SocialSurface.quiet(
              this,
              getString(R.string.flow_share),
              () -> DemoSharing.open(this, "p", model.postId)));
    SocialSurface.gap(col, 20);
    col.addView(text(this, getString(R.string.ui_comments), true));
    comments = column(this);
    comments.setId(R.id.social_comments_list);
    comments.setPadding(0, 0, 0, 0);
    col.addView(comments);
    moreComments =
        SocialSurface.quiet(
            this, getString(R.string.social_more_comments), () -> loadComments(true));
    col.addView(moreComments);
    retry =
        SocialSurface.quiet(
            this,
            getString(R.string.social_retry),
            () -> {
              if (model.pending != null || model.restored) model.retry();
              else load();
            });
    col.addView(retry);
    reconcile = SocialSurface.quiet(this, getString(R.string.social_reconcile), model::reconcile);
    col.addView(reconcile);
    LinearLayout dock = column(this);
    dock.setOrientation(LinearLayout.HORIZONTAL);
    dock.setGravity(Gravity.BOTTOM);
    dock.setPadding(dp(this, 16), dp(this, 4), dp(this, 16), dp(this, 8));
    outer.addView(dock, new LinearLayout.LayoutParams(-1, -2));
    reply = field(this, dock, getString(R.string.ui_comment_hint), 1);
    reply.setId(R.id.social_comment_input);
    reply.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    reply.setMaxLines(3);
    ((android.view.View) reply.getParent().getParent())
        .setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    reply.setText(model.body);
    reply.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void afterTextChanged(android.text.Editable e) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            model.body = s.toString();
            if (send != null)
              send.setEnabled(
                  !model.busy
                      && model.pending == null
                      && !model.restored
                      && model.projection != null
                      && !model.body.trim().isEmpty());
          }
        });
    send =
        primaryButton(
            this,
            getString(R.string.ui_send),
            () -> {
              if (!text(model.body.trim(), 500, 2000)) {
                reply.setError(getString(R.string.social_comment_invalid));
                return;
              }
              model.send(
                  new SocialCommand(
                      "add_comment",
                      model.postId,
                      UUID.randomUUID().toString(),
                      number(model.projection, "contentVersion"),
                      0,
                      0,
                      map("body", model.body.trim()),
                      UUID.randomUUID().toString()));
            });
    send.setId(R.id.social_send_action);
    LinearLayout.LayoutParams sendLayout = new LinearLayout.LayoutParams(-2, -2);
    sendLayout.leftMargin = dp(this, 8);
    send.setLayoutParams(sendLayout);
    dock.addView(send);
    model.changes.observe(this, x -> render());
    ((AniloveApplication) getApplication())
        .communityOutbox()
        .entries()
        .observe(
            this,
            rows -> {
              if (reactionTracker.completedContributions(rows).contains(model.postId)
                  && "queued".equals(model.notice)) model.notice = "";
              if (reactionTracker.completedPosts(rows).contains(model.postId)) refreshReaction();
              render();
            });
    load();
    privateView(outer);
    if (model.editingCommentId != null) outer.post(this::editComment);
  }

  private void load() {
    model.read(
        "communityFeed/" + model.postId,
        p -> {
          model.projection = p != null && SocialSchema.feed(p, model.postId) ? p : null;
          if (model.projection != null) {
            authorLabel = getString(R.string.social_member);
            authorAvatar.setTag(new Object());
            authorAvatar.setImageResource(R.drawable.profile_avatar_default);
            authors
                .get((String) p.get("authorUid"))
                .addOnSuccessListener(
                    alias -> {
                      if (!model.current() || isDestroyed()) return;
                      authorLabel =
                          alias == null
                              ? getString(R.string.social_member)
                              : (String) alias.get("displayName");
                      if (alias != null) AuthorIdentity.apply(alias, byline, authorAvatar);
                      model.changed();
                    });
            loadComments(false);
          } else {
            model.rows.clear();
            comments.removeAllViews();
          }
          refreshReaction();
        });
  }

  private void refreshReaction() {
    if (!model.current()) return;
    long generation = ++reactionGeneration;
    com.google.android.gms.tasks.Task<DocumentSnapshot>
        own = model.social.read("communityPosts/" + model.postId + "/likes/" + model.ticket.uid),
        count = model.social.read("communityCounts/" + model.postId);
    com.google.android.gms.tasks.Tasks.whenAllComplete(own, count)
        .addOnCompleteListener(
            done -> {
              if (!model.current() || isDestroyed() || generation != reactionGeneration) return;
              if (own.isSuccessful()) model.like = own.getResult().getData();
              if (count.isSuccessful()) model.count = count.getResult().getData();
              model.changed();
            });
  }

  private void loadComments(boolean next) {
    if (model.lost || model.projection == null || next && model.cursor == null) return;
    model.commentStart = next ? model.cursor : null;
    attachComments();
  }

  private void attachComments() {
    if (commentListener != null) commentListener.remove();
    if (!model.current() || model.projection == null) return;
    commentListener =
        model.social.commentPage(
            model.postId,
            model.commentStart,
            (snapshot, error) -> {
              if (!model.current() || isDestroyed()) return;
              if (error != null) {
                model.rows.clear();
                model.notice = "unavailable";
                model.changed();
                return;
              }
              if (snapshot == null || snapshot.getMetadata().isFromCache()) return;
              List<DocumentSnapshot> docs = snapshot.getDocuments();
              model.rows = new ArrayList<>();
              for (DocumentSnapshot d : docs)
                if (SocialSchema.comment(d.getData(), model.postId, d.getId())) {
                  model.rows.add(d);
                  if (model.ticket.uid.equals(d.get("authorUid")))
                    model.ownComments.put(d.getId(), d.getData());
                }
              while (model.ownComments.size() > 20)
                model.ownComments.remove(model.ownComments.keySet().iterator().next());
              model.cursor = docs.size() == 20 ? docs.get(19) : null;
              refreshReaction();
              model.changed();
            });
  }

  private void postMenu() {
    if (model.projection == null) return;
    boolean own = model.ticket.uid.equals(model.projection.get("authorUid"));
    String[] choices =
        own
            ? new String[] {
              getString(R.string.social_edit),
              getString(R.string.community_hide),
              getString(R.string.social_delete),
              getString(R.string.report_title)
            }
            : new String[] {getString(R.string.report_title)};
    dialog =
        new MaterialAlertDialogBuilder(this)
            .setItems(
                choices,
                (d, w) -> {
                  if (!own || w == 3) {
                    ReportSheet.target(
                            "post",
                            model.postId,
                            null,
                            number(model.projection, "contentVersion"),
                            null)
                        .show(getSupportFragmentManager(), "report");
                    return;
                  }
                  if (w == 0) {
                    startActivity(
                        new Intent(this, SocialComposerActivity.class)
                            .putExtra("post", model.postId));
                    return;
                  }
                  model.read(
                      "communityPosts/" + model.postId,
                      r -> {
                        model.root = r;
                        if (r == null || isDestroyed() || isFinishing()) return;
                        Runnable action =
                            () ->
                                model.send(
                                    new SocialCommand(
                                        w == 1 ? "hide" : "archive",
                                        model.postId,
                                        null,
                                        number(r, "contentVersion"),
                                        number(r, "controlVersion"),
                                        0,
                                        Collections.emptyMap(),
                                        UUID.randomUUID().toString()));
                        if (w == 2)
                          dialog =
                              new MaterialAlertDialogBuilder(this)
                                  .setMessage(R.string.social_delete_help)
                                  .setPositiveButton(R.string.social_delete, (a, b) -> action.run())
                                  .setNegativeButton(android.R.string.cancel, null)
                                  .show();
                        else action.run();
                      });
                })
            .show();
  }

  private void commentMenu(DocumentSnapshot row) {
    String cid = row.getId();
    boolean own = model.ticket.uid.equals(row.getString("authorUid"));
    dialog =
        new MaterialAlertDialogBuilder(this)
            .setItems(
                own
                    ? new String[] {
                      getString(R.string.social_edit),
                      getString(R.string.social_withdraw),
                      getString(R.string.report_title)
                    }
                    : new String[] {getString(R.string.report_title)},
                (d, w) -> {
                  if (!own || w == 2) {
                    ReportSheet.target(
                            "comment",
                            cid,
                            model.postId,
                            number(row.getData(), "commentVersion"),
                            null)
                        .show(getSupportFragmentManager(), "report");
                    return;
                  }
                  if (w == 1) {
                    model.send(
                        new SocialCommand(
                            "withdraw_comment",
                            model.postId,
                            cid,
                            number(row.getData(), "sourceContentVersion"),
                            0,
                            number(row.getData(), "commentVersion"),
                            Collections.emptyMap(),
                            UUID.randomUUID().toString()));
                    return;
                  }
                  model.editingCommentId = cid;
                  model.editingCommentVersion = number(row.getData(), "commentVersion");
                  model.commentEditText = row.getString("body");
                  editComment();
                })
            .show();
  }

  private void editComment() {
    if (model.editingCommentId == null || isDestroyed()) return;
    LinearLayout fields = column(this);
    TextInputEditText edit = field(this, fields, getString(R.string.social_comment), 3);
    edit.setText(model.commentEditText);
    edit.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

          public void afterTextChanged(android.text.Editable e) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            model.commentEditText = s.toString();
          }
        });
    dialog =
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.social_edit)
            .setView(fields)
            .setPositiveButton(R.string.social_send_comment, null)
            .setNegativeButton(
                android.R.string.cancel,
                (d, w) -> {
                  model.editingCommentId = null;
                  model.commentEditText = "";
                })
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    button -> {
                      String candidate = model.commentEditText.trim();
                      if (!text(candidate, 500, 2000)) {
                        edit.setError(getString(R.string.social_comment_invalid));
                        edit.requestFocus();
                        return;
                      }
                      model.send(
                          new SocialCommand(
                              "edit_comment",
                              model.postId,
                              model.editingCommentId,
                              number(model.projection, "contentVersion"),
                              0,
                              model.editingCommentVersion,
                              map("body", candidate),
                              UUID.randomUUID().toString()));
                      model.editingCommentId = null;
                      model.commentEditText = "";
                      dialog.dismiss();
                    }));
    dialog.show();
  }

  private void render() {
    if (adoptionCard != null) adoptionCard.bind(model.projection);
    if (gallery != null)
      gallery.bind(model.projection == null ? null : model.projection.get("mediaAssetIds"));
    if (model.lost) {
      if (dialog != null) dialog.dismiss();
      finish();
      return;
    }
    if (model.lastCommittedReport != null && !model.lastCommittedReport.equals(consumedReport)) {
      consumedReport = model.lastCommittedReport;
      com.google.android.material.snackbar.Snackbar.make(
              col,
              R.string.report_received,
              com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
          .setAction(
              R.string.report_mine, v -> startActivity(new Intent(this, MyReportsActivity.class)))
          .show();
    }
    byline.setText(authorLabel == null ? getString(R.string.social_member) : authorLabel);
    postContext.setText(
        model.projection == null
            ? ""
            : getString(
                    "everyone".equals(model.projection.get("audience"))
                        ? R.string.community_everyone
                        : R.string.social_audience)
                + (model.projection.get("editedAt") == null
                    ? ""
                    : " \u00b7 " + getString(R.string.social_edited)));
    SocialSurface.hideEmpty(postContext);
    story.setText(
        model.projection == null
            ? getString(R.string.social_removed)
            : (String) model.projection.get("body"));
    status.setText(
        (model.notice.isEmpty() ? "" : DailyCopy.notice(this, model.notice))
            + (model.count == null
                ? ""
                : " \u00b7 " + getString(R.string.social_likes, number(model.count, "likeCount"))));
    SocialSurface.hideEmpty(status);
    boolean locked = model.busy || model.pending != null || model.restored;
    reply.setEnabled(!locked && model.projection != null);
    send.setEnabled(!locked && model.projection != null && !model.body.trim().isEmpty());
    java.util.List<CommunityOutbox.Entry> pendingRows =
        ((AniloveApplication) getApplication()).communityOutbox().entries().getValue();
    CommunityOutbox.Entry pendingLike = null;
    if (pendingRows != null)
      for (CommunityOutbox.Entry pendingRow : pendingRows)
        if (model.postId.equals(pendingRow.command.postId)
            && "set_like".equals(pendingRow.command.command)) pendingLike = pendingRow;
    if (pendingLike != null) {
      like.setSelected(Boolean.TRUE.equals(pendingLike.command.input.get("active")));
      like.setContentDescription(getString(R.string.community_pending));
    }
    like.setText(
        Boolean.TRUE.equals(model.like == null ? null : model.like.get("active"))
            ? R.string.ui_unlike
            : R.string.ui_like);
    like.setEnabled(
        !locked
            && (model.projection != null
                || Boolean.TRUE.equals(model.like == null ? null : model.like.get("active"))));
    if (pendingLike != null) {
      boolean active = Boolean.TRUE.equals(pendingLike.command.input.get("active"));
      like.setText(active ? R.string.ui_unlike : R.string.ui_like);
      like.setIconResource(R.drawable.ic_social_pending);
      like.setEnabled(!locked && pendingLike.replaceable());
    } else {
      like.setIconResource(R.drawable.ic_nav_favorite);
      like.setSelected(Boolean.TRUE.equals(model.like == null ? null : model.like.get("active")));
      like.setContentDescription(like.getText());
    }
    like.setContentDescription(
        getString(like.isSelected() ? R.string.ui_unlike : R.string.ui_like));
    like.setText("");
    like.setIconResource(
        like.isSelected() ? R.drawable.ic_nav_favorite : R.drawable.ic_flow_favorite_outline);
    like.setIconTint(
        android.content.res.ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(
                this, like.isSelected() ? R.color.anilove_primary : R.color.anilove_text_muted)));
    moreComments.setVisibility(model.cursor == null ? View.GONE : View.VISIBLE);
    retry.setVisibility(
        model.pending != null || "unavailable".equals(model.notice) ? View.VISIBLE : View.GONE);
    retry.setEnabled(!model.busy);
    reconcile.setVisibility(model.pending != null || model.restored ? View.VISIBLE : View.GONE);
    reconcile.setEnabled(!model.busy);
    comments.removeAllViews();
    if (model.projection == null)
      for (Map.Entry<String, Map<String, Object>> entry : model.ownComments.entrySet())
        comments.addView(
            button(
                this,
                getString(R.string.social_withdraw),
                () ->
                    model.read(
                        "communityPosts/" + model.postId + "/comments/" + entry.getKey(),
                        c -> {
                          if (c == null || !"visible".equals(c.get("state"))) return;
                          model.send(
                              new SocialCommand(
                                  "withdraw_comment",
                                  model.postId,
                                  entry.getKey(),
                                  number(c, "sourceContentVersion"),
                                  0,
                                  number(c, "commentVersion"),
                                  Collections.emptyMap(),
                                  UUID.randomUUID().toString()));
                        })));
    if (model.projection != null && model.rows.isEmpty())
      comments.addView(SocialSurface.muted(this, getString(R.string.ui_no_comments)));
    for (DocumentSnapshot row : model.rows) {
      LinearLayout entry = column(this);
      entry.setPadding(0, dp(this, 8), 0, dp(this, 16));
      LinearLayout top = new LinearLayout(this);
      top.setGravity(Gravity.CENTER_VERTICAL);
      TextView member = text(this, getString(R.string.social_member), true);
      top.addView(member, new LinearLayout.LayoutParams(0, -2, 1));
      top.addView(SocialSurface.more(this, () -> commentMenu(row)));
      entry.addView(top);
      entry.addView(text(this, row.getString("body"), false));
      if (number(row.getData(), "sourceContentVersion")
          < number(model.projection, "contentVersion"))
        entry.addView(SocialSurface.muted(this, getString(R.string.social_source_edited)));
      comments.addView(entry);
    }
    if (pendingRows != null)
      for (CommunityOutbox.Entry pendingRow : pendingRows)
        if (model.postId.equals(pendingRow.command.postId)
            && "add_comment".equals(pendingRow.command.command)) {
          comments.addView(text(this, (String) pendingRow.command.input.get("body"), false));
          comments.addView(
              SocialSurface.muted(
                  this,
                  getString(
                      pendingRow.review()
                          ? R.string.community_review
                          : R.string.community_pending)));
        }
    if ("queued".equals(model.notice) && !reply.getText().toString().equals(model.body))
      reply.setText(model.body);
    if (model.lastCommittedSocial != null && !model.lastCommittedSocial.equals(consumedSocial)) {
      consumedSocial = model.lastCommittedSocial;
      model.body = "";
      reply.setText("");
      load();
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (model != null && !model.lost)
      detailListener =
          FirebaseFirestore.getInstance()
              .document("communityFeed/" + model.postId)
              .addSnapshotListener(
                  (d, e) -> {
                    if (!model.current()) return;
                    if (e != null) {
                      model.projection = null;
                      model.rows.clear();
                      model.changed();
                    } else if (d != null && !d.getMetadata().isFromCache()) {
                      boolean first = model.projection == null;
                      model.projection =
                          d.exists() && SocialSchema.feed(d.getData(), model.postId)
                              ? d.getData()
                              : null;
                      if (model.projection == null) model.rows.clear();
                      else if (first || commentListener == null) attachComments();
                      model.changed();
                    }
                  });
  }

  @Override
  protected void onStop() {
    if (detailListener != null) {
      detailListener.remove();
      detailListener = null;
    }
    if (commentListener != null) {
      commentListener.remove();
      commentListener = null;
    }
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    if (dialog != null) dialog.dismiss();
    if (authors != null) authors.close();
    super.onDestroy();
  }
}
