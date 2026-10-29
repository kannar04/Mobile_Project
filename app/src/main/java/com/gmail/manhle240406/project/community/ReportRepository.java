package com.gmail.manhle240406.project.community;

import static com.gmail.manhle240406.project.community.SocialRepository.*;

import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

public final class ReportRepository {
    private final FirebaseFirestore db;
    private final SessionGateway sessions;

    public ReportRepository(FirebaseFirestore db, SessionGateway s) {
        this.db = db;
        sessions = s;
    }

    public Task<QuerySnapshot> mine(String uid, DocumentSnapshot cursor) {
        Query q =
                db.collection("users/" + uid + "/reportStatus")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                        .limit(20);
        if (cursor != null) q = q.startAfter(cursor);
        return Tasks.withTimeout(q.get(Source.SERVER), 15, TimeUnit.SECONDS);
    }

    public Task<DocumentSnapshot> read(String path) {
        return Tasks.withTimeout(db.document(path).get(Source.SERVER), 15, TimeUnit.SECONDS);
    }

    public Task<Map<String, Object>> execute(SessionEpoch.Ticket ticket, ReportCommand c) {
        Task<Map<String, Object>> attempt =
                db.runTransaction(
                        new TransactionOptions.Builder().setMaxAttempts(3).build(),
                        tx -> {
                            if (ticket.uid == null || !sessions.current(ticket))
                                throw new IllegalStateException("session_changed");
                            String uid = ticket.uid,
                                    root = "reports/" + c.reportId,
                                    own = "users/" + uid + "/";
                            Object now = FieldValue.serverTimestamp();
                            DocumentReference operation =
                                    db.document(own + "reportOperations/" + c.operationId);
                            Map<String, Object> prior = tx.get(operation).getData();
                            if (prior != null) {
                                if (!c.matches(prior)) throw new IllegalStateException("conflict");
                                return prior;
                            }
                            Map<String, Object> link =
                                    tx.get(db.document(own + "reportLinks/" + c.targetKey()))
                                            .getData();
                            if (c.command.equals("submit") && link != null)
                                return map("existingReportId", link.get("currentReportId"));
                            Map<String, Object> report = tx.get(db.document(root)).getData();
                            Map<String, Object> rate =
                                    c.command.equals("withdraw")
                                            ? null
                                            : tx.get(db.document("reportRateControls/" + uid))
                                                    .getData();
                            String source =
                                    c.kind.equals("post")
                                            ? "communityFeed/" + c.targetId
                                            : "communityCommentFeed/"
                                                    + c.parentId
                                                    + "/comments/"
                                                    + c.targetId;
                            // A stale/removed target becomes a reporter statement, never a
                            // hidden-source read.
                            Map<String, Object> projection = null;
                            if (!c.command.equals("withdraw")) {
                                Map<String, Object> parent =
                                        tx.get(
                                                        db.document(
                                                                "communityFeed/"
                                                                        + (c.parentId == null
                                                                                ? c.targetId
                                                                                : c.parentId)))
                                                .getData();
                                if (parent != null)
                                    projection =
                                            c.kind.equals("post")
                                                    ? parent
                                                    : tx.get(db.document(source)).getData();
                            }
                            long version = c.baseVersion + 1;
                            if (number(report, "lastReporterVersion") != c.baseVersion)
                                throw new IllegalStateException("conflict");
                            if (report != null && !uid.equals(report.get("reporterUid")))
                                throw new IllegalStateException("denied");
                            boolean current =
                                    projection != null
                                            && projection.get("body") instanceof String
                                            && ((String) projection.get("body"))
                                                            .getBytes(
                                                                    java.nio.charset
                                                                            .StandardCharsets.UTF_8)
                                                            .length
                                                    <= 3072
                                            && number(
                                                            projection,
                                                            c.kind.equals("post")
                                                                    ? "contentVersion"
                                                                    : "commentVersion")
                                                    == c.observedVersion;
                            String provenance =
                                    current
                                            ? "current_authorized_projection"
                                            : "reporter_statement";
                            Map<String, Object> revision =
                                    map(
                                            "schemaVersion",
                                            1L,
                                            "reportId",
                                            c.reportId,
                                            "reporterUid",
                                            uid,
                                            "reporterVersion",
                                            version,
                                            "command",
                                            c.command,
                                            "statement",
                                            c.input.get("statement"),
                                            "evidenceText",
                                            c.input.get("evidenceText"),
                                            "observedVersion",
                                            c.observedVersion,
                                            "snapshotProvenance",
                                            provenance,
                                            "sourceRef",
                                            current ? source : null,
                                            "operationId",
                                            c.operationId,
                                            "createdAt",
                                            now);
                            Map<String, Object> next;
                            if (report == null) {
                                next =
                                        map(
                                                "schemaVersion",
                                                1L,
                                                "reporterUid",
                                                uid,
                                                "targetKind",
                                                c.kind,
                                                "targetId",
                                                c.targetId,
                                                "parentId",
                                                c.parentId,
                                                "observedVersion",
                                                c.observedVersion,
                                                "category",
                                                c.input.get("category"),
                                                "statement",
                                                c.input.get("statement"),
                                                "evidenceText",
                                                c.input.get("evidenceText"),
                                                "snapshotProvenance",
                                                provenance,
                                                "sourceRef",
                                                current ? source : null,
                                                "status",
                                                "received",
                                                "statusVersion",
                                                1L,
                                                "createdAt",
                                                now,
                                                "lastReporterVersion",
                                                1L,
                                                "opId",
                                                c.operationId,
                                                "lastReporterOperationId",
                                                c.operationId);
                            } else {
                                next = new HashMap<>(report);
                                next.put("lastReporterVersion", version);
                                next.put("lastReporterOperationId", c.operationId);
                            }
                            long count = 1, rateVersion = number(rate, "version") + 1;
                            Object window = now;
                            if (rate != null) {
                                Timestamp last = (Timestamp) rate.get("lastAcceptedAt"),
                                        start = (Timestamp) rate.get("windowStart");
                                long seconds = System.currentTimeMillis() / 1000;
                                if (seconds - last.getSeconds() < 5)
                                    throw new IllegalStateException("rate");
                                if (seconds - start.getSeconds() < 86400) {
                                    count = number(rate, "count") + 1;
                                    window = start;
                                }
                                if (count > 30) throw new IllegalStateException("rate");
                            }
                            Map<String, Object> receipt =
                                    map(
                                            "schemaVersion",
                                            1L,
                                            "ownerUid",
                                            uid,
                                            "operationId",
                                            c.operationId,
                                            "command",
                                            c.command,
                                            "reportId",
                                            c.reportId,
                                            "targetKey",
                                            c.targetKey(),
                                            "baseVersion",
                                            c.baseVersion,
                                            "resultVersion",
                                            version,
                                            "input",
                                            c.input,
                                            "committedAt",
                                            now);
                            if (!sessions.current(ticket))
                                throw new IllegalStateException("session_changed");
                            tx.set(db.document(root), next);
                            tx.set(db.document(root + "/reporterRevisions/v" + version), revision);
                            tx.set(operation, receipt);
                            if (report == null) {
                                // Snapshot is only an exact body/version of a readable projection.
                                // No privileged source lookup.
                                tx.set(
                                        db.document(root + "/restrictedEvidence/current"),
                                        map(
                                                "schemaVersion",
                                                1L,
                                                "reportId",
                                                c.reportId,
                                                "reporterUid",
                                                uid,
                                                "provenance",
                                                provenance,
                                                "sourceRef",
                                                current ? source : null,
                                                "observedVersion",
                                                c.observedVersion,
                                                "body",
                                                current ? projection.get("body") : null));
                                tx.set(
                                        db.document(own + "reportStatus/" + c.reportId),
                                        map(
                                                "schemaVersion",
                                                1L,
                                                "reportId",
                                                c.reportId,
                                                "targetKind",
                                                c.kind,
                                                "targetId",
                                                c.targetId,
                                                "parentId",
                                                c.parentId,
                                                "category",
                                                c.input.get("category"),
                                                "status",
                                                "received",
                                                "statusVersion",
                                                1L,
                                                "response",
                                                null,
                                                "createdAt",
                                                now,
                                                "updatedAt",
                                                now));
                                tx.set(
                                        db.document(own + "reportLinks/" + c.targetKey()),
                                        map(
                                                "schemaVersion",
                                                1L,
                                                "targetKind",
                                                c.kind,
                                                "targetId",
                                                c.targetId,
                                                "parentId",
                                                c.parentId,
                                                "currentReportId",
                                                c.reportId,
                                                "state",
                                                "open",
                                                "updatedAt",
                                                now));
                            }
                            if (!c.command.equals("withdraw"))
                                tx.set(
                                        db.document("reportRateControls/" + uid),
                                        map(
                                                "windowStart",
                                                window,
                                                "count",
                                                count,
                                                "lastAcceptedAt",
                                                now,
                                                "version",
                                                rateVersion,
                                                "lastReportId",
                                                c.reportId,
                                                "lastOperationId",
                                                c.operationId));
                            return receipt;
                        });
        return attempt.continueWithTask(
                t -> {
                    if (!sessions.current(ticket))
                        return Tasks.forException(new IllegalStateException("session_changed"));
                    if (t.isSuccessful()) return Tasks.forResult(t.getResult());
                    Exception failure = t.getException();
                    return read("users/" + ticket.uid + "/reportOperations/" + c.operationId)
                            .continueWithTask(
                                    r -> {
                                        if (!sessions.current(ticket))
                                            return Tasks.forException(
                                                    new IllegalStateException("session_changed"));
                                        if (r.isSuccessful()
                                                && r.getResult().exists()
                                                && c.matches(r.getResult().getData()))
                                            return Tasks.forResult(r.getResult().getData());
                                        if (!c.command.equals("submit"))
                                            return Tasks.forException(failure);
                                        return read("users/"
                                                        + ticket.uid
                                                        + "/reportLinks/"
                                                        + c.targetKey())
                                                .continueWithTask(
                                                        link -> {
                                                            if (!sessions.current(ticket))
                                                                return Tasks.forException(
                                                                        new IllegalStateException(
                                                                                "session_changed"));
                                                            Map<String, Object> found =
                                                                    link.isSuccessful()
                                                                            ? link.getResult()
                                                                                    .getData()
                                                                            : null;
                                                            if (found != null
                                                                    && c.kind.equals(
                                                                            found.get("targetKind"))
                                                                    && c.targetId.equals(
                                                                            found.get("targetId"))
                                                                    && Objects.equals(
                                                                            c.parentId,
                                                                            found.get("parentId"))
                                                                    && com.gmail.manhle240406
                                                                            .project.pet.PetSchema
                                                                            .id(
                                                                                    found.get(
                                                                                            "currentReportId")))
                                                                return Tasks.forResult(
                                                                        map(
                                                                                "existingReportId",
                                                                                found.get(
                                                                                        "currentReportId")));
                                                            return Tasks.forException(
                                                                    failure == null
                                                                            ? new IllegalStateException(
                                                                                    "unknown")
                                                                            : failure);
                                                        });
                                    });
                });
    }
}
