package com.gmail.manhle240406.project.adoption;

import com.gmail.manhle240406.project.foundation.*;
import com.gmail.manhle240406.project.listing.ListingSchema;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** F17/F18/F20/F21 managed command port. No callable service is activated or wired. */
public final class PlacementCommandRepository {
  public interface Commands {
    Task<Map<String, Object>> call(Map<String, Object> intent);
  }

  public static final Commands UNAVAILABLE =
      intent -> Tasks.forException(new IllegalStateException("placement_service_not_authorized"));
  private static final Set<String> COMMANDS =
      new HashSet<>(
          Arrays.asList(
              "submit",
              "amend",
              "withdraw",
              "request_cancel",
              "select",
              "reject",
              "acknowledge_preparation",
              "propose_extension",
              "ack_extension",
              "mark_review",
              "cancel_statement",
              "cancel",
              "meeting",
              "terms",
              "ack_meeting",
              "checklist",
              "accept_terms",
              "revoke_consent",
              "confirm",
              "correct_confirmation",
              "finalize"));
  private final FirebaseFirestore db;
  private final SessionGateway sessions;
  private final Commands commands;

  public PlacementCommandRepository(
      FirebaseFirestore db, SessionGateway sessions, Commands commands) {
    this.db = db;
    this.sessions = sessions;
    this.commands = Objects.requireNonNull(commands);
  }

  private void current(SessionEpoch.Ticket t) {
    if (t.uid == null || !sessions.current(t)) throw new IllegalStateException("session_changed");
  }

  private <T> Task<T> scoped(SessionEpoch.Ticket t, Task<T> task) {
    return Tasks.withTimeout(task, 15, TimeUnit.SECONDS)
        .continueWith(
            done -> {
              current(t);
              if (!done.isSuccessful()) throw Objects.requireNonNull(done.getException());
              return done.getResult();
            });
  }

  public Task<Map<String, Object>> execute(SessionEpoch.Ticket t, Map<String, Object> intent) {
    current(t);
    if (!COMMANDS.contains(intent.get("command")))
      throw new IllegalArgumentException("command_not_allowed");
    AdoptionValues.listingId((String) intent.get("operationId"));
    AdoptionValues.version(intent, "expectedVersion");
    return scoped(t, commands.call(ListingSchema.freeze(intent)));
  }

  public Task<DocumentSnapshot> receipt(SessionEpoch.Ticket t, String module, String operation) {
    current(t);
    String op = AdoptionValues.listingId(operation), path;
    if ("questionnaire".equals(module)) path = "users/" + t.uid + "/questionnaireReceipts/" + op;
    else if ("preparation".equals(module))
      path = "preparationReceipts/" + t.uid + "/operations/" + op;
    else if (Arrays.asList("application", "reservation", "handover").contains(module))
      path = module + "Operations/" + t.uid + "/receipts/" + op;
    else throw new IllegalArgumentException("receipt_module");
    return scoped(t, db.document(path).get(Source.SERVER));
  }

  public Task<DocumentSnapshot> application(SessionEpoch.Ticket t, String id) {
    current(t);
    return scoped(
        t, db.document("applications/" + AdoptionValues.listingId(id)).get(Source.SERVER));
  }

  public Task<DocumentSnapshot> reservation(SessionEpoch.Ticket t, String id) {
    current(t);
    return scoped(
        t, db.document("reservations/" + AdoptionValues.listingId(id)).get(Source.SERVER));
  }

  public Task<DocumentSnapshot> placement(SessionEpoch.Ticket t, String id) {
    current(t);
    return scoped(t, db.document("placements/" + AdoptionValues.listingId(id)).get(Source.SERVER));
  }

  public Task<DocumentSnapshot> parentOutcome(SessionEpoch.Ticket t, String listingId) {
    current(t);
    return scoped(
        t,
        db.document("listingOutcomeSummaries/" + AdoptionValues.listingId(listingId))
            .get(Source.SERVER));
  }

  public Task<DocumentSnapshot> template(SessionEpoch.Ticket t, String id, long version) {
    current(t);
    if (version < 1) throw new IllegalArgumentException("template_version");
    return scoped(
        t,
        db.document(
                "questionnaireTemplates/" + AdoptionValues.catalogId(id) + "/revisions/v" + version)
            .get(Source.SERVER));
  }

