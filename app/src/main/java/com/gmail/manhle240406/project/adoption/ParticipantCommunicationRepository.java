package com.gmail.manhle240406.project.adoption;

import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * F19 private text and explicit own contact consent. No external intents, attachment or push
 * wiring.
 */
public final class ParticipantCommunicationRepository implements AutoCloseable {
  private final FirebaseFirestore db;
  private final SessionGateway sessions;
  private ListenerRegistration listener;
  private final Runnable invalidation = this::stop;

  public ParticipantCommunicationRepository(FirebaseFirestore db, SessionGateway sessions) {
    this.db = db;
    this.sessions = sessions;
    sessions.onInvalidated(invalidation);
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

  private DocumentReference ref(String path) {
    return db.document(path);
  }

  private Map<String, Object> record(DocumentSnapshot d) {
    if (!d.exists()) throw new IllegalStateException("record_unavailable");
    return Objects.requireNonNull(d.getData());
  }

  private static Map<String, Object> map(Object... args) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < args.length; i += 2) m.put((String) args[i], args[i + 1]);
    return m;
  }

  private String peer(Map<String, Object> grant, String uid) {
    if (uid.equals(grant.get("hostUid"))) return (String) grant.get("applicantUid");
    if (uid.equals(grant.get("applicantUid"))) return (String) grant.get("hostUid");
    throw new IllegalStateException("not_participant");
  }

  public Task<DocumentSnapshot> contact(SessionEpoch.Ticket t, String reservation, String owner) {
    current(t);
    if (owner == null || owner.contains("/")) throw new IllegalArgumentException("contact_owner");
    return scoped(
        t,
        ref("reservationContactCards/" + AdoptionValues.listingId(reservation) + "/owners/" + owner)
            .get(Source.SERVER));
  }

  public Task<Void> shareContact(
      SessionEpoch.Ticket t,
      String reservation,
      String operation,
      long expectedConsentVersion,
      boolean consent,
      String previewPhone,
      long previewSourceVersion) {
    current(t);
    String id = AdoptionValues.listingId(reservation), op = AdoptionValues.listingId(operation);
    if (expectedConsentVersion < 0) throw new IllegalArgumentException("consent_version");
    DocumentReference card = ref("reservationContactCards/" + id + "/owners/" + t.uid);
    return scoped(
        t,
        db.runTransaction(
            tx -> {
              current(t);
              DocumentSnapshot old = tx.get(card);
              Map<String, Object> grant = record(tx.get(ref("reservationCommunication/" + id)));
              Map<String, Object> source =
                  consent ? record(tx.get(ref("users/" + t.uid + "/private/contact"))) : null;
              if (old.exists() && op.equals(old.getString("operationId"))) {
                if (!Boolean.valueOf(consent).equals(old.getBoolean("consent"))
                    || old.getLong("consentVersion") != expectedConsentVersion + 1
                    || consent
                        && (!Objects.equals(previewPhone, old.getString("phoneE164"))
                            || !Long.valueOf(previewSourceVersion)
                                .equals(old.getLong("sourceVersion"))))
                  throw new IllegalStateException("intent_conflict");
                return null;
              }
              long v = old.exists() ? AdoptionValues.version(old.getData(), "consentVersion") : 0;
              if (v != expectedConsentVersion) throw new IllegalStateException("version_conflict");
              Map<String, Object> next =
                  old.exists()
                      ? new HashMap<>(record(old))
                      : map(
                          "schemaVersion",
                          1L,
                          "reservationId",
                          id,
                          "ownerUid",
                          t.uid,
                          "counterpartUid",
                          peer(grant, t.uid));
              if (consent) {
                String phone = (String) source.get("phoneE164");
                if (phone == null || !phone.matches("[+][0-9]{7,15}"))
                  throw new IllegalStateException("phone_unavailable");
                if (!Objects.equals(phone, previewPhone)
                    || AdoptionValues.version(source, "version") != previewSourceVersion)
                  throw new IllegalStateException("contact_preview_changed");
                next.put("phoneE164", phone);
                next.put("sourceVersion", AdoptionValues.version(source, "version"));
              } else if (!old.exists()) throw new IllegalStateException("no_shared_card");
              next.put("consent", consent);
              next.put("consentVersion", v + 1);
              next.put("updatedAt", FieldValue.serverTimestamp());
              next.put("operationId", op);
              current(t);
              tx.set(card, next);
              return null;
            }));
  }

  public Task<Void> block(
      SessionEpoch.Ticket t,
      String reservation,
      String operation,
      long expectedVersion,
      boolean blocked) {
    current(t);
    String id = AdoptionValues.listingId(reservation), op = AdoptionValues.listingId(operation);
    DocumentReference target = ref("conversationBlocks/" + id + "/members/" + t.uid);
    return scoped(
        t,
        db.runTransaction(
            tx -> {
              current(t);
              DocumentSnapshot d = tx.get(target);
              long v = d.exists() ? AdoptionValues.version(d.getData(), "version") : 0;
              if (d.exists() && op.equals(d.getString("operationId"))) {
                if (!Boolean.valueOf(blocked).equals(d.getBoolean("blocked"))
                    || v != expectedVersion + 1) throw new IllegalStateException("intent_conflict");
                return null;
              }
              if (v != expectedVersion) throw new IllegalStateException("version_conflict");
              tx.set(
                  target,
                  map(
                      "uid",
                      t.uid,
                      "blocked",
                      blocked,
                      "version",
                      v + 1,
                      "updatedAt",
                      FieldValue.serverTimestamp(),
                      "operationId",
                      op));
              return null;
            }));
  }

  public Task<Map<String, Object>> send(
      SessionEpoch.Ticket t,
      String reservation,
      String messageId,
      String operation,
      long expectedCounterVersion,
      String body) {
    current(t);
    String id = AdoptionValues.listingId(reservation),
        mid = AdoptionValues.listingId(messageId),
        op = AdoptionValues.listingId(operation);
    AdoptionValues.text(body, 2000);
    if (body.isEmpty() || expectedCounterVersion < 0)
      throw new IllegalArgumentException("message_schema");
    String digest =
        AdoptionValues.digest(
            AdoptionValues.canonical(
                map(
                    "reservationId",
                    id,
                    "messageId",
                    mid,
                    "baseVersion",
                    expectedCounterVersion,
                    "body",
                    body)));
    return scoped(
        t,
        db.runTransaction(
            tx -> {
              current(t);
              DocumentReference receipt =
                  ref("conversationReceipts/" + t.uid + "/operations/" + op);
              DocumentSnapshot prior = tx.get(receipt);
              if (prior.exists()) {
                if (!digest.equals(prior.getString("inputDigest")))
                  throw new IllegalStateException("intent_conflict");
                return Collections.unmodifiableMap(
                    map(
                        "messageId",
                        prior.getString("messageId"),
                        "sequence",
                        prior.getLong("sequence")));
              }
              Map<String, Object> grant = record(tx.get(ref("reservationCommunication/" + id)));
              String other = peer(grant, t.uid);
              DocumentReference counterRef = ref("conversationCounters/" + id),
                  rateRef = ref("conversationSendLimits/" + id + "/members/" + t.uid),
                  messageRef = ref("conversations/" + id + "/messages/" + mid);
              Map<String, Object> counter = record(tx.get(counterRef));
              DocumentSnapshot rate = tx.get(rateRef), existing = tx.get(messageRef);
              DocumentReference ownIndex = ref("users/" + t.uid + "/conversationIndex/" + id),
                  peerIndex = ref("users/" + other + "/conversationIndex/" + id);
              long version = AdoptionValues.version(counter, "version"),
                  sequence = AdoptionValues.version(counter, "nextSequence"),
                  count = AdoptionValues.version(counter, "messageCount");
              if (version != expectedCounterVersion || count >= 1000 || existing.exists())
                throw new IllegalStateException("send_conflict");
              Object now = FieldValue.serverTimestamp();
              Map<String, Object> result =
                  map(
                      "schemaVersion",
                      1L,
                      "uid",
                      t.uid,
                      "operationId",
                      op,
                      "reservationId",
                      id,
                      "messageId",
                      mid,
                      "inputDigest",
                      digest,
                      "baseVersion",
                      version,
                      "sequence",
                      sequence,
                      "committedAt",
                      now);
              tx.set(
                  messageRef,
                  map(
                      "schemaVersion",
                      1L,
                      "senderUid",
                      t.uid,
                      "reservationId",
                      id,
                      "messageId",
                      mid,
                      "sequence",
                      sequence,
                      "body",
                      body,
                      "source",
                      "participant_message",
                      "createdAt",
                      now,
                      "operationId",
                      op));
              tx.set(
                  counterRef,
                  map(
                      "schemaVersion",
                      1L,
                      "reservationId",
                      id,
                      "nextSequence",
                      sequence + 1,
                      "messageCount",
                      count + 1,
                      "version",
                      version + 1));
              tx.set(
                  rateRef,
                  map(
                      "uid",
                      t.uid,
                      "lastSentAt",
                      now,
                      "version",
                      rate.exists() ? AdoptionValues.version(rate.getData(), "version") + 1 : 1L,
                      "operationId",
                      op,
                      "reservationId",
                      id));
              tx.update(ownIndex, "updatedAt", now);
              tx.update(peerIndex, "updatedAt", now);
              tx.set(receipt, result);
              current(t);
              return Collections.unmodifiableMap(map("messageId", mid, "sequence", sequence));
            }));
  }

  private Query thread(String id) {
    return db.collection("conversations/" + id + "/messages")
        .orderBy("sequence", Query.Direction.DESCENDING)
        .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
        .limit(20);
  }

  public Task<QuerySnapshot> messages(
      SessionEpoch.Ticket t, String reservation, DocumentSnapshot older) {
    current(t);
    String id = AdoptionValues.listingId(reservation);
    Query q = thread(id);
    if (older != null) {
      if (!older.getReference().getParent().getPath().equals("conversations/" + id + "/messages"))
        throw new IllegalArgumentException("foreign_cursor");
      q = q.startAfter(older);
    }
    return scoped(t, q.get(Source.SERVER));
  }

  public synchronized void listenInbox(
      SessionEpoch.Ticket t, com.google.firebase.firestore.EventListener<QuerySnapshot> callback) {
    current(t);
    stop();
    listener =
        db.collection("users/" + t.uid + "/conversationIndex")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener(
                MetadataChanges.INCLUDE,
                (rows, error) -> {
                  if (sessions.current(t)) callback.onEvent(rows, error);
                });
  }

  public synchronized void listen(
      SessionEpoch.Ticket t,
      String reservation,
      com.google.firebase.firestore.EventListener<QuerySnapshot> callback) {
    current(t);
    stop();
    listener =
        thread(AdoptionValues.listingId(reservation))
            .addSnapshotListener(
                MetadataChanges.INCLUDE,
                (rows, error) -> {
                  if (sessions.current(t)) callback.onEvent(rows, error);
                });
  }

  public Task<QuerySnapshot> inbox(SessionEpoch.Ticket t, DocumentSnapshot cursor) {
    current(t);
    String path = "users/" + t.uid + "/conversationIndex";
    Query q =
        db.collection(path)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
            .limit(20);
    if (cursor != null) {
      if (!cursor.getReference().getParent().getPath().equals(path))
        throw new IllegalArgumentException("foreign_cursor");
      q = q.startAfter(cursor);
    }
    return scoped(t, q.get(Source.SERVER));
  }

  public Task<Void> markRead(SessionEpoch.Ticket t, String reservation, long sequence) {
    current(t);
    String id = AdoptionValues.listingId(reservation);
    DocumentReference index = ref("users/" + t.uid + "/conversationIndex/" + id);
    return scoped(
        t,
        db.runTransaction(
            tx -> {
              current(t);
              Map<String, Object> row = record(tx.get(index)),
                  counter = record(tx.get(ref("conversationCounters/" + id)));
              long old = AdoptionValues.version(row, "lastReadSequence");
              if (sequence < old || sequence >= AdoptionValues.version(counter, "nextSequence"))
                throw new IllegalArgumentException("read_marker");
              tx.update(index, "lastReadSequence", sequence);
              return null;
            }));
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
