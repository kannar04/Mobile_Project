package com.gmail.manhle240406.project.community;

import com.gmail.manhle240406.project.pet.PetSchema;
import com.google.firebase.Timestamp;
import java.util.*;

/** Unsupported server data is never interpreted as a public card or a successful receipt. */
public final class SocialSchema {
  private SocialSchema() {}

  private static boolean keys(Map<String, Object> m, String... names) {
    return m != null && m.keySet().equals(new HashSet<>(Arrays.asList(names)));
  }

  private static boolean positive(Object n) {
    return n instanceof Long && (Long) n >= 1;
  }

  public static boolean feed(Map<String, Object> m, String id) {
    if (m != null && Long.valueOf(6).equals(m.get("schemaVersion"))) {
      if (!"adoption".equals(m.get("type"))
          || !"community".equals(m.get("audience"))
          || !com.gmail.manhle240406.project.pet.PetSchema.id(m.get("listingId"))
          || !positive(m.get("listingContentVersion"))
          || !positive(m.get("listingPublicationVersion"))) return false;
      Map<String, Object> old = new HashMap<>(m);
      old.remove("listingId");
      old.remove("listingContentVersion");
      old.remove("listingPublicationVersion");
      old.put("schemaVersion", 5L);
      old.put("type", "community");
      return feed(old, id);
    }

    if (m == null) return false;
    Object ver = m.get("schemaVersion");
    if (Long.valueOf(5).equals(ver)) {
      if (!m.containsKey("mediaAssetIds")
          || !m.containsKey("manifestVersion")
          || !m.get("contentVersion").equals(m.get("manifestVersion"))) return false;
      java.util.List<String> ids =
          com.gmail.manhle240406.project.media.MediaGrid.ids(m.get("mediaAssetIds"));
      if (!(m.get("mediaAssetIds") instanceof java.util.List)
          || ids.size() != ((java.util.List<?>) m.get("mediaAssetIds")).size()) return false;
      Map<String, Object> old = new HashMap<>(m);
      old.remove("mediaAssetIds");
      old.remove("manifestVersion");
      old.put("schemaVersion", "everyone".equals(m.get("audience")) ? 4L : 3L);
      return feed(old, id);
    }
    if (Long.valueOf(2).equals(ver)) {
      return keys(
              m,
              "schemaVersion",
              "postId",
              "authorUid",
              "type",
              "contentVersion",
              "body",
              "bundledIllustrationKey",
              "audience",
              "state",
              "firstPublishedAt",
              "editedAt")
          && id.equals(m.get("postId"))
          && PetSchema.id(id)
          && m.get("authorUid") instanceof String
          && "community".equals(m.get("type"))
          && positive(m.get("contentVersion"))
          && m.get("body") instanceof String
          && (m.get("bundledIllustrationKey") == null
              || m.get("bundledIllustrationKey") instanceof String)
          && CommunitySchema.fields(
              CommunitySchema.fields(
                  (String) m.get("body"), (String) m.get("bundledIllustrationKey")))
          && "community".equals(m.get("audience"))
          && "visible".equals(m.get("state"))
          && m.get("firstPublishedAt") instanceof Timestamp
          && (m.get("editedAt") == null || m.get("editedAt") instanceof Timestamp);
    } else if (Long.valueOf(3).equals(ver) || Long.valueOf(4).equals(ver)) {
      return keys(
              m,
              "schemaVersion",
              "postId",
              "authorUid",
              "type",
              "contentVersion",
              "controlVersion",
              "body",
              "bundledIllustrationKey",
              "audience",
              "state",
              "firstPublishedAt",
              "editedAt")
          && id.equals(m.get("postId"))
          && PetSchema.id(id)
          && m.get("authorUid") instanceof String
          && "community".equals(m.get("type"))
          && positive(m.get("contentVersion"))
          && positive(m.get("controlVersion"))
          && m.get("body") instanceof String
          && (m.get("bundledIllustrationKey") == null
              || m.get("bundledIllustrationKey") instanceof String)
          && CommunitySchema.fields(
              CommunitySchema.fields(
                  (String) m.get("body"), (String) m.get("bundledIllustrationKey")))
          && (Long.valueOf(4).equals(ver) ? "everyone" : "community").equals(m.get("audience"))
          && "visible".equals(m.get("state"))
          && m.get("firstPublishedAt") instanceof Timestamp
          && (m.get("editedAt") == null || m.get("editedAt") instanceof Timestamp);
    }
    return false;
  }

