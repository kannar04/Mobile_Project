package com.gmail.manhle240406.project.pet;

import com.google.firebase.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** F06 schema1: stable IDs and physically separated private declarations. */
public final class PetSchema {
  private PetSchema() {}

  public static final List<String> SEXES = Arrays.asList("unknown", "female", "male");
  public static final List<String> BASES =
      Arrays.asList("claimed_owner", "authorized_caregiver", "other_permission");

  public static Map<String, Object> map(Object... pairs) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
    return m;
  }

  public static boolean keys(Map<String, Object> m, String... keys) {
    return m != null && m.keySet().equals(new HashSet<>(Arrays.asList(keys)));
  }

  public static boolean id(Object id) {
    return id instanceof String
        && ((String) id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }

  public static boolean number(Object n, long min) {
    return n instanceof Long && (Long) n >= min;
  }

  public static boolean text(Object x, int min, int max, int bytes) {
    if (!(x instanceof String)) return false;
    String s = (String) x;
    int n = s.codePointCount(0, s.length());
    return s.equals(s.trim())
        && n >= min
        && n <= max
        && s.getBytes(StandardCharsets.UTF_8).length <= bytes
        && (s.isEmpty()
            || s.codePoints()
                .anyMatch(c -> !Character.isISOControl(c) && !Character.isWhitespace(c)));
  }

  public static boolean identity(Map<String, Object> d) {
    if (!keys(
        d,
        "displayName",
        "species",
        "breed",
        "sex",
        "ageMonths",
        "ageObservedAt",
        "marks",
        "marksUnknown",
        "illustrationKey")) return false;
    Object age = d.get("ageMonths"),
        observed = d.get("ageObservedAt"),
        unknown = d.get("marksUnknown");
    return text(d.get("displayName"), 0, 80, 320)
        && text(d.get("species"), 1, 40, 160)
        && text(d.get("breed"), 0, 80, 320)
        && SEXES.contains(d.get("sex"))
        && ((age == null && observed == null)
            || number(age, 0) && (Long) age <= 1200 && observed instanceof Timestamp)
        && unknown instanceof Boolean
        && text(d.get("marks"), Boolean.TRUE.equals(unknown) ? 0 : 1, 500, 2000)
        && (!Boolean.TRUE.equals(unknown) || "".equals(d.get("marks")))
        && Arrays.asList("default", "cat", "dog").contains(d.get("illustrationKey"));
  }

  public static boolean declaration(Map<String, Object> c) {
    return keys(c, "basis", "reportedCare", "explanation")
        && BASES.contains(c.get("basis"))
        && c.get("reportedCare") instanceof Boolean
        && text(c.get("explanation"), "claimed_owner".equals(c.get("basis")) ? 0 : 1, 1000, 3000);
  }

  public static boolean root(Map<String, Object> p, String id) {
    return keys(
            p,
            "schemaVersion",
            "petId",
            "registrarUid",
            "editorUid",
            "identityVersion",
            "currentRevisionId",
            "custodyVersion",
            "lifecycle",
            "createdAt",
            "updatedAt",
            "lastOperationId")
        && Long.valueOf(1).equals(p.get("schemaVersion"))
        && id.equals(p.get("petId"))
        && p.get("registrarUid") instanceof String
        && p.get("editorUid") instanceof String
        && number(p.get("identityVersion"), 1)
        && number(p.get("custodyVersion"), 0)
        && id(p.get("currentRevisionId"))
        && id(p.get("lastOperationId"))
        && Arrays.asList("active", "archived").contains(p.get("lifecycle"))
        && p.get("createdAt") instanceof Timestamp
        && p.get("updatedAt") instanceof Timestamp;
  }

  public static boolean revision(Map<String, Object> r, String pet, long v) {
    return keys(
            r,
            "schemaVersion",
            "petId",
            "version",
            "actorUid",
            "identity",
            "operationId",
            "createdAt")
        && Long.valueOf(1).equals(r.get("schemaVersion"))
        && pet.equals(r.get("petId"))
        && Long.valueOf(v).equals(r.get("version"))
        && r.get("actorUid") instanceof String
        && id(r.get("operationId"))
        && r.get("createdAt") instanceof Timestamp
        && r.get("identity") instanceof Map
        && identity(nested(r, "identity"));
  }

  public static boolean claim(Map<String, Object> c, String pet, String uid) {
    return keys(
            c,
            "schemaVersion",
            "petId",
            "claimantUid",
            "claimVersion",
            "identityVersion",
            "basis",
            "reportedCare",
            "explanation",
            "reviewState",
            "submittedRevisionId",
            "submittedAt",
            "createdAt",
            "updatedAt",
            "lastOperationId")
        && Long.valueOf(1).equals(c.get("schemaVersion"))
        && pet.equals(c.get("petId"))
        && uid.equals(c.get("claimantUid"))
        && number(c.get("claimVersion"), 1)
        && number(c.get("identityVersion"), 1)
        && declaration(declarationOf(c))
        && Arrays.asList(
                "draft",
                "submitted",
                "needs_information",
                "accepted_for_demo",
                "rejected",
                "withdrawn")
            .contains(c.get("reviewState"))
        && (c.get("submittedRevisionId") == null || id(c.get("submittedRevisionId")))
        && (c.get("submittedAt") == null || c.get("submittedAt") instanceof Timestamp)
        && c.get("createdAt") instanceof Timestamp
        && c.get("updatedAt") instanceof Timestamp
        && id(c.get("lastOperationId"));
  }

  public static Map<String, Object> declarationOf(Map<String, Object> c) {
    return map(
        "basis",
        c.get("basis"),
        "reportedCare",
        c.get("reportedCare"),
        "explanation",
        c.get("explanation"));
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> nested(Map<String, Object> m, String key) {
    return (Map<String, Object>) m.get(key);
  }

  public static long version(Map<String, Object> m, String key) {
    return m == null ? 0 : (Long) m.get(key);
  }

  public static boolean privateChip(Map<String, Object> m, String uid, String pet) {
    return keys(
            m,
            "schemaVersion",
            "ownerUid",
            "petId",
            "chip",
            "notes",
            "verification",
            "privateVersion",
            "lastOperationId",
            "createdAt",
            "updatedAt")
        && Long.valueOf(1).equals(m.get("schemaVersion"))
        && uid.equals(m.get("ownerUid"))
        && pet.equals(m.get("petId"))
        && (m.get("chip") == null || text(m.get("chip"), 1, 64, 256))
        && text(m.get("notes"), 0, 500, 2000)
        && "unverified".equals(m.get("verification"))
        && number(m.get("privateVersion"), 1)
        && id(m.get("lastOperationId"))
        && m.get("createdAt") instanceof Timestamp
        && m.get("updatedAt") instanceof Timestamp;
  }
}
