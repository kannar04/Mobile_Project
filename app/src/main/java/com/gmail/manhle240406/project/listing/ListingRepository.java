package com.gmail.manhle240406.project.listing;

import static com.gmail.manhle240406.project.pet.PetSchema.*;

import com.gmail.manhle240406.project.foundation.*;
import com.gmail.manhle240406.project.pet.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Online commands. No publication, trusted decision, activity mutation or canonical deletion. */
public final class ListingRepository {
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

    public ListingRepository(FirebaseFirestore db, SessionGateway sessions) {
        this.db = db;
        this.sessions = sessions;
    }

    private DocumentReference ref(String p) {
        return db.document(p);
    }

    private boolean valid(SessionEpoch.Ticket t) {
        return t != null && t.uid != null && sessions.current(t);
    }

    private static void require(boolean x, String m) {
        if (!x) throw new IllegalStateException(m);
    }

    private static Map<String, Object> data(DocumentSnapshot d) {
        return d.exists() ? new HashMap<>(Objects.requireNonNull(d.getData())) : null;
    }

    private String root(String id) {
        return "listings/" + id;
    }

    private String op(String id, String operation) {
        return root(id) + "/operations/" + operation;
    }

    public Task<Result<Detail>> load(SessionEpoch.Ticket t, String id) {
        Task<Detail> read =
                ref(root(id))
                        .get(Source.SERVER)
                        .continueWithTask(
                                task -> {
                                    if (!task.isSuccessful())
                                        throw Objects.requireNonNull(task.getException());
                                    Map<String, Object> r = data(task.getResult());
                                    require(r != null, "not_found");
                                    require(
                                            ListingSchema.root(r, id)
                                                    && t.uid.equals(r.get("ownerUid")),
                                            "unknown_schema");
                                    return ref(root(id) + "/revisions/" + r.get("activeRevisionId"))
                                            .get(Source.SERVER)
                                            .continueWith(
                                                    v -> {
                                                        if (!v.isSuccessful())
                                                            throw Objects.requireNonNull(
                                                                    v.getException());
                                                        Map<String, Object> revision =
                                                                data(v.getResult());
                                                        require(
                                                                ListingSchema.revision(
                                                                                revision,
                                                                                id,
                                                                                version(
                                                                                        r,
                                                                                        "contentVersion"))
                                                                        && Objects.equals(
                                                                                r.get("petId"),
                                                                                revision.get(
                                                                                        "petId"))
                                                                        && t.uid.equals(
                                                                                revision.get(
                                                                                        "ownerUid")),
                                                                "unknown_schema");
                                                        return new Detail(r, revision);
                                                    });
                                });
        return wrap(Tasks.withTimeout(read, 15, TimeUnit.SECONDS), t, false);
    }

    public Task<Result<Map<String, Object>>> reconcile(SessionEpoch.Ticket t, ListingCommand c) {
        return getReceipt(t, c.listingId, c.operationId)
                .continueWith(
                        done -> {
                            Result<Map<String, Object>> r = done.getResult();
                            return r.value != null && !c.matches(r.value)
                                    ? new Result<>(Code.CONFLICT, null)
                                    : r;
                        });
    }

    public Task<Result<Map<String, Object>>> getReceipt(
            SessionEpoch.Ticket t, String id, String operation) {
        Task<Map<String, Object>> read =
                ref(op(id, operation))
                        .get(Source.SERVER)
                        .continueWith(
                                task -> {
                                    if (!task.isSuccessful())
                                        throw Objects.requireNonNull(task.getException());
                                    Map<String, Object> r = data(task.getResult());
                                    require(r != null, "not_found");
                                    require(
                                            ListingSchema.receipt(r, t.uid, id, operation),
                                            "unknown_schema");
                                    return r;
                                });
        return wrap(Tasks.withTimeout(read, 15, TimeUnit.SECONDS), t, false);
    }

