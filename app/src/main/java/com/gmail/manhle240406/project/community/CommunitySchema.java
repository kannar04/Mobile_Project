package com.gmail.manhle240406.project.community;

import static com.gmail.manhle240406.project.pet.PetSchema.*;

import com.google.firebase.Timestamp;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Approved F31 private slice; no reviewer capability or public projection is invented here. */
public final class CommunitySchema {
    private CommunitySchema() {}

    public static final List<String> ILLUSTRATIONS = Arrays.asList(null, "default", "cat", "dog");

    public static Map<String, Object> fields(String body, String illustration) {
        return map("body", body, "bundledIllustrationKey", illustration);
    }

    public static boolean fields(Map<String, Object> f) {
        return keys(f, "body", "bundledIllustrationKey")
                && text(f.get("body"), 1, 2000, 8000)
                && ILLUSTRATIONS.contains(f.get("bundledIllustrationKey"))
                && ((String) f.get("body")).getBytes(StandardCharsets.UTF_8).length
                                + (f.get("bundledIllustrationKey") == null
                                        ? 0
                                        : ((String) f.get("bundledIllustrationKey")).length())
                                + 100
                        <= 8192;
    }

    public static boolean root(Map<String, Object> p, String id) {
        return keys(
                        p,
                        "schemaVersion",
                        "postId",
                        "authorUid",
                        "type",
                        "lifecycle",
                        "contentVersion",
                        "currentRevisionId",
                        "controlVersion",
                        "visibility",
                        "submittedVersion",
                        "publicationConsent",
                        "moderatorHold",
                        "firstPublishedAt",
                        "createdAt",
                        "updatedAt",
                        "lastOperationId")
                && Long.valueOf(1).equals(p.get("schemaVersion"))
                && id(id)
                && id.equals(p.get("postId"))
                && p.get("authorUid") instanceof String
                && "community".equals(p.get("type"))
                && Arrays.asList("active", "archived").contains(p.get("lifecycle"))
                && number(p.get("contentVersion"), 1)
                && ("v" + p.get("contentVersion")).equals(p.get("currentRevisionId"))
                && number(p.get("controlVersion"), 1)
                && "hidden".equals(p.get("visibility"))
                && p.get("submittedVersion") == null
                && Boolean.FALSE.equals(p.get("publicationConsent"))
                && Arrays.asList("clear", "held").contains(p.get("moderatorHold"))
                && (p.get("firstPublishedAt") == null
                        || p.get("firstPublishedAt") instanceof Timestamp)
                && p.get("createdAt") instanceof Timestamp
                && p.get("updatedAt") instanceof Timestamp
                && id(p.get("lastOperationId"));
    }

    public static boolean revision(Map<String, Object> r, String id, long v) {
        return keys(
                        r,
                        "schemaVersion",
                        "postId",
                        "authorUid",
                        "type",
                        "contentVersion",
                        "body",
                        "bundledIllustrationKey",
                        "createdAt",
                        "operationId")
                && Long.valueOf(1).equals(r.get("schemaVersion"))
                && id.equals(r.get("postId"))
                && r.get("authorUid") instanceof String
                && "community".equals(r.get("type"))
                && Long.valueOf(v).equals(r.get("contentVersion"))
                && r.get("body") instanceof String
                && (r.get("bundledIllustrationKey") == null
                        || r.get("bundledIllustrationKey") instanceof String)
                && fields(fields((String) r.get("body"), (String) r.get("bundledIllustrationKey")))
                && r.get("createdAt") instanceof Timestamp
                && id(r.get("operationId"));
    }

    public static boolean versions(Map<String, Object> v) {
        return keys(v, "contentVersion", "controlVersion")
                && number(v.get("contentVersion"), 0)
                && number(v.get("controlVersion"), 0);
    }

    public static boolean receipt(Map<String, Object> r, String uid, String op) {
        return keys(
                        r,
                        "schemaVersion",
                        "ownerUid",
                        "operationId",
                        "postId",
                        "command",
                        "base",
                        "result",
                        "input",
                        "status",
                        "committedAt")
                && Long.valueOf(1).equals(r.get("schemaVersion"))
                && uid.equals(r.get("ownerUid"))
                && op.equals(r.get("operationId"))
                && id(op)
                && id(r.get("postId"))
                && Arrays.asList("create", "edit", "hide", "archive").contains(r.get("command"))
                && r.get("base") instanceof Map
                && versions(nested(r, "base"))
                && r.get("result") instanceof Map
                && versions(nested(r, "result"))
                && number(nested(r, "result").get("contentVersion"), 1)
                && number(nested(r, "result").get("controlVersion"), 1)
                && r.get("input") instanceof Map
                && ("create".equals(r.get("command")) || "edit".equals(r.get("command"))
                        ? fields(nested(r, "input"))
                        : nested(r, "input").isEmpty())
                && "committed".equals(r.get("status"))
                && r.get("committedAt") instanceof Timestamp;
    }
}
