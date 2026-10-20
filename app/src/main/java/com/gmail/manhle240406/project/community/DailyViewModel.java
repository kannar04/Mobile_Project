package com.gmail.manhle240406.project.community;

import android.app.Application;
import android.os.*;
import androidx.lifecycle.*;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;

/** Rotation memory for composer/detail/report; cold restoration contains identifiers only. */
public final class DailyViewModel extends AndroidViewModel {
  public final MutableLiveData<Long> changes = new MutableLiveData<>(0L);
  public final SocialRepository social;
  public final ReportRepository reports;
  public final SessionGateway sessions;
  public final SessionEpoch.Ticket ticket;
  public final SavedStateHandle saved;
  public boolean adoption;
  public Map<String, Object> selectedListing;
  public Map<String, Object> participation = new HashMap<>();
  public final Map<String, String> adoptionRequirements = new HashMap<>();
  public String body = "", reportText = "", postId, reportId, notice = "";
  public int illustration, category;
  public boolean busy, lost, restored, dirty, committed;
  public String lastCommittedReport, lastCommittedSocial, lastCommittedCommand;
  private SocialCommand localRetry;
  public SocialCommand pending;
  public ReportCommand pendingReport;
  public Map<String, Object> root, projection, count, like, report;
  public Map<String, Map<String, Object>> ownComments = new LinkedHashMap<>();
  public String editingCommentId, commentEditText = "";
  public long editingCommentVersion;
  public List<DocumentSnapshot> rows = new ArrayList<>();
  public DocumentSnapshot cursor, commentStart;
  private final Runnable invalidation = this::invalidate;
  private final Handler main = new Handler(Looper.getMainLooper());
  private long event, generation;

  public DailyViewModel(Application app, SavedStateHandle state) {
    super(app);
    saved = state;
    FirebaseEnvironment env = ((AniloveApplication) app).foundation();
    sessions = env.sessions;
    ticket = sessions == null ? null : sessions.capture();
    social =
        sessions == null ? null : new SocialRepository(FirebaseFirestore.getInstance(), sessions);
    reports =
        sessions == null ? null : new ReportRepository(FirebaseFirestore.getInstance(), sessions);
    postId = state.get("post_id");
    reportId = state.get("report_id");
    String owner = state.get("owner_uid");
    restored = state.contains("pending_op") && ticket != null && Objects.equals(owner, ticket.uid);
    if (sessions != null) sessions.onInvalidated(invalidation);
    if (ticket == null || ticket.uid == null || owner != null && !owner.equals(ticket.uid))
      invalidate();
  }

  public void changed() {
    changes.setValue(++event);
  }

  public boolean current() {
    return !lost && sessions != null && sessions.current(ticket);
  }

  public void identify(String id) {
    postId = id;
    saved.set("post_id", id);
    saved.set("owner_uid", ticket.uid);
  }

  public void read(String path, java.util.function.Consumer<Map<String, Object>> result) {
    if (!current()) return;
    long g = generation;
    social
        .read(path)
        .addOnCompleteListener(
            t -> {
              if (g != generation || !current()) return;
              if (t.isSuccessful()) {
                result.accept(t.getResult().getData());
              } else notice = "unavailable";
              changed();
            });
  }

  public void send(SocialCommand command) {
    if (java.util.Arrays.asList("set_like", "add_comment").contains(command.command)
        || command.command.equals("publish")
            && command.contentVersion == 0
            && command.input.containsKey("body")
            && !command.input.containsKey("mediaAssetIds")
            && !"adoption".equals(command.input.get("type"))) {
      if (busy || !current() || restored || pending != null || pendingReport != null) return;
      busy = true;
      localRetry = null;
      notice = "sending";
      changed();
      long g = generation;
      ((AniloveApplication) getApplication())
          .communityOutbox()
          .accept(ticket, command)
          .addOnCompleteListener(
              done -> {
                if (g != generation || !current()) return;
                busy = false;
                localRetry = done.isSuccessful() ? null : command;
                notice = done.isSuccessful() ? "queued" : "local_failed";
                if (done.isSuccessful() && command.command.equals("add_comment")) body = "";
                changed();
              });
      return;
    }
    if (busy || lost || restored || pending != null && pending != command || pendingReport != null)
      return;
    pending = command;
    identify(command.postId);
    saved.set("pending_op", command.operationId);
    saved.set("pending_kind", "social");
    execute(social.execute(ticket, command), command.operationId);
  }

