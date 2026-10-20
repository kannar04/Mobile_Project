package com.gmail.manhle240406.project.adoption;

import com.gmail.manhle240406.project.foundation.*;
import com.gmail.manhle240406.project.listing.ListingSchema;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Non-UI managed commands and bounded permitted SDK reads. No role/service activation. */
public class SafetyRepository implements AutoCloseable {
  public interface Commands {
    Task<Map<String, Object>> call(String module, String command, Map<String, Object> intent);
  }

  public static final Commands UNAVAILABLE =
      (module, command, input) ->
          Tasks.forException(new IllegalStateException("safety_callable_not_authorized"));
  protected final FirebaseFirestore db;
  protected final SessionGateway sessions;
  private final Commands commands;
  private final String module;
  private ListenerRegistration listener;
  private final Runnable invalidation = this::stop;
  private static final Set<String> OWN =
      new HashSet<>(
          Arrays.asList(
              "accountNotices",
              "decisionNotices",
              "welfareStatus",
              "supportStatus",
              "followupIndex",
              "experienceFeedback",
              "returnIndex",
              "moderationNotices"));

  public SafetyRepository(
      FirebaseFirestore db, SessionGateway sessions, Commands commands, String module) {
    this.db = db;
    this.sessions = sessions;
    this.commands = Objects.requireNonNull(commands);
    this.module = module;
    sessions.onInvalidated(invalidation);
  }

  protected void current(SessionEpoch.Ticket t) {
    if (t.uid == null || !sessions.current(t)) throw new IllegalStateException("session_changed");
  }

  protected <T> Task<T> scoped(SessionEpoch.Ticket t, Task<T> task) {
    return Tasks.withTimeout(task, 15, TimeUnit.SECONDS)
        .continueWith(
            done -> {
              current(t);
              if (!done.isSuccessful()) throw Objects.requireNonNull(done.getException());
              return done.getResult();
            });
  }

  public Task<Map<String, Object>> execute(
      SessionEpoch.Ticket t, String command, Map<String, Object> input) {
    current(t);
    if (command == null || !command.matches("[a-z_]{1,40}"))
      throw new IllegalArgumentException("command");
    if (AdoptionValues.canonical(input).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
        > 32768) throw new IllegalArgumentException("command_size");
    Map<String, Object> frozen = new HashMap<>(input);
    if (!Arrays.asList("lookup_uid", "lookup_email", "read_evidence", "evaluate_due")
        .contains(command)) {
      if (frozen.containsKey("command") && !command.equals(frozen.get("command")))
        throw new IllegalArgumentException("command_mismatch");
      frozen.put("command", command);
      AdoptionValues.listingId((String) frozen.get("operationId"));
      AdoptionValues.version(frozen, "expectedVersion");
    }
    return scoped(t, commands.call(module, command, ListingSchema.freeze(frozen)));
  }

  public Task<DocumentSnapshot> own(SessionEpoch.Ticket t, String collection, String id) {
    current(t);
    if (!OWN.contains(collection) || id == null || id.contains("/"))
      throw new IllegalArgumentException("own_path");
    return scoped(
        t, db.document("users/" + t.uid + "/" + collection + "/" + id).get(Source.SERVER));
  }

  private Query ownQuery(SessionEpoch.Ticket t, String collection) {
    if (!OWN.contains(collection)) throw new IllegalArgumentException("own_collection");
    String field = "followupIndex".equals(collection) ? "updatedAt" : "createdAt";
    return db.collection("users/" + t.uid + "/" + collection)
        .orderBy(field, Query.Direction.DESCENDING)
        .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
        .limit(20);
  }

  public Task<QuerySnapshot> page(
      SessionEpoch.Ticket t, String collection, DocumentSnapshot cursor) {
    current(t);
    Query q = ownQuery(t, collection);
    if (cursor != null) {
      if (!cursor.getReference().getParent().getPath().equals("users/" + t.uid + "/" + collection))
        throw new IllegalArgumentException("foreign_cursor");
      q = q.startAfter(cursor);
    }
    return scoped(t, q.get(Source.SERVER));
  }

  public synchronized void head(
      SessionEpoch.Ticket t,
      String collection,
      com.google.firebase.firestore.EventListener<QuerySnapshot> callback) {
    current(t);
    stop();
    listener =
        ownQuery(t, collection)
            .addSnapshotListener(
                (rows, error) -> {
                  if (sessions.current(t)) callback.onEvent(rows, error);
                });
  }

  public synchronized void stop() {
    if (listener != null) {
      listener.remove();
      listener = null;
    }
  }

  @Override
  public synchronized void close() {
    stop();
    sessions.removeInvalidation(invalidation);
  }
}
