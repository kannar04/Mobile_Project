package com.gmail.manhle240406.project.listing;

import static com.gmail.manhle240406.project.pet.PetSchema.*;

import static org.junit.Assert.*;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.*;

public class ListingSafetyTest {
    private final String id = "12345678-1234-1234-1234-123456789abc",
            pet = "12345678-1234-1234-1234-123456789abd",
            op = "12345678-1234-1234-1234-123456789abe";
    private final Timestamp now = new Timestamp(1000, 0);

    private Map<String, Object> fields() {
        Map<String, Object> f = ListingSchema.empty();
        f.put("title", "Mèo cần người chăm sóc");
        return f;
    }

    private Map<String, Object> base() {
        return map(
                "contentVersion",
                0L,
                "controlVersion",
                0L,
                "slotVersion",
                0L,
                "identityVersion",
                1L,
                "authorityVersion",
                1L,
                "custodyVersion",
                0L);
    }

    private Map<String, Object> root() {
        return map(
                "schemaVersion",
                1L,
                "listingId",
                id,
                "ownerUid",
                "alice",
                "petId",
                pet,
                "contentVersion",
                1L,
                "activeRevisionId",
                "v1",
                "lifecycle",
                "draft",
                "visibility",
                "hidden",
                "controlVersion",
                1L,
                "createdAt",
                now,
                "updatedAt",
                now,
                "lastOperationId",
                op,
                "closureReason",
                null,
                "closedAt",
                null,
                "archivedAt",
                null);
    }

    @Test
    public void incompleteWorkingCopyCannotBeCanonical() {
        assertTrue(ListingSchema.fields(ListingSchema.empty(), false));
        assertFalse(ListingSchema.fields(ListingSchema.empty(), true));
        assertTrue(ListingSchema.fields(fields(), true));
    }

    @Test
    public void unicodeCodepointsBytesAndCapsAreBounded() {
        Map<String, Object> f = fields();
        f.put("title", "🐈".repeat(80));
        assertTrue(ListingSchema.fields(f, true));
        f.put("title", "🐈".repeat(81));
        assertFalse(ListingSchema.fields(f, true));
        f = fields();
        f.put("description", "🐈".repeat(2000));
        assertTrue(ListingSchema.fields(f, true));
        f.put("description", "🐈".repeat(2001));
        assertFalse(ListingSchema.fields(f, true));
    }

    @Test
    public void privateFieldsCustomMediaAndInventedOfferTypesRejected() {
        for (String key : Arrays.asList("phone", "chip", "address", "approved", "applicantUid")) {
            Map<String, Object> f = fields();
            f.put(key, "private");
            assertFalse(ListingSchema.fields(f, true));
        }
        Map<String, Object> f = fields();
        f.put("mediaRefs", Arrays.asList("file:///photo"));
        assertFalse(ListingSchema.fields(f, true));
        f = fields();
        f.put("offerType", "sale");
        assertFalse(ListingSchema.fields(f, true));
    }

    @Test
    public void canonicalRootCannotClaimPublicAdoptionOrUnknownSchema() {
        assertTrue(ListingSchema.root(root(), id));
        for (String state : Arrays.asList("open", "placed", "adopted")) {
            Map<String, Object> r = root();
            r.put("lifecycle", state);
            assertFalse(ListingSchema.root(r, id));
        }
        Map<String, Object> r = root();
        r.put("visibility", "public");
        assertFalse(ListingSchema.root(r, id));
        r = root();
        r.put("schemaVersion", 2L);
        assertFalse(ListingSchema.root(r, id));
    }

    @Test
    public void terminalStateRequiresReasonAndPreservedTimes() {
        Map<String, Object> r = root();
        r.put("lifecycle", "closed");
        assertFalse(ListingSchema.root(r, id));
        r.put("closureReason", "no_longer_offered");
        r.put("closedAt", now);
        assertTrue(ListingSchema.root(r, id));
        r.put("lifecycle", "archived");
        assertFalse(ListingSchema.root(r, id));
        r.put("archivedAt", now);
        assertTrue(ListingSchema.root(r, id));
    }

    @Test
    public void commandFreezesInputAndMatchesExactTargetsVersionsAndPayload() {
        Map<String, Object> f = fields(), b = base();
        ListingCommand c = new ListingCommand("create", id, pet, op, b, f);
        f.put("title", "Changed content");
        b.put("slotVersion", 5L);
        assertNotEquals(f, c.input);
        assertEquals(0L, c.base.get("slotVersion"));
        Map<String, Object> r = c.persisted();
        assertTrue(c.matches(r));
        r.put("input", f);
        assertFalse(c.matches(r));
        r = c.persisted();
        r.put("petId", id);
        assertFalse(c.matches(r));
    }

    @Test
    public void invalidCommandCannotPublishHardDeleteOrFabricateAction() {
        for (String type : Arrays.asList("publish", "delete", "submit", "placed")) {
            try {
                new ListingCommand(type, id, pet, op, base(), fields());
                fail();
            } catch (IllegalArgumentException expected) {
            }
        }
        try {
            new ListingCommand("hide", id, pet, op, base(), fields());
            fail();
        } catch (IllegalArgumentException expected) {
        }
        Map<String, Object> b = base();
        b.put("authorityVersion", -1L);
        assertFalse(ListingSchema.versions(b));
    }

    @Test
    public void receiptIsStrictAndAudienceScoped() {
        ListingCommand c = new ListingCommand("create", id, pet, op, base(), fields());
        Map<String, Object> r = c.persisted();
        r.putAll(
                map(
                        "schemaVersion",
                        1L,
                        "actorUid",
                        "alice",
                        "result",
                        map("contentVersion", 1L, "controlVersion", 1L, "slotVersion", 1L),
                        "committedAt",
                        now,
                        "status",
                        "committed"));
        assertTrue(ListingSchema.receipt(r, "alice", id, op));
        assertFalse(ListingSchema.receipt(r, "bob", id, op));
        r.put("phone", "private");
        assertFalse(ListingSchema.receipt(r, "alice", id, op));
    }

    @Test
    public void draftCannotClaimAuthoritativeReviewOrMismatchedBaseVersions() {
        Map<String, Object> d =
                map(
                        "schemaVersion",
                        1L,
                        "ownerUid",
                        "alice",
                        "draftVersion",
                        1L,
                        "linkedPetId",
                        null,
                        "intendedListingId",
                        id,
                        "baseListingVersion",
                        null,
                        "baseControlVersion",
                        null,
                        "editableFields",
                        ListingSchema.empty(),
                        "updatedAt",
                        now,
                        "lastOperationId",
                        op);
        assertTrue(ListingSchema.draft(d, "alice"));
        d.put("baseListingVersion", 1L);
        assertFalse(ListingSchema.draft(d, "alice"));
        d.put("baseControlVersion", 1L);
        assertTrue(ListingSchema.draft(d, "alice"));
        d.put("review", "approved");
        assertFalse(ListingSchema.draft(d, "alice"));
    }
}
