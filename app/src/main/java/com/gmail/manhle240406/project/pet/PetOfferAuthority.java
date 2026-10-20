package com.gmail.manhle240406.project.pet;

import java.util.Map;
import java.util.Objects;

/** Foreground UI hint only: future offering commands must recheck gates atomically in rules. */
public final class PetOfferAuthority {
  public enum Reason {
    ALLOWED_FOR_DEMO,
    NOT_READY,
    UNKNOWN_GATE,
    NOT_EDITOR,
    ARCHIVED,
    STALE_REVIEW,
    HELD,
    BLOCKED,
    ACTIVE_OBLIGATIONS
  }

  private PetOfferAuthority() {}

  public static boolean authority(Map<String, Object> a, String pet) {
    return PetSchema.keys(
            a,
            "schemaVersion",
            "petId",
            "offerUid",
            "claimUid",
            "claimVersion",
            "identityVersion",
            "authorityVersion",
            "status",
            "hold",
            "decisionId",
            "custodyVersion",
            "updatedAt",
            "lastOperationId")
        && Long.valueOf(1).equals(a.get("schemaVersion"))
        && pet.equals(a.get("petId"))
        && (a.get("offerUid") == null || a.get("offerUid") instanceof String)
        && a.get("claimUid") instanceof String
        && PetSchema.number(a.get("claimVersion"), 1)
        && PetSchema.number(a.get("identityVersion"), 1)
        && PetSchema.number(a.get("authorityVersion"), 0)
        && PetSchema.number(a.get("custodyVersion"), 0)
        && java.util.Arrays.asList("blocked", "enabled", "suspended").contains(a.get("status"))
        && java.util.Arrays.asList("clear", "held").contains(a.get("hold"))
        && (a.get("decisionId") == null || PetSchema.id(a.get("decisionId")))
        && PetSchema.id(a.get("lastOperationId"))
        && a.get("updatedAt") instanceof com.google.firebase.Timestamp;
  }

  public static boolean slot(Map<String, Object> s, String pet) {
    return PetSchema.keys(
            s, "schemaVersion", "petId", "activeListingId", "slotVersion", "updatedAt")
        && Long.valueOf(1).equals(s.get("schemaVersion"))
        && pet.equals(s.get("petId"))
        && (s.get("activeListingId") == null || PetSchema.id(s.get("activeListingId")))
        && PetSchema.number(s.get("slotVersion"), 0)
        && s.get("updatedAt") instanceof com.google.firebase.Timestamp;
  }

  public static boolean gate(Map<String, Object> g, String pet) {
    return PetSchema.keys(
            g,
            "schemaVersion",
            "petId",
            "state",
            "activeReservationId",
            "activePlacementId",
            "gateVersion",
            "custodyVersion",
            "updatedAt")
        && Long.valueOf(1).equals(g.get("schemaVersion"))
        && pet.equals(g.get("petId"))
        && java.util.Arrays.asList("none", "reserved", "placed", "unknown").contains(g.get("state"))
        && (g.get("activeReservationId") == null || PetSchema.id(g.get("activeReservationId")))
        && (g.get("activePlacementId") == null || PetSchema.id(g.get("activePlacementId")))
        && PetSchema.number(g.get("gateVersion"), 0)
        && PetSchema.number(g.get("custodyVersion"), 0)
        && g.get("updatedAt") instanceof com.google.firebase.Timestamp;
  }

  public static Reason evaluate(
      String pet,
      String uid,
      long expectedIdentity,
      boolean ready,
      Map<String, Object> p,
      Map<String, Object> c,
      Map<String, Object> a,
      Map<String, Object> g,
      Map<String, Object> s) {
    if (!ready || uid == null) return Reason.NOT_READY;
    if (!PetSchema.root(p, pet)
        || !PetSchema.claim(c, pet, uid)
        || !authority(a, pet)
        || !gate(g, pet)
        || !slot(s, pet)) return Reason.UNKNOWN_GATE;
    if (!uid.equals(p.get("editorUid"))
        || !uid.equals(a.get("offerUid"))
        || !uid.equals(a.get("claimUid"))) return Reason.NOT_EDITOR;
    if (!"active".equals(p.get("lifecycle"))) return Reason.ARCHIVED;
    if (expectedIdentity != PetSchema.version(p, "identityVersion")
        || !Objects.equals(p.get("identityVersion"), a.get("identityVersion"))
        || !Objects.equals(p.get("identityVersion"), c.get("identityVersion"))
        || !Objects.equals(c.get("claimVersion"), a.get("claimVersion"))
        || !Objects.equals(p.get("custodyVersion"), a.get("custodyVersion"))
        || !Objects.equals(p.get("custodyVersion"), g.get("custodyVersion"))
        || !"accepted_for_demo".equals(c.get("reviewState"))
        || !PetSchema.id(a.get("decisionId"))) return Reason.STALE_REVIEW;
    if (!"clear".equals(a.get("hold"))) return Reason.HELD;
    if (!"enabled".equals(a.get("status"))) return Reason.BLOCKED;
    if (!"none".equals(g.get("state"))
        || g.get("activeReservationId") != null
        || g.get("activePlacementId") != null
        || s.get("activeListingId") != null) return Reason.ACTIVE_OBLIGATIONS;
    return Reason.ALLOWED_FOR_DEMO;
  }
}