  public void sendReport(ReportCommand command) {
    if (busy
        || lost
        || restored
        || pendingReport != null && pendingReport != command
        || pending != null) return;
    pendingReport = command;
    reportId = command.reportId;
    saved.set("report_id", reportId);
    saved.set("owner_uid", ticket.uid);
    saved.set("pending_op", command.operationId);
    saved.set("pending_kind", "report");
    execute(reports.execute(ticket, command), command.operationId);
  }

  private void execute(Task<Map<String, Object>> task, String op) {
    boolean reporting = pendingReport != null;
    busy = true;
    committed = false;
    notice = "sending";
    long g = generation;
    changed();
    Runnable timeout =
        () -> {
          if (current() && g == generation && busy) {
            notice = "unknown";
            changed();
          }
        };
    main.postDelayed(timeout, 15000);
    task.addOnCompleteListener(
        t -> {
          main.removeCallbacks(timeout);
          if (!current() || g != generation) return;
          busy = false;
          if (t.isSuccessful()) {
            Map<String, Object> r = t.getResult();
            Object existing = r.get("existingReportId");
            if (existing != null) {
              reportId = (String) existing;
              notice = "duplicate";
            } else {
              notice = "committed";
              committed = true;
              dirty = false;
            }
            if (reporting) lastCommittedReport = op;
            else {
              lastCommittedSocial = op;
              lastCommittedCommand = pending == null ? null : pending.command;
            }
            pending = null;
            pendingReport = null;
            saved.remove("pending_op");
            saved.remove("pending_kind");
          } else {
            String message = t.getException() == null ? "" : t.getException().getMessage();
            notice =
                message != null && message.contains("conflict")
                    ? "conflict"
                    : message != null && message.contains("rate") ? "rate" : "unavailable";
          }
          changed();
        });
  }

  public void reconcile() {
    if (!current() || busy) return;
    String op = saved.get("pending_op");
    String kind = saved.get("pending_kind");
    if (op == null) return;
    busy = true;
    long g = generation;
    changed();
    social
        .read(
            "users/"
                + ticket.uid
                + "/"
                + ("report".equals(kind) ? "reportOperations/" : "communityOperations/")
                + op)
        .addOnCompleteListener(
            t -> {
              if (!current() || g != generation) return;
              busy = false;
              if (t.isSuccessful() && t.getResult().exists()) {
                Map<String, Object> r = t.getResult().getData();
                boolean schema =
                    "report".equals(kind)
                        ? reportId != null
                            && SocialSchema.reportReceipt(r, ticket.uid, op, reportId)
                        : postId != null && SocialSchema.receipt(r, ticket.uid, op, postId);
                boolean match =
                    schema
                        && (restored
                            || pending != null && pending.matches(r)
                            || pendingReport != null && pendingReport.matches(r));
                if (match) {
                  if ("report".equals(kind)) lastCommittedReport = op;
                  else {
                    lastCommittedSocial = op;
                    lastCommittedCommand = (String) r.get("command");
                  }
                  restored = false;
                  committed = true;
                  notice = "committed";
                  pending = null;
                  pendingReport = null;
                  saved.remove("pending_op");
                  saved.remove("pending_kind");
                } else notice = "conflict";
              } else if (t.isSuccessful()) {
                notice = "not_committed";
              } else notice = "unavailable";
              changed();
            });
  }

  public void abandonCold() {
    if (!restored || busy || !notice.equals("not_committed")) return;
    restored = false;
    saved.remove("pending_op");
    saved.remove("pending_kind");
    notice = "lost_text";
    changed();
  }

  public void clearKnownFailure() {
    if (busy || restored || !notice.equals("conflict") && !notice.equals("not_committed")) return;
    pending = null;
    pendingReport = null;
    saved.remove("pending_op");
    saved.remove("pending_kind");
    notice = "";
    changed();
  }

  public void retry() {
    if (localRetry != null) {
      send(localRetry);
      return;
    }
    if (pending != null) send(pending);
    else if (pendingReport != null) sendReport(pendingReport);
    else reconcile();
  }

  private void invalidate() {
    generation++;
    main.removeCallbacksAndMessages(null);
    lastCommittedReport = null;
    lastCommittedSocial = null;
    body = "";
    reportText = "";
    participation.clear();
    adoptionRequirements.clear();
    root = null;
    projection = null;
    count = null;
    like = null;
    report = null;
    ownComments.clear();
    editingCommentId = null;
    commentEditText = "";
    rows.clear();
    pending = null;
    localRetry = null;
    pendingReport = null;
    busy = false;
    lost = true;
    for (String k : new ArrayList<>(saved.keys())) saved.remove(k);
    changed();
  }

  @Override
  protected void onCleared() {
    invalidate();
    if (sessions != null) sessions.removeInvalidation(invalidation);
  }
}