  public Task<DocumentSnapshot> binding(SessionEpoch.Ticket t, String listingId, long version) {
    current(t);
    if (version < 1) throw new IllegalArgumentException("binding_version");
    return scoped(
        t,
        db.document(
                "listingQuestionnaireBindings/"
                    + AdoptionValues.listingId(listingId)
                    + "/versions/v"
                    + version)
            .get(Source.SERVER));
  }

  public Task<QuerySnapshot> ownApplications(
      SessionEpoch.Ticket t, String status, DocumentSnapshot cursor) {
    current(t);
    if (cursor != null && status != null && !status.equals(cursor.getString("status")))
      throw new IllegalArgumentException("foreign_cursor");
    Query q = db.collection("users/" + t.uid + "/applicationIndex");
    if (status != null
        && !Arrays.asList(
                "pending",
                "under_review",
                "selected",
                "preparing",
                "cancellation_requested",
                "withdrawn",
                "rejected",
                "cancelled",
                "completed",
                "closed_not_selected")
            .contains(status)) throw new IllegalArgumentException("application_status");
    if (status != null) q = q.whereEqualTo("status", status);
    String path = "users/" + t.uid + "/applicationIndex";
    return page(
        t,
        q.orderBy("submittedAt", Query.Direction.DESCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING),
        path,
        cursor);
  }

  public Task<QuerySnapshot> hostApplications(
      SessionEpoch.Ticket t, String listingId, String status, DocumentSnapshot cursor) {
    current(t);
    if (cursor != null
        && (!t.uid.equals(cursor.getString("hostUid"))
            || !listingId.equals(cursor.getString("listingId"))
            || status != null && !status.equals(cursor.getString("status"))))
      throw new IllegalArgumentException("foreign_cursor");
    if (status != null
        && !Arrays.asList(
                "pending",
                "under_review",
                "selected",
                "preparing",
                "cancellation_requested",
                "withdrawn",
                "rejected",
                "cancelled",
                "completed",
                "closed_not_selected")
            .contains(status)) throw new IllegalArgumentException("application_status");
    Query q =
        db.collection("applications")
            .whereEqualTo("hostUid", t.uid)
            .whereEqualTo("listingId", AdoptionValues.listingId(listingId));
    if (status != null) q = q.whereEqualTo("status", status);
    return page(
        t,
        q.orderBy("submittedAt", Query.Direction.DESCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING),
        "applications",
        cursor);
  }

  public Task<QuerySnapshot> answers(SessionEpoch.Ticket t, String app, DocumentSnapshot cursor) {
    current(t);
    String path = "applications/" + AdoptionValues.listingId(app) + "/answerRevisions";
    return page(
        t,
        db.collection(path)
            .orderBy("answerVersion", Query.Direction.DESCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING),
        path,
        cursor);
  }

  public Task<DocumentSnapshot> preparation(SessionEpoch.Ticket t, String reservation) {
    current(t);
    return scoped(
        t,
        db.document("reservationPreparation/" + AdoptionValues.listingId(reservation))
            .get(Source.SERVER));
  }

  public Task<DocumentSnapshot> preparationRevision(
      SessionEpoch.Ticket t, String reservation, String kind, long version) {
    current(t);
    if (!Arrays.asList("meeting", "terms").contains(kind) || version < 1)
      throw new IllegalArgumentException("preparation_revision");
    return scoped(
        t,
        db.document(
                "reservationPreparation/"
                    + AdoptionValues.listingId(reservation)
                    + "/"
                    + kind
                    + "Revisions/v"
                    + version)
            .get(Source.SERVER));
  }

  private Task<QuerySnapshot> page(
      SessionEpoch.Ticket t, Query q, String path, DocumentSnapshot cursor) {
    if (cursor != null) {
      if (!cursor.getReference().getParent().getPath().equals(path))
        throw new IllegalArgumentException("foreign_cursor");
      q = q.startAfter(cursor);
    }
    return scoped(t, q.limit(20).get(Source.SERVER));
  }
}
