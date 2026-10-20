package com.gmail.manhle240406.project.foundation;

import com.google.firebase.Timestamp;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Immutable typed documents; no arbitrary payload or business fields. */
public final class FoundationDocuments {
  private FoundationDocuments() {}

  private static Set<String> keys(String... values) {
    return new HashSet<>(Arrays.asList(values));
  }

  public static boolean validOperationId(String id) {
    return id != null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }

  private static void fields(Map<String, Object> data, Set<String> expected) {
    if (data == null || !data.keySet().equals(expected))
      throw new IllegalArgumentException("Invalid schema");
  }

  public static final class Config {
    public final String environmentLabel;
    public final Timestamp updatedAt;

    public Config(Map<String, Object> data) {
      fields(data, keys("schemaVersion", "environmentLabel", "featureMode", "updatedAt"));
      Object label = data.get("environmentLabel");
      if (!Long.valueOf(1).equals(data.get("schemaVersion"))
          || !"course_demo".equals(data.get("featureMode"))
          || !(label instanceof String)
          || ((String) label).isEmpty()
          || ((String) label).length() > 40
          || !(data.get("updatedAt") instanceof Timestamp))
        throw new IllegalArgumentException("Invalid config");
      environmentLabel = (String) label;
      updatedAt = (Timestamp) data.get("updatedAt");
    }
  }

  public static final class Authority {
    public final boolean active;

    public Authority(Map<String, Object> data) {
      fields(data, keys("status", "roles", "updatedAt"));
      Object roles = data.get("roles");
      if (!(roles instanceof java.util.List)
          || !keys("active", "restricted").contains(data.get("status"))
          || !(data.get("updatedAt") instanceof Timestamp)
          || !keys("member", "admin").containsAll((java.util.List<?>) roles))
        throw new IllegalArgumentException("Invalid authority");
      active = "active".equals(data.get("status"));
    }
  }

  public static final class Diagnostic {
    public final String operationId;
    public final Timestamp createdAt;

    public Diagnostic(Map<String, Object> data, String uid, String id) {
      fields(data, Set.of("ownerUid", "operationId", "schemaVersion", "createdAt"));
      if (!validOperationId(id)
          || !uid.equals(data.get("ownerUid"))
          || !id.equals(data.get("operationId"))
          || !Long.valueOf(1).equals(data.get("schemaVersion"))
          || !(data.get("createdAt") instanceof Timestamp))
        throw new IllegalArgumentException("Conflicting diagnostic");
      operationId = id;
      createdAt = (Timestamp) data.get("createdAt");
    }
  }
}