    public Task<Result<Page>> list(
            SessionEpoch.Ticket t, String kind, String listing, DocumentSnapshot cursor) {
        Query q;
        if ("canonical".equals(kind))
            q =
                    db.collection("listings")
                            .whereEqualTo("ownerUid", t.uid)
                            .orderBy("updatedAt", Query.Direction.DESCENDING);
        else if ("drafts".equals(kind))
            q =
                    db.collection("users/" + t.uid + "/listingDrafts")
                            .orderBy("updatedAt", Query.Direction.DESCENDING);
        else if ("revisions".equals(kind))
            q =
                    db.collection(root(listing) + "/revisions")
                            .orderBy("contentVersion", Query.Direction.DESCENDING);
        else if ("operations".equals(kind))
            q =
                    db.collection(root(listing) + "/operations")
                            .orderBy("committedAt", Query.Direction.DESCENDING);
        else return Tasks.forResult(new Result<>(Code.DENIED, null));
        q = q.orderBy(FieldPath.documentId(), Query.Direction.DESCENDING).limit(20);
        if (cursor != null) q = q.startAfter(cursor);
        Task<Page> read =
                q.get(Source.SERVER)
                        .continueWith(
                                task -> {
                                    if (!task.isSuccessful())
                                        throw Objects.requireNonNull(task.getException());
                                    List<Map<String, Object>> rows = new ArrayList<>();
                                    for (DocumentSnapshot d : task.getResult()) {
                                        Map<String, Object> r = data(d);
                                        boolean okay =
                                                "canonical".equals(kind)
                                                        ? ListingSchema.root(r, d.getId())
                                                                && t.uid.equals(r.get("ownerUid"))
                                                        : "drafts".equals(kind)
                                                                ? ListingSchema.draft(r, t.uid)
                                                                : "revisions".equals(kind)
                                                                        ? ListingSchema.revision(
                                                                                        r,
                                                                                        listing,
                                                                                        version(
                                                                                                r,
                                                                                                "contentVersion"))
                                                                                && t.uid.equals(
                                                                                        r.get(
                                                                                                "ownerUid"))
                                                                        : ListingSchema.receipt(
                                                                                r, t.uid, listing,
                                                                                d.getId());
                                        require(okay, "unknown_schema");
                                        r.put("documentId", d.getId());
                                        rows.add(r);
                                    }
                                    List<DocumentSnapshot> ds = task.getResult().getDocuments();
                                    return new Page(rows, ds.size() == 20 ? ds.get(19) : null);
                                });
        return wrap(Tasks.withTimeout(read, 15, TimeUnit.SECONDS), t, false);
    }

    public Task<Result<Map<String, Object>>> saveDraft(
            SessionEpoch.Ticket t,
            String id,
            String operation,
            long baseVersion,
            Map<String, Object> payload) {
        final Map<String, Object> frozen = ListingSchema.freeze(payload);
        Task<Map<String, Object>> write =
                db.runTransaction(
                        new TransactionOptions.Builder().setMaxAttempts(3).build(),
                        tx -> {
                            require(valid(t), "session_changed");
                            DocumentReference target =
                                    ref("users/" + t.uid + "/listingDrafts/" + id);
                            Map<String, Object> old = data(tx.get(target));
                            require(
                                    old == null || ListingSchema.draft(old, t.uid),
                                    "unknown_schema");
                            if (old != null && operation.equals(old.get("lastOperationId"))) {
                                for (String k : frozen.keySet())
                                    require(Objects.equals(frozen.get(k), old.get(k)), "conflict");
                                require(
                                        version(old, "draftVersion") == baseVersion + 1,
                                        "conflict");
                                return old;
                            }
                            require(version(old, "draftVersion") == baseVersion, "conflict");
                            Map<String, Object> next = new HashMap<>(frozen);
                            next.putAll(
                                    map(
                                            "schemaVersion",
                                            1L,
                                            "ownerUid",
                                            t.uid,
                                            "draftVersion",
                                            baseVersion + 1,
                                            "updatedAt",
                                            FieldValue.serverTimestamp(),
                                            "lastOperationId",
                                            operation));
                            require(valid(t), "session_changed");
                            tx.set(target, next);
                            return next;
                        });
        return wrap(write, t, true);
    }

    public Task<Result<Map<String, Object>>> remoteDraft(SessionEpoch.Ticket t, String id) {
        Task<Map<String, Object>> read =
                ref("users/" + t.uid + "/listingDrafts/" + id)
                        .get(Source.SERVER)
                        .continueWith(
                                task -> {
                                    if (!task.isSuccessful())
                                        throw Objects.requireNonNull(task.getException());
                                    Map<String, Object> r = data(task.getResult());
                                    require(r != null, "not_found");
                                    require(ListingSchema.draft(r, t.uid), "unknown_schema");
                                    return r;
                                });
        return wrap(Tasks.withTimeout(read, 15, TimeUnit.SECONDS), t, false);
    }

