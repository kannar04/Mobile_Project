package com.gmail.manhle240406.project.community;

import static com.gmail.manhle240406.project.pet.PetSchema.*;

import android.app.Application;
import android.os.*;
import androidx.lifecycle.*;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.foundation.*;
import com.google.firebase.firestore.*;
import java.util.*;

/** F31 working text stays in memory; only safe recovery identifiers enter SavedState. */
public final class CommunityViewModel extends AndroidViewModel {
  public final MutableLiveData<Long> changes = new MutableLiveData<>(0L);
  public String lastCommittedOperation;
  public String postId, body = "", kind = "posts", message = "";
  public int illustration;
  public long contentVersion, controlVersion;
  public boolean editing,
      busy,
      writePending,
      dirty,
      restored,
      postsChecked,
      operationsChecked,
      lost;
  public CommunityCommand pending;
  public CommunityPostRepository.Detail detail, conflict;
  public CommunityPostRepository.Page page;
  private final SavedStateHandle saved;
  private final SessionGateway sessions;
  private final CommunityPostRepository repository;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final Runnable invalidation;
  private SessionEpoch.Ticket ticket;
  private long change, generation;

  public CommunityViewModel(Application app, SavedStateHandle state) {
    super(app);
    saved = state;
    AniloveApplication a = (AniloveApplication) app;
    sessions = a.foundation().sessions;
    repository =
        sessions == null
            ? null
            : new CommunityPostRepository(FirebaseFirestore.getInstance(), sessions);
    ticket = sessions == null ? null : sessions.capture();
    invalidation =
        () -> {
          generation++;
          main.removeCallbacksAndMessages(null);
          clearMemory();
          lost = true;
          for (String k : recoveryKeys()) saved.remove(k);
          notifyChange();
        };
    if (sessions != null) sessions.onInvalidated(invalidation);
    String uid = saved.get("community_uid");
    if (current() && Objects.equals(uid, ticket.uid) && saved.get("community_operation") != null) {
      restored = true;
      postId = saved.get("community_post");
      Long cv = saved.get("community_content"), ctl = saved.get("community_control");
      contentVersion = cv == null ? 0 : cv;
      controlVersion = ctl == null ? 0 : ctl;
      message = "recovery";
    } else for (String k : recoveryKeys()) saved.remove(k);
    if (current()) list("posts", false);
    else message = "denied";
  }

  private static List<String> recoveryKeys() {
    return Arrays.asList(
        "community_uid",
        "community_operation",
        "community_post",
        "community_command",
        "community_content",
        "community_control");
  }

  private void clearMemory() {
    lastCommittedOperation = null;
    body = "";
    postId = null;
    illustration = 0;
    contentVersion = 0;
    controlVersion = 0;
    pending = null;
    detail = null;
    conflict = null;
    page = null;
    editing = false;
    busy = false;
    writePending = false;
    dirty = false;
    restored = false;
    postsChecked = false;
    operationsChecked = false;
  }

  private boolean current() {
    return ticket != null && ticket.uid != null && sessions != null && sessions.current(ticket);
  }

  private boolean current(SessionEpoch.Ticket t, long g) {
    return sessions != null && sessions.current(t) && g == generation;
  }

  public boolean locked() {
    return busy || writePending || pending != null || restored;
  }

  public void notifyChange() {
    changes.setValue(++change);
  }

  public void changed(String b, int key) {
    if (locked()) return;
    body = b;
    illustration = key;
    dirty = true;
  }

  public void newPost() {
    if (!current() || locked()) return;
    generation++;
    postId = UUID.randomUUID().toString();
    body = "";
    illustration = 0;
    contentVersion = 0;
    controlVersion = 0;
    detail = null;
    conflict = null;
    editing = true;
    dirty = false;
    notifyChange();
  }

  public void list(String target, boolean next) {
    if (!current() || busy || writePending || pending != null) return;
    DocumentSnapshot cursor =
        next && Objects.equals(kind, target) && page != null ? page.cursor : null;
    if (!next || !Objects.equals(kind, target)) page = null;
    busy = true;
    kind = target;
    notifyChange();
    SessionEpoch.Ticket t = ticket;
    long g = generation;
    repository
        .list(t, target, postId, cursor)
        .addOnSuccessListener(
            r -> {
              if (!current(t, g)) return;
              busy = false;
              if (r.value != null) {
                page = r.value;
                if ("posts".equals(target)) postsChecked = true;
                if ("operations".equals(target)) operationsChecked = true;
              } else message = code(r.code);
              notifyChange();
            });
  }

  public void load(String id) {
    if (!current() || locked()) return;
    busy = true;
    notifyChange();
    SessionEpoch.Ticket t = ticket;
    long g = generation;
    repository
        .load(t, id)
        .addOnSuccessListener(
            r -> {
              if (!current(t, g)) return;
              busy = false;
              if (r.value != null) {
                detail = r.value;
                postId = id;
                contentVersion = version(detail.root, "contentVersion");
                controlVersion = version(detail.root, "controlVersion");
                fill(detail);
                editing = true;
                dirty = false;
                kind = "posts";
              } else message = code(r.code);
              notifyChange();
            });
  }

  private void fill(CommunityPostRepository.Detail d) {
    body = (String) d.revision.get("body");
    illustration = CommunitySchema.ILLUSTRATIONS.indexOf(d.revision.get("bundledIllustrationKey"));
  }

