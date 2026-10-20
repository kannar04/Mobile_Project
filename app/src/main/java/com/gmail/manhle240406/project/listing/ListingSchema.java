package com.gmail.manhle240406.project.listing;

import static com.gmail.manhle240406.project.pet.PetSchema.*;

import com.gmail.manhle240406.project.pet.PetSchema;
import com.google.firebase.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Private F07 slice. Review, care, upload and participant records stay outside this schema. */
public final class ListingSchema {
  private ListingSchema() {}

  public static Map<String, Object> empty() {
    return map(
        "title",
        "",
        "description",
        "",
        "regionCode",
        null,
        "offerType",
        "adoption",
        "mediaRefs",
        Collections.emptyList());
  }

  public static boolean fields(Map<String, Object> f, boolean canonical) {
    return keys(f, "title", "description", "regionCode", "offerType", "mediaRefs")
        && text(f.get("title"), canonical ? 5 : 0, 80, 320)
        && text(f.get("description"), 0, 2000, 8000)
        && (f.get("regionCode") == null
            || Arrays.asList("north", "central", "south").contains(f.get("regionCode")))
        && "adoption".equals(f.get("offerType"))
        && f.get("mediaRefs") instanceof List
        && ((List<?>) f.get("mediaRefs")).isEmpty();
  }

  public static boolean root(Map<String, Object> r, String id) {
    return keys(
            r,
            "schemaVersion",
            "listingId",
            "ownerUid",
            "petId",
            "contentVersion",
            "activeRevisionId",
            "lifecycle",
            "visibility",
            "controlVersion",
            "createdAt",
            "updatedAt",
            "lastOperationId",
            "closureReason",
            "closedAt",
            "archivedAt")
        && Long.valueOf(1).equals(r.get("schemaVersion"))
        && id(id)
        && id.equals(r.get("listingId"))
        && r.get("ownerUid") instanceof String
        && id(r.get("petId"))
        && number(r.get("contentVersion"), 1)
        && ("v" + r.get("contentVersion")).equals(r.get("activeRevisionId"))
        && number(r.get("controlVersion"), 1)
        && Arrays.asList("draft", "closed", "archived").contains(r.get("lifecycle"))
        && "hidden".equals(r.get("visibility"))
        && id(r.get("lastOperationId"))
        && r.get("createdAt") instanceof Timestamp
        && r.get("updatedAt") instanceof Timestamp
        && ("draft".equals(r.get("lifecycle"))
            ? r.get("closureReason") == null
                && r.get("closedAt") == null
                && r.get("archivedAt") == null
            : "no_longer_offered".equals(r.get("closureReason"))
                && r.get("closedAt") instanceof Timestamp
                && ("closed".equals(r.get("lifecycle"))
                    ? r.get("archivedAt") == null
                    : r.get("archivedAt") instanceof Timestamp));
  }

  public static boolean revision(Map<String, Object> r, String listing, long v) {
    return keys(
            r,
            "schemaVersion",
            "listingId",
            "ownerUid",
            "petId",
            "contentVersion",
            "editableFields",
            "petIdentityVersion",
            "custodyVersion",
            "identitySnapshot",
            "careVersion",
            "mediaVersion",
            "createdAt",
            "operationId")
        && Long.valueOf(1).equals(r.get("schemaVersion"))
        && listing.equals(r.get("listingId"))
        && r.get("ownerUid") instanceof String
        && id(r.get("petId"))
        && Long.valueOf(v).equals(r.get("contentVersion"))
        && r.get("editableFields") instanceof Map
        && fields(nested(r, "editableFields"), true)
        && number(r.get("petIdentityVersion"), 1)
        && number(r.get("custodyVersion"), 0)
        && r.get("identitySnapshot") instanceof Map
        && PetSchema.identity(nested(r, "identitySnapshot"))
        && r.get("careVersion") == null
        && r.get("mediaVersion") == null
        && r.get("createdAt") instanceof Timestamp
        && id(r.get("operationId"));
  }

  public static boolean draft(Map<String, Object> r, String uid) {
    return keys(
            r,
            "schemaVersion",
            "ownerUid",
            "draftVersion",
            "linkedPetId",
            "intendedListingId",
            "baseListingVersion",
            "baseControlVersion",
            "editableFields",
            "updatedAt",
            "lastOperationId")
        && Long.valueOf(1).equals(r.get("schemaVersion"))
        && uid.equals(r.get("ownerUid"))
        && number(r.get("draftVersion"), 1)
        && (r.get("linkedPetId") == null || id(r.get("linkedPetId")))
        && id(r.get("intendedListingId"))
        && (r.get("baseListingVersion") == null || number(r.get("baseListingVersion"), 1))
        && (r.get("baseControlVersion") == null || number(r.get("baseControlVersion"), 1))
        && (r.get("baseListingVersion") == null) == (r.get("baseControlVersion") == null)
        && r.get("editableFields") instanceof Map
        && fields(nested(r, "editableFields"), false)
        && r.get("updatedAt") instanceof Timestamp
        && id(r.get("lastOperationId"));
  }

  public static boolean receipt(Map<String, Object> r, String uid, String listing, String op) {
    return keys(
            r,
            "schemaVersion",
            "command",
            "actorUid",
            "listingId",
            "petId",
            "operationId",
            "base",
            "result",
            "input",
            "committedAt",
            "status")
        && Long.valueOf(1).equals(r.get("schemaVersion"))
        && uid.equals(r.get("actorUid"))
        && listing.equals(r.get("listingId"))
        && op.equals(r.get("operationId"))
        && id(op)
        && id(r.get("petId"))
        && Arrays.asList("create", "edit", "hide", "close", "archive").contains(r.get("command"))
        && r.get("base") instanceof Map
        && versions(nested(r, "base"))
        && r.get("result") instanceof Map
        && keys(nested(r, "result"), "contentVersion", "controlVersion", "slotVersion")
        && number(nested(r, "result").get("contentVersion"), 1)
        && number(nested(r, "result").get("controlVersion"), 1)
        && number(nested(r, "result").get("slotVersion"), 0)
        && r.get("input") instanceof Map
        && ("create".equals(r.get("command")) || "edit".equals(r.get("command"))
            ? fields(nested(r, "input"), true)
            : nested(r, "input").isEmpty())
        && r.get("committedAt") instanceof Timestamp
        && "committed".equals(r.get("status"));
  }

  public static boolean versions(Map<String, Object> b) {
    return keys(
            b,
            "contentVersion",
            "controlVersion",
            "slotVersion",
            "identityVersion",
            "authorityVersion",
            "custodyVersion")
        && b.values().stream().allMatch(x -> number(x, 0));
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> freeze(Map<String, Object> input) {
    Map<String, Object> result = new HashMap<>();
    input.forEach(
        (k, v) ->
            result.put(
                k,
                v instanceof Map
                    ? freeze((Map<String, Object>) v)
                    : v instanceof List
                        ? Collections.unmodifiableList(new ArrayList<>((List<?>) v))
                        : v));
    return Collections.unmodifiableMap(result);
  }

  public static int bytes(String json) {
    return json.getBytes(StandardCharsets.UTF_8).length;
  }
}