    public Task<Result<Void>> deleteDraft(
            SessionEpoch.Ticket t, String id, long expected, String lastOp) {
        Task<Void> task =
                db.runTransaction(
                        new TransactionOptions.Builder().setMaxAttempts(3).build(),
                        tx -> {
                            require(valid(t), "session_changed");
                            DocumentReference r = ref("users/" + t.uid + "/listingDrafts/" + id);
                            Map<String, Object> old = data(tx.get(r));
                            if (old == null) return null;
                            require(
                                    ListingSchema.draft(old, t.uid)
                                            && version(old, "draftVersion") == expected
                                            && Objects.equals(lastOp, old.get("lastOperationId")),
                                    "conflict");
                            require(valid(t), "session_changed");
                            tx.delete(r);
                            return null;
                        });
        return wrap(task, t, true);
    }

    public Task<Result<Map<String, Object>>> execute(SessionEpoch.Ticket t, ListingCommand c) {
        if (!valid(t)) return Tasks.forResult(new Result<>(Code.SESSION_CHANGED, null));
        Task<Map<String, Object>> task =
                db.runTransaction(
                        new TransactionOptions.Builder().setMaxAttempts(3).build(),
                        tx -> {
                            require(valid(t), "session_changed");
                            DocumentReference rr = ref(root(c.listingId)),
                                    receipt = ref(op(c.listingId, c.operationId));
                            Map<String, Object> oldReceipt = data(tx.get(receipt));
                            if (oldReceipt != null) {
                                require(
                                        ListingSchema.receipt(
                                                        oldReceipt,
                                                        t.uid,
                                                        c.listingId,
                                                        c.operationId)
                                                && c.matches(oldReceipt),
                                        "conflict");
                                return oldReceipt;
                            }
                            Map<String, Object> old = data(tx.get(rr));
                            boolean create = "create".equals(c.command),
                                    material = create || "edit".equals(c.command);
                            require(
                                    create
                                            ? old == null
                                            : ListingSchema.root(old, c.listingId)
                                                    && t.uid.equals(old.get("ownerUid"))
                                                    && c.petId.equals(old.get("petId")),
                                    "conflict");
                            require(
                                    version(old, "contentVersion")
                                                    == version(c.base, "contentVersion")
                                            && version(old, "controlVersion")
                                                    == version(c.base, "controlVersion"),
                                    "conflict");
                            Map<String, Object> slot = null,
                                    pet = null,
                                    identity = null,
                                    authority = null,
                                    gate = null;
                            boolean metadataArchive =
                                    "archive".equals(c.command)
                                            && old != null
                                            && "closed".equals(old.get("lifecycle"))
                                            && version(c.base, "slotVersion") == 0
                                            && version(c.base, "authorityVersion") == 0;
                            // For closed metadata-only archive, rules read current protected gates;
                            // the client gains no former-actor gate access. Domain version0 means
                            // not observed, as for hide.
                            if (!"hide".equals(c.command) && !metadataArchive) {
                                slot = data(tx.get(ref("petListingSlots/" + c.petId)));
                                authority = data(tx.get(ref("petOfferAuthorities/" + c.petId)));
                                gate = data(tx.get(ref("petActivityGates/" + c.petId)));
                                require(
                                        PetOfferAuthority.slot(slot, c.petId)
                                                && PetOfferAuthority.authority(authority, c.petId)
                                                && PetOfferAuthority.gate(gate, c.petId),
                                        "unknown_schema");
                                require(
                                        version(slot, "slotVersion")
                                                        == version(c.base, "slotVersion")
                                                && version(authority, "authorityVersion")
                                                        == version(c.base, "authorityVersion")
                                                && version(gate, "custodyVersion")
                                                        == version(c.base, "custodyVersion"),
                                        "conflict");
                                require(
                                        "clear".equals(authority.get("hold"))
                                                && "none".equals(gate.get("state"))
                                                && gate.get("activeReservationId") == null
                                                && gate.get("activePlacementId") == null,
                                        "denied");
                                if (material) {
                                    pet = data(tx.get(ref("pets/" + c.petId)));
                                    Map<String, Object> claim =
                                            data(
                                                    tx.get(
                                                            ref(
                                                                    "pets/"
                                                                            + c.petId
                                                                            + "/claims/"
                                                                            + t.uid)));
                                    require(PetSchema.root(pet, c.petId), "unknown_schema");
                                    identity =
                                            data(
                                                    tx.get(
                                                            ref(
                                                                    "pets/"
                                                                            + c.petId
                                                                            + "/identityRevisions/"
                                                                            + pet.get(
                                                                                    "currentRevisionId"))));
                                    require(
                                            PetSchema.revision(
                                                    identity,
                                                    c.petId,
                                                    version(pet, "identityVersion")),
                                            "unknown_schema");
                                    Map<String, Object> shell = data(tx.get(ref("users/" + t.uid))),
                                            subject =
                                                    data(tx.get(ref("securitySubjects/" + t.uid)));
                                    require(
                                            shell != null
                                                    && keys(
                                                            shell,
                                                            "uid",
                                                            "schemaVersion",
                                                            "createdAt")
                                                    && t.uid.equals(shell.get("uid"))
                                                    && Long.valueOf(1)
                                                            .equals(shell.get("schemaVersion"))
                                                    && subject != null
                                                    && "active".equals(subject.get("status")),
                                            "denied");
                                    require(
                                            Objects.equals(
                                                    slot.get("activeListingId"),
                                                    create ? null : c.listingId),
                                            "conflict");
                                    Map<String, Object> emptySlot = new HashMap<>(slot);
                                    emptySlot.put("activeListingId", null);
                                    require(
                                            PetOfferAuthority.evaluate(
                                                            c.petId,
                                                            t.uid,
                                                            version(c.base, "identityVersion"),
                                                            true,
                                                            pet,
                                                            claim,
                                                            authority,
                                                            gate,
                                                            emptySlot)
                                                    == PetOfferAuthority.Reason.ALLOWED_FOR_DEMO,
                                            "denied");
                                    require(
                                            version(pet, "custodyVersion")
                                                    == version(c.base, "custodyVersion"),
                                            "conflict");
                                } else
                                    require(
                                            "closed".equals(old.get("lifecycle"))
                                                            && "archive".equals(c.command)
                                                    || Objects.equals(
                                                            slot.get("activeListingId"),
                                                            c.listingId),
                                            "conflict");
                            }
                            require(create || !"archived".equals(old.get("lifecycle")), "denied");
                            if ("edit".equals(c.command) || "close".equals(c.command))
                                require("draft".equals(old.get("lifecycle")), "denied");
                            Object now = FieldValue.serverTimestamp();
                            long cv = version(old, "contentVersion") + (material ? 1 : 0),
                                    ctl = version(old, "controlVersion") + 1;
                            long sv =
                                    slot == null
                                            ? version(c.base, "slotVersion")
                                            : version(slot, "slotVersion");
                            Map<String, Object> next =
                                    create
                                            ? map(
                                                    "schemaVersion",
                                                    1L,
                                                    "listingId",
                                                    c.listingId,
                                                    "ownerUid",
                                                    t.uid,
                                                    "petId",
                                                    c.petId,
                                                    "createdAt",
                                                    now,
                                                    "lifecycle",
                                                    "draft",
                                                    "closureReason",
                                                    null,
                                                    "closedAt",
                                                    null,
                                                    "archivedAt",
                                                    null)
                                            : new HashMap<>(old);
                            next.putAll(
                                    map(
                                            "contentVersion",
                                            cv,
                                            "activeRevisionId",
                                            "v" + cv,
                                            "controlVersion",
                                            ctl,
                                            "visibility",
                                            "hidden",
                                            "updatedAt",
                                            now,
                                            "lastOperationId",
                                            c.operationId));
                            if ("close".equals(c.command) || "archive".equals(c.command)) {
                                next.put(
                                        "lifecycle",
                                        "close".equals(c.command) ? "closed" : "archived");
                                next.put("closureReason", "no_longer_offered");
                                if (next.get("closedAt") == null) next.put("closedAt", now);
                                if ("archive".equals(c.command)) next.put("archivedAt", now);
                            }
                            boolean slotChange =
                                    create
                                            || slot != null
                                                    && c.listingId.equals(
                                                            slot.get("activeListingId"))
                                                    && ("close".equals(c.command)
                                                            || "archive".equals(c.command));
                            if (slotChange) sv++;
                            Map<String, Object> result =
                                    map(
                                            "contentVersion",
                                            cv,
                                            "controlVersion",
                                            ctl,
                                            "slotVersion",
                                            sv);
                            Map<String, Object> rec =
                                    map(
                                            "schemaVersion",
                                            1L,
                                            "command",
                                            c.command,
                                            "actorUid",
                                            t.uid,
                                            "listingId",
                                            c.listingId,
                                            "petId",
                                            c.petId,
                                            "operationId",
                                            c.operationId,
                                            "base",
                                            c.base,
                                            "result",
                                            result,
                                            "input",
                                            c.input,
                                            "committedAt",
                                            now,
                                            "status",
                                            "committed");
                            require(valid(t), "session_changed");
                            tx.set(rr, next);
                            if (material)
                                tx.set(
                                        ref(root(c.listingId) + "/revisions/v" + cv),
                                        map(
                                                "schemaVersion",
                                                1L,
                                                "listingId",
                                                c.listingId,
                                                "ownerUid",
                                                t.uid,
                                                "petId",
                                                c.petId,
                                                "contentVersion",
                                                cv,
                                                "editableFields",
                                                c.input,
                                                "petIdentityVersion",
                                                version(pet, "identityVersion"),
                                                "custodyVersion",
                                                version(pet, "custodyVersion"),
                                                "identitySnapshot",
                                                nested(identity, "identity"),
                                                "careVersion",
                                                null,
                                                "mediaVersion",
                                                null,
                                                "createdAt",
                                                now,
                                                "operationId",
                                                c.operationId));
                            if (slotChange) {
                                Map<String, Object> updated = new HashMap<>(slot);
                                updated.putAll(
                                        map(
                                                "activeListingId",
                                                create ? c.listingId : null,
                                                "slotVersion",
                                                sv,
                                                "updatedAt",
                                                now));
                                tx.set(ref("petListingSlots/" + c.petId), updated);
                            }
                            tx.set(receipt, rec);
                            return rec;
                        });
        return wrap(task, t, true)
                .continueWithTask(
                        done -> {
                            Result<Map<String, Object>> r = done.getResult();
                            if (r.code == Code.UNKNOWN_OUTCOME || r.code == Code.DENIED)
                                return reconcile(t, c)
                                        .continueWith(
                                                x ->
                                                        x.getResult().code == Code.SERVER
                                                                ? new Result<>(
                                                                        Code.COMMITTED,
                                                                        x.getResult().value)
                                                                : r);
                            return Tasks.forResult(r);
                        });
    }