  public void command(String type) {
    if (!current() || locked() || postId == null) return;
    Map<String, Object> input =
        "create".equals(type) || "edit".equals(type)
            ? CommunitySchema.fields(body.trim(), CommunitySchema.ILLUSTRATIONS.get(illustration))
            : Collections.emptyMap();
    try {
      pending =
          new CommunityCommand(
              type, postId, UUID.randomUUID().toString(), contentVersion, controlVersion, input);
    } catch (IllegalArgumentException e) {
      message = "invalid";
      notifyChange();
      return;
    }
    saved.set("community_uid", ticket.uid);
    saved.set("community_operation", pending.operationId);
    saved.set("community_post", postId);
    saved.set("community_command", type);
    saved.set("community_content", contentVersion);
    saved.set("community_control", controlVersion);
    retry();
  }

  public void retry() {
    if (!current() || busy || writePending || pending == null) return;
    CommunityCommand c = pending;
    SessionEpoch.Ticket t = ticket;
    long g = generation;
    writePending = true;
    message = "busy";
    notifyChange();
    main.postDelayed(
        () -> {
          if (current(t, g) && writePending && pending == c) {
            message = "unknown";
            notifyChange();
          }
        },
        15000);
    repository
        .execute(t, c)
        .addOnSuccessListener(
            r -> {
              if (!current(t, g) || pending != c) return;
              writePending = false;
              if (r.code == CommunityPostRepository.Code.COMMITTED) committed(r.value);
              else {
                message = code(r.code);
                if (r.code == CommunityPostRepository.Code.DENIED
                    || r.code == CommunityPostRepository.Code.UNKNOWN_SCHEMA) {
                  pending = null;
                  forgetOperation();
                }
                notifyChange();
              }
            });
  }

  private void forgetOperation() {
    for (String k : recoveryKeys()) saved.remove(k);
    restored = false;
  }

  private void committed(Map<String, Object> receipt) {
    lastCommittedOperation = (String) receipt.get("operationId");
    postId = (String) receipt.get("postId");
    contentVersion = version(nested(receipt, "result"), "contentVersion");
    controlVersion = version(nested(receipt, "result"), "controlVersion");
    pending = null;
    forgetOperation();
    message = "committed";
    dirty = false;
    load(postId);
  }

  public void reconcile() {
    if (!current() || busy || writePending) return;
    String operation = pending == null ? saved.get("community_operation") : pending.operationId;
    if (operation == null) return;
    busy = true;
    notifyChange();
    SessionEpoch.Ticket t = ticket;
    long g = generation;
    repository
        .receipt(t, operation)
        .addOnSuccessListener(
            r -> {
              if (!current(t, g)) return;
              busy = false;
              if (r.value != null) {
                boolean match =
                    pending != null
                        ? pending.matches(r.value)
                        : Objects.equals(saved.get("community_post"), r.value.get("postId"))
                            && Objects.equals(
                                saved.get("community_command"), r.value.get("command"))
                            && Objects.equals(
                                saved.get("community_content"),
                                nested(r.value, "base").get("contentVersion"))
                            && Objects.equals(
                                saved.get("community_control"),
                                nested(r.value, "base").get("controlVersion"));
                if (match) committed(r.value);
                else {
                  message = "conflict";
                  notifyChange();
                }
              } else {
                message = code(r.code);
                notifyChange();
              }
            });
  }

  public void compare() {
    if (!current() || busy || writePending || contentVersion < 1 || postId == null) return;
    busy = true;
    notifyChange();
    SessionEpoch.Ticket t = ticket;
    long g = generation;
    repository
        .load(t, postId)
        .addOnSuccessListener(
            r -> {
              if (!current(t, g)) return;
              busy = false;
              if (r.value != null) conflict = r.value;
              else message = code(r.code);
              notifyChange();
            });
  }

  public void resolve(boolean keep) {
    if (conflict == null || writePending) return;
    detail = conflict;
    contentVersion = version(detail.root, "contentVersion");
    controlVersion = version(detail.root, "controlVersion");
    if (!keep) fill(detail);
    conflict = null;
    pending = null;
    forgetOperation();
    message = "";
    dirty = true;
    notifyChange();
  }

  public void releaseRecovery() {
    if (!current()
        || busy
        || writePending
        || !restored
        || !postsChecked
        || !operationsChecked
        || !"no_receipt".equals(message)) return;
    forgetOperation();
    pending = null;
    postId = null;
    body = "";
    editing = false;
    dirty = false;
    notifyChange();
  }

  public void returnToList() {
    if (locked()) return;
    generation++;
    body = "";
    postId = null;
    detail = null;
    conflict = null;
    editing = false;
    dirty = false;
    list("posts", false);
  }

  private static String code(CommunityPostRepository.Code c) {
    switch (c) {
      case COMMITTED:
        return "committed";
      case CONFLICT:
        return "conflict";
      case DENIED:
      case SESSION_CHANGED:
        return "denied";
      case UNKNOWN_SCHEMA:
        return "schema";
      case UNKNOWN_OUTCOME:
        return "unknown";
      case NOT_FOUND:
        return "no_receipt";
      default:
        return "network";
    }
  }

  @Override
  protected void onCleared() {
    generation++;
    main.removeCallbacksAndMessages(null);
    if (sessions != null) sessions.removeInvalidation(invalidation);
    body = "";
    pending = null;
  }
}