  public static boolean comment(Map<String, Object> m, String post, String id) {
    return keys(
            m,
            "schemaVersion",
            "postId",
            "commentId",
            "authorUid",
            "commentVersion",
            "sourceContentVersion",
            "body",
            "state",
            "createdAt",
            "editedAt")
        && Long.valueOf(2).equals(m.get("schemaVersion"))
        && post.equals(m.get("postId"))
        && id.equals(m.get("commentId"))
        && PetSchema.id(id)
        && m.get("authorUid") instanceof String
        && positive(m.get("commentVersion"))
        && positive(m.get("sourceContentVersion"))
        && m.get("body") instanceof String
        && SocialRepository.text((String) m.get("body"), 500, 2000)
        && "visible".equals(m.get("state"))
        && m.get("createdAt") instanceof Timestamp
        && (m.get("editedAt") == null || m.get("editedAt") instanceof Timestamp);
  }

  public static boolean reportRecord(Map<String, Object> m, String uid) {
    return keys(
            m,
            "schemaVersion",
            "reporterUid",
            "targetKind",
            "targetId",
            "parentId",
            "observedVersion",
            "category",
            "statement",
            "evidenceText",
            "snapshotProvenance",
            "sourceRef",
            "status",
            "statusVersion",
            "createdAt",
            "lastReporterVersion",
            "opId",
            "lastReporterOperationId")
        && Long.valueOf(1).equals(m.get("schemaVersion"))
        && uid.equals(m.get("reporterUid"))
        && Arrays.asList("post", "comment").contains(m.get("targetKind"))
        && PetSchema.id(m.get("targetId"))
        && positive(m.get("observedVersion"))
        && ReportCommand.CATEGORIES.contains(m.get("category"))
        && m.get("statement") instanceof String
        && ((String) m.get("statement")).codePointCount(0, ((String) m.get("statement")).length())
            <= 1000
        && Arrays.asList(
                "received",
                "in_review",
                "needs_information",
                "resolved",
                "rejected",
                "duplicate",
                "resolved_no_action",
                "action_recorded",
                "needs_specialist")
            .contains(m.get("status"))
        && positive(m.get("statusVersion"))
        && positive(m.get("lastReporterVersion"))
        && m.get("createdAt") instanceof Timestamp
        && PetSchema.id(m.get("opId"))
        && PetSchema.id(m.get("lastReporterOperationId"));
  }

  public static boolean status(Map<String, Object> m, String id) {
    return keys(
            m,
            "schemaVersion",
            "reportId",
            "targetKind",
            "targetId",
            "parentId",
            "category",
            "status",
            "statusVersion",
            "response",
            "createdAt",
            "updatedAt")
        && Long.valueOf(1).equals(m.get("schemaVersion"))
        && id.equals(m.get("reportId"))
        && ReportCommand.CATEGORIES.contains(m.get("category"))
        && positive(m.get("statusVersion"))
        && Arrays.asList(
                "received",
                "in_review",
                "needs_information",
                "resolved",
                "rejected",
                "duplicate",
                "resolved_no_action",
                "action_recorded",
                "needs_specialist")
            .contains(m.get("status"))
        && m.get("createdAt") instanceof Timestamp
        && m.get("updatedAt") instanceof Timestamp;
  }

  public static boolean receipt(Map<String, Object> m, String uid, String op, String post) {
    return keys(
            m,
            "schemaVersion",
            "ownerUid",
            "operationId",
            "postId",
            "targetId",
            "command",
            "base",
            "result",
            "input",
            "status",
            "committedAt")
        && Long.valueOf(2).equals(m.get("schemaVersion"))
        && uid.equals(m.get("ownerUid"))
        && op.equals(m.get("operationId"))
        && post.equals(m.get("postId"))
        && "committed".equals(m.get("status"))
        && m.get("command") instanceof String
        && m.get("base") instanceof Map
        && m.get("result") instanceof Map
        && m.get("input") instanceof Map
        && m.get("committedAt") instanceof Timestamp;
  }

  public static boolean reportReceipt(Map<String, Object> m, String uid, String op, String report) {
    return keys(
            m,
            "schemaVersion",
            "ownerUid",
            "operationId",
            "command",
            "reportId",
            "targetKey",
            "baseVersion",
            "resultVersion",
            "input",
            "committedAt")
        && Long.valueOf(1).equals(m.get("schemaVersion"))
        && uid.equals(m.get("ownerUid"))
        && op.equals(m.get("operationId"))
        && report.equals(m.get("reportId"))
        && Arrays.asList("submit", "append", "withdraw").contains(m.get("command"))
        && m.get("targetKey") instanceof String
        && m.get("baseVersion") instanceof Long
        && (Long) m.get("baseVersion") >= 0
        && positive(m.get("resultVersion"))
        && m.get("input") instanceof Map
        && m.get("committedAt") instanceof Timestamp;
  }
}
