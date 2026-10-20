package com.gmail.manhle240406.project.community;

import static com.gmail.manhle240406.project.community.SocialRepository.*;
import static com.gmail.manhle240406.project.community.SocialViews.*;

import android.os.Bundle;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.gmail.manhle240406.project.R;
import com.google.firebase.firestore.*;
import java.util.*;

public final class MyReportsActivity extends AppCompatActivity {
  private DailyViewModel model;
  private LinearLayout col;
  private boolean loading;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    EdgeToEdge.enable(this);
    model = new ViewModelProvider(this).get(DailyViewModel.class);
    if (model.lost) {
      finish();
      return;
    }
    LinearLayout outer = column(this);
    install(this, outer);
    insets(outer);
    ScrollView scroll = new ScrollView(this);
    outer.addView(scroll);
    col = column(this);
    scroll.addView(col);
    model.changes.observe(
        this,
        x -> {
          if (model.lost) {
            finish();
            return;
          }
          if (model.committed) {
            model.committed = false;
            if (model.reportId != null && model.report != null) detail(model.reportId);
            else load(false);
          } else render();
        });
    load(false);
    privateView(outer);
  }

  private void load(boolean next) {
    if (loading) return;
    loading = true;
    model
        .reports
        .mine(model.ticket.uid, next ? model.cursor : null)
        .addOnCompleteListener(
            t -> {
              if (!model.current()) return;
              loading = false;
              if (t.isSuccessful()) {
                model.rows = new ArrayList<>();
                for (DocumentSnapshot row : t.getResult())
                  if (SocialSchema.status(row.getData(), row.getId())) model.rows.add(row);
                model.cursor = model.rows.size() == 20 ? model.rows.get(19) : null;
              } else model.notice = "unavailable";
              render();
            });
  }

  private void render() {
    if (model.report != null) {
      renderDetail();
      return;
    }
    col.removeAllViews();
    col.addView(text(this, getString(R.string.report_mine), true));
    col.addView(button(this, getString(R.string.social_close), this::finish));
    col.addView(text(this, DailyCopy.notice(this, model.notice), false));
    for (DocumentSnapshot row : model.rows) {
      col.addView(
          button(
              this,
              DailyCopy.reportStatus(this, row.get("status"))
                  + " · "
                  + getResources()
                      .getStringArray(R.array.report_reasons)[
                      ReportCommand.CATEGORIES.indexOf(row.get("category"))],
              () -> detail(row.getId())));
    }
    col.addView(button(this, getString(R.string.social_refresh), () -> load(false)));
    if (model.cursor != null)
      col.addView(button(this, getString(R.string.social_more), () -> load(true)));
  }

  private void detail(String id) {
    model.read(
        "reports/" + id,
        r -> {
          if (r != null && SocialSchema.reportRecord(r, model.ticket.uid)) {
            model.read(
                "users/" + model.ticket.uid + "/reportStatus/" + id,
                status -> {
                  Map<String, Object> copy = new HashMap<>(r);
                  if (status != null) {
                    copy.put("status", status.get("status"));
                    copy.put("reviewResponse", status.get("response"));
                  }
                  model.report = copy;
                  model.reportId = id;
                });
          } else model.notice = "unavailable";
        });
  }

  private void renderDetail() {
    Map<String, Object> r = model.report;
    col.removeAllViews();
    col.addView(text(this, DailyCopy.reportStatus(this, r.get("status")), true));
    col.addView(text(this, (String) r.get("statement"), false));
    if (r.get("reviewResponse") instanceof String
        && !((String) r.get("reviewResponse")).isBlank()) {
      col.addView(text(this, getString(R.string.permissions_report_response), true));
      col.addView(text(this, (String) r.get("reviewResponse"), false));
    }
    col.addView(text(this, getString(R.string.report_original_retained), false));
    col.addView(
        button(
            this,
            getString(R.string.report_append),
            () ->
                ReportSheet.target(
                        (String) r.get("targetKind"),
                        (String) r.get("targetId"),
                        (String) r.get("parentId"),
                        number(r, "observedVersion"),
                        model.reportId)
                    .show(getSupportFragmentManager(), "report")));
    col.addView(
        button(
            this,
            getString(R.string.report_withdraw),
            () ->
                model.sendReport(
                    new ReportCommand(
                        UUID.randomUUID().toString(),
                        model.reportId,
                        "withdraw",
                        (String) r.get("targetKind"),
                        (String) r.get("targetId"),
                        (String) r.get("parentId"),
                        number(r, "observedVersion"),
                        number(r, "lastReporterVersion"),
                        (String) r.get("category"),
                        getString(R.string.report_withdraw_statement),
                        null))));
    col.addView(
        button(
            this,
            getString(R.string.social_close),
            () -> {
              model.report = null;
              load(false);
            }));
  }
}
