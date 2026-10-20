package com.gmail.manhle240406.project.community;

import static com.gmail.manhle240406.project.pet.PetSchema.*;

import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class CommunityPostRepository {
  public enum Code {
    SERVER,
    COMMITTED,
    NOT_FOUND,
    CONFLICT,
    DENIED,
    UNKNOWN_SCHEMA,
    UNAVAILABLE,
    UNKNOWN_OUTCOME,
    SESSION_CHANGED
  }

  public static final class Result<T> {
    public final Code code;
    public final T value;

    Result(Code c, T v) {
      code = c;
      value = v;
    }
  }

  public static final class Detail {
    public final Map<String, Object> root, revision;

    Detail(Map<String, Object> r, Map<String, Object> v) {
      root = r;
      revision = v;
    }
  }

  public static final class Page {
    public final List<Map<String, Object>> rows;
    public final DocumentSnapshot cursor;

    Page(List<Map<String, Object>> r, DocumentSnapshot c) {
      rows = Collections.unmodifiableList(r);
      cursor = c;
    }
  }

  private final FirebaseFirestore db;
  private final SessionGateway sessions;

  public CommunityPostRepository(FirebaseFirestore db, SessionGateway sessions) {
    this.db = db;
    this.sessions = sessions;
  }

  private boolean current(SessionEpoch.Ticket t) {
    return t != null && t.uid != null && sessions.current(t);
  }

  private static void require(boolean x, String m) {
    if (!x) throw new IllegalStateException(m);
  }

  private static Map<String, Object> data(DocumentSnapshot d) {
    return d.exists() ? new HashMap<>(Objects.requireNonNull(d.getData())) : null;
  }

  private DocumentReference post(String id) {
    return db.document("communityPosts/" + id);
  }

  private DocumentReference operation(String uid, String id) {
    return db.document("users/" + uid + "/communityOperations/" + id);
  }

  public Task<Result<Detail>> load(SessionEpoch.Ticket t, String id) {
    if (!current(t) || !id(id)) return Tasks.forResult(new Result<>(Code.SESSION_CHANGED, null));
    Task<Detail> task =
        post(id)
            .get(Source.SERVER)
            .continueWithTask(
                first -> {
                  if (!first.isSuccessful()) throw Objects.requireNonNull(first.getException());
                  Map<String, Object> p = data(first.getResult());
                  require(p != null, "not_found");
                  require(
                      CommunitySchema.root(p, id) && t.uid.equals(p.get("authorUid")),
                      "unknown_schema");
                  return post(id)
                      .collection("revisions")
                      .document((String) p.get("currentRevisionId"))
                      .get(Source.SERVER)
                      .continueWith(
                          r -> {
                            if (!r.isSuccessful()) throw Objects.requireNonNull(r.getException());
                            Map<String, Object> v = data(r.getResult());
                            require(
                                CommunitySchema.revision(v, id, version(p, "contentVersion"))
                                    && t.uid.equals(v.get("authorUid")),
                                "unknown_schema");
                            return new Detail(p, v);
                          });
                });
    return wrap(Tasks.withTimeout(task, 15, TimeUnit.SECONDS), t, false);
  }

  public Task<Result<Map<String, Object>>> receipt(SessionEpoch.Ticket t, String id) {
    if (!current(t) || !id(id)) return Tasks.forResult(new Result<>(Code.SESSION_CHANGED, null));
    Task<Map<String, Object>> task =
        operation(t.uid, id)
            .get(Source.SERVER)
            .continueWith(
                r -> {
                  if (!r.isSuccessful()) throw Objects.requireNonNull(r.getException());
                  Map<String, Object> m = data(r.getResult());
                  require(m != null, "not_found");
                  require(CommunitySchema.receipt(m, t.uid, id), "unknown_schema");
                  return m;
                });
    return wrap(Tasks.withTimeout(task, 15, TimeUnit.SECONDS), t, false);
  }

  public Task<Result<Page>> list(
      SessionEpoch.Ticket t, String kind, String postId, DocumentSnapshot cursor) {
    if (!current(t)) return Tasks.forResult(new Result<>(Code.SESSION_CHANGED, null));
    Query q;
    if ("posts".equals(kind))
      q =
          db.collection("communityPosts")
              .whereEqualTo("schemaVersion", 1L)
              .whereEqualTo("authorUid", t.uid)
              .orderBy("updatedAt", Query.Direction.DESCENDING);
    else if ("operations".equals(kind))
      q =
          db.collection("users/" + t.uid + "/communityOperations")
              .whereEqualTo("schemaVersion", 1L)
              .orderBy("committedAt", Query.Direction.DESCENDING);
    else if ("revisions".equals(kind) && id(postId))
      q =
          post(postId)
              .collection("revisions")
              .orderBy("contentVersion", Query.Direction.DESCENDING);
    else return Tasks.forResult(new Result<>(Code.DENIED, null));
    q = q.orderBy(FieldPath.documentId(), Query.Direction.DESCENDING).limit(20);
    if (cursor != null) q = q.startAfter(cursor);
    Task<Page> task =
        q.get(Source.SERVER)
            .continueWith(
                r -> {
                  if (!r.isSuccessful()) throw Objects.requireNonNull(r.getException());
                  List<Map<String, Object>> rows = new ArrayList<>();
                  for (DocumentSnapshot d : r.getResult()) {
                    Map<String, Object> m = data(d);
                    boolean okay =
                        "posts".equals(kind)
                            ? CommunitySchema.root(m, d.getId()) && t.uid.equals(m.get("authorUid"))
                            : "operations".equals(kind)
                                ? CommunitySchema.receipt(m, t.uid, d.getId())
                                : number(m.get("contentVersion"), 1)
                                    && CommunitySchema.revision(
                                        m, postId, version(m, "contentVersion"))
                                    && t.uid.equals(m.get("authorUid"));
                    require(okay, "unknown_schema");
                    rows.add(m);
                  }
                  List<DocumentSnapshot> docs = r.getResult().getDocuments();
                  return new Page(rows, docs.size() == 20 ? docs.get(19) : null);
                });
    return wrap(Tasks.withTimeout(task, 15, TimeUnit.SECONDS), t, false);
  }

  public Task<Result<Map<String, Object>>> execute(SessionEpoch.Ticket t, CommunityCommand c) {
    if (!current(t)) return Tasks.forResult(new Result<>(Code.SESSION_CHANGED, null));
    Task<Map<String, Object>> task =
        db.runTransaction(
            new TransactionOptions.Builder().setMaxAttempts(3).build(),
            tx -> {
              require(current(t), "session_changed");
              DocumentReference receipt = operation(t.uid, c.operationId);
              Map<String, Object> previous = data(tx.get(receipt));
              if (previous != null) {
                require(
                    CommunitySchema.receipt(previous, t.uid, c.operationId) && c.matches(previous),
                    "conflict");
                return previous;
              }
              DocumentReference root = post(c.postId);
              Map<String, Object> old = data(tx.get(root));
              boolean create = "create".equals(c.command),
                  material = create || "edit".equals(c.command);
              require(
                  create
                      ? old == null
                      : CommunitySchema.root(old, c.postId) && t.uid.equals(old.get("authorUid")),
                  "conflict");
              require(
                  version(old, "contentVersion") == c.contentVersion
                      && version(old, "controlVersion") == c.controlVersion,
                  "conflict");
              if (!create) require("active".equals(old.get("lifecycle")), "denied");
              // The gated private slice owns no feed reads. Rules perform atomic
              // projection-absence/removal checks.
              if (material) {
                Map<String, Object> shell = data(tx.get(db.document("users/" + t.uid))),
                    subject = data(tx.get(db.document("securitySubjects/" + t.uid)));
                require(
                    shell != null
                        && keys(shell, "uid", "schemaVersion", "createdAt")
                        && t.uid.equals(shell.get("uid"))
                        && Long.valueOf(1).equals(shell.get("schemaVersion"))
                        && subject != null
                        && "active".equals(subject.get("status")),
                    "denied");
              }
              Object now = FieldValue.serverTimestamp();
              long cv = c.contentVersion + (material ? 1 : 0), ctl = c.controlVersion + 1;
              Map<String, Object> next =
                  create
                      ? map(
                          "schemaVersion",
                          1L,
                          "postId",
                          c.postId,
                          "authorUid",
                          t.uid,
                          "type",
                          "community",
                          "lifecycle",
                          "active",
                          "moderatorHold",
                          "clear",
                          "firstPublishedAt",
                          null,
                          "createdAt",
                          now)
                      : new HashMap<>(old);
              next.putAll(
                  map(
                      "contentVersion",
                      cv,
                      "currentRevisionId",
                      "v" + cv,
                      "controlVersion",
                      ctl,
                      "visibility",
                      "hidden",
                      "submittedVersion",
                      null,
                      "publicationConsent",
                      false,
                      "updatedAt",
                      now,
                      "lastOperationId",
                      c.operationId));
              if ("archive".equals(c.command)) next.put("lifecycle", "archived");
              Map<String, Object> rec =
                  map(
                      "schemaVersion",
                      1L,
                      "ownerUid",
                      t.uid,
                      "operationId",
                      c.operationId,
                      "postId",
                      c.postId,
                      "command",
                      c.command,
                      "base",
                      c.base(),
                      "result",
                      map("contentVersion", cv, "controlVersion", ctl),
                      "input",
                      c.input,
                      "status",
                      "committed",
                      "committedAt",
                      now);
              require(current(t), "session_changed");
              tx.set(root, next);
              if (material) {
                Map<String, Object> revision =
                    map(
                        "schemaVersion",
                        1L,
                        "postId",
                        c.postId,
                        "authorUid",
                        t.uid,
                        "type",
                        "community",
                        "contentVersion",
                        cv,
                        "body",
                        c.input.get("body"),
                        "bundledIllustrationKey",
                        c.input.get("bundledIllustrationKey"),
                        "createdAt",
                        now,
                        "operationId",
                        c.operationId);
                tx.set(root.collection("revisions").document("v" + cv), revision);
              }
              if (!create) tx.delete(db.document("communityFeed/" + c.postId));
              tx.set(receipt, rec);
              return rec;
            });
    return wrap(task, t, true)
        .continueWithTask(
            done -> {
              Result<Map<String, Object>> r = done.getResult();
              if (r.code == Code.DENIED || r.code == Code.UNKNOWN_OUTCOME)
                return receipt(t, c.operationId)
                    .continueWith(
                        x -> {
                          Result<Map<String, Object>> observed = x.getResult();
                          return observed.value != null && c.matches(observed.value)
                              ? new Result<>(Code.COMMITTED, observed.value)
                              : r;
                        });
              return Tasks.forResult(r);
            });
  }

  private <T> Task<Result<T>> wrap(Task<T> task, SessionEpoch.Ticket t, boolean write) {
    return task.continueWith(
        done -> {
          if (!current(t)) return new Result<>(Code.SESSION_CHANGED, null);
          if (done.isSuccessful())
            return new Result<>(write ? Code.COMMITTED : Code.SERVER, done.getResult());
          Throwable e = done.getException();
          while (e != null) {
            String m = e.getMessage();
            if ("conflict".equals(m)) return new Result<>(Code.CONFLICT, null);
            if ("denied".equals(m)) return new Result<>(Code.DENIED, null);
            if ("not_found".equals(m)) return new Result<>(Code.NOT_FOUND, null);
            if ("unknown_schema".equals(m)) return new Result<>(Code.UNKNOWN_SCHEMA, null);
            if (e instanceof FirebaseFirestoreException) {
              FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
              if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                  || code == FirebaseFirestoreException.Code.UNAUTHENTICATED)
                return new Result<>(Code.DENIED, null);
              if (code == FirebaseFirestoreException.Code.ABORTED)
                return new Result<>(Code.CONFLICT, null);
            }
            e = e.getCause();
          }
          return new Result<>(write ? Code.UNKNOWN_OUTCOME : Code.UNAVAILABLE, null);
        });
  }
}