    private <T> Task<Result<T>> wrap(Task<T> task, SessionEpoch.Ticket t, boolean write) {
        return task.continueWith(
                done -> {
                    if (!valid(t)) return new Result<>(Code.SESSION_CHANGED, null);
                    if (done.isSuccessful())
                        return new Result<>(write ? Code.COMMITTED : Code.SERVER, done.getResult());
                    Throwable e = done.getException();
                    while (e != null) {
                        String m = e.getMessage();
                        if ("conflict".equals(m)) return new Result<>(Code.CONFLICT, null);
                        if ("unknown_schema".equals(m))
                            return new Result<>(Code.UNKNOWN_SCHEMA, null);
                        if ("not_found".equals(m)) return new Result<>(Code.NOT_FOUND, null);
                        if ("denied".equals(m)) return new Result<>(Code.DENIED, null);
                        if (e instanceof FirebaseFirestoreException) {
                            FirebaseFirestoreException.Code c =
                                    ((FirebaseFirestoreException) e).getCode();
                            if (c == FirebaseFirestoreException.Code.PERMISSION_DENIED
                                    || c == FirebaseFirestoreException.Code.UNAUTHENTICATED)
                                return new Result<>(Code.DENIED, null);
                            if (c == FirebaseFirestoreException.Code.ABORTED)
                                return new Result<>(Code.CONFLICT, null);
                        }
                        e = e.getCause();
                    }
                    return new Result<>(write ? Code.UNKNOWN_OUTCOME : Code.UNAVAILABLE, null);
                });
    }
}
