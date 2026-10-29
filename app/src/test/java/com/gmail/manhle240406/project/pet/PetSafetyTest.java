package com.gmail.manhle240406.project.pet;

import static com.gmail.manhle240406.project.pet.PetSchema.*;

import static org.junit.Assert.*;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.*;

public class PetSafetyTest {
    private final String pet = "12345678-1234-1234-1234-123456789abc",
            op = "12345678-1234-1234-1234-123456789abd";
    private final Timestamp now = new Timestamp(1000, 0);

    private Map<String, Object> identity() {
        return map(
                "displayName",
                "Mèo Nguyễn",
                "species",
                "cat",
                "breed",
                "",
                "sex",
                "unknown",
                "ageMonths",
                null,
                "ageObservedAt",
                null,
                "marks",
                "",
                "marksUnknown",
                true,
                "illustrationKey",
                "default");
    }

    private Map<String, Object> declaration() {
        return map("basis", "claimed_owner", "reportedCare", true, "explanation", "");
    }

    @Test
    public void unicodeOptionalUnknownAndExplicitMarks() {
        assertTrue(PetSchema.identity(identity()));
        Map<String, Object> d = identity();
        d.put("marksUnknown", false);
        assertFalse(PetSchema.identity(d));
        d.put("marks", "Chân trắng");
        assertTrue(PetSchema.identity(d));
        assertTrue(PetSchema.declaration(declaration()));
    }

    @Test
    public void estimatesNeedObservationAndNeverBecomeCurrentPreciseAge() {
        Map<String, Object> d = identity();
        d.put("ageMonths", 12L);
        assertFalse(PetSchema.identity(d));
        d.put("ageObservedAt", now);
        assertTrue(PetSchema.identity(d));
        d.put("ageMonths", 1201L);
        assertFalse(PetSchema.identity(d));
    }

    @Test
    public void privateExplanationAndByteBoundsCannotBeBypassedByUnicode() {
        Map<String, Object> c = declaration();
        c.put("basis", "authorized_caregiver");
        assertFalse(PetSchema.declaration(c));
        c.put("explanation", "Synthetic authorization");
        assertTrue(PetSchema.declaration(c));
        c.put("explanation", "🐈".repeat(1000));
        assertFalse(PetSchema.declaration(c));
        assertFalse(text("\u0001\u0002", 1, 10, 40));
    }

    @Test
    public void identityRejectsPrivateFieldsUnknownSchemaAndExternalMedia() {
        Map<String, Object> i = identity();
        i.put("chip", "synthetic");
        assertFalse(PetSchema.identity(i));
        i.remove("chip");
        i.put("illustrationKey", "https://image.invalid");
        assertFalse(PetSchema.identity(i));
    }

    @Test
    public void commandFreezesNestedInputAndRejectsChangedReceiptPayload() {
        Map<String, Object> i = identity(), input = map("identity", i, "claim", declaration());
        PetCommand command = new PetCommand("register_pet", pet, op, 0, 0, 0, 0, input);
        i.put("species", "dog");
        assertEquals("cat", nested(command.input, "identity").get("species"));
        Map<String, Object> receipt =
                map(
                        "type",
                        "register_pet",
                        "petId",
                        pet,
                        "operationId",
                        op,
                        "baseIdentityVersion",
                        0L,
                        "baseClaimVersion",
                        0L,
                        "baseAuthorityVersion",
                        0L,
                        "input",
                        command.input);
        assertTrue(command.matchesReceipt(receipt));
        receipt.put("input", input);
        assertFalse(command.matchesReceipt(receipt));
    }

    private List<Map<String, Object>> enabled() {
        Map<String, Object> p =
                map(
                        "schemaVersion",
                        1L,
                        "petId",
                        pet,
                        "registrarUid",
                        "a",
                        "editorUid",
                        "a",
                        "identityVersion",
                        1L,
                        "currentRevisionId",
                        op,
                        "custodyVersion",
                        0L,
                        "lifecycle",
                        "active",
                        "createdAt",
                        now,
                        "updatedAt",
                        now,
                        "lastOperationId",
                        op);
        Map<String, Object> c =
                map(
                        "schemaVersion",
                        1L,
                        "petId",
                        pet,
                        "claimantUid",
                        "a",
                        "claimVersion",
                        2L,
                        "identityVersion",
                        1L,
                        "basis",
                        "claimed_owner",
                        "reportedCare",
                        true,
                        "explanation",
                        "",
                        "reviewState",
                        "accepted_for_demo",
                        "submittedRevisionId",
                        op,
                        "submittedAt",
                        now,
                        "createdAt",
                        now,
                        "updatedAt",
                        now,
                        "lastOperationId",
                        op);
        Map<String, Object> a =
                map(
                        "schemaVersion",
                        1L,
                        "petId",
                        pet,
                        "offerUid",
                        "a",
                        "claimUid",
                        "a",
                        "claimVersion",
                        2L,
                        "identityVersion",
                        1L,
                        "authorityVersion",
                        1L,
                        "status",
                        "enabled",
                        "hold",
                        "clear",
                        "decisionId",
                        op,
                        "custodyVersion",
                        0L,
                        "updatedAt",
                        now,
                        "lastOperationId",
                        op);
        Map<String, Object> g =
                map(
                        "schemaVersion",
                        1L,
                        "petId",
                        pet,
                        "state",
                        "none",
                        "activeReservationId",
                        null,
                        "activePlacementId",
                        null,
                        "gateVersion",
                        0L,
                        "custodyVersion",
                        0L,
                        "updatedAt",
                        now);
        Map<String, Object> s =
                map(
                        "schemaVersion",
                        1L,
                        "petId",
                        pet,
                        "activeListingId",
                        null,
                        "slotVersion",
                        0L,
                        "updatedAt",
                        now);
        return Arrays.asList(p, c, a, g, s);
    }

    private PetOfferAuthority.Reason evaluate(List<Map<String, Object>> d) {
        return PetOfferAuthority.evaluate(
                pet, "a", 1, true, d.get(0), d.get(1), d.get(2), d.get(3), d.get(4));
    }

    @Test
    public void eligibilityFailsClosedOnEveryGate() {
        List<Map<String, Object>> d = enabled();
        assertEquals(PetOfferAuthority.Reason.ALLOWED_FOR_DEMO, evaluate(d));
        d.set(3, null);
        assertEquals(PetOfferAuthority.Reason.UNKNOWN_GATE, evaluate(d));
        d = enabled();
        d.get(3).put("state", "unknown");
        assertEquals(PetOfferAuthority.Reason.ACTIVE_OBLIGATIONS, evaluate(d));
        d = enabled();
        d.get(4).put("activeListingId", op);
        assertEquals(PetOfferAuthority.Reason.ACTIVE_OBLIGATIONS, evaluate(d));
    }

    @Test
    public void changedIdentityCustodyHoldOrActorInvalidatesOffering() {
        for (String change : Arrays.asList("identity", "custody", "hold", "actor")) {
            List<Map<String, Object>> d = enabled();
            if (change.equals("identity")) d.get(0).put("identityVersion", 2L);
            if (change.equals("custody")) d.get(0).put("custodyVersion", 1L);
            if (change.equals("hold")) d.get(2).put("hold", "held");
            if (change.equals("actor")) d.get(2).put("offerUid", "b");
            assertNotEquals(PetOfferAuthority.Reason.ALLOWED_FOR_DEMO, evaluate(d));
        }
    }

    @Test
    public void noProfileVisibilityOrChipIsNeededForDemoPermission() {
        assertFalse(identity().containsKey("chip"));
        List<Map<String, Object>> d = enabled();
        assertEquals(PetOfferAuthority.Reason.ALLOWED_FOR_DEMO, evaluate(d));
        assertEquals(
                PetOfferAuthority.Reason.NOT_READY,
                PetOfferAuthority.evaluate(
                        pet, "a", 1, false, d.get(0), d.get(1), d.get(2), d.get(3), d.get(4)));
    }

    @Test
    public void minimizedOperatorResponseReceiptRejectsRawReporterOrEvidence() {
        Map<String, Object> input =
                map(
                        "decision",
                        "respond_challenge",
                        "reason",
                        "Synthetic minimized reason",
                        "decisionId",
                        op);
        Map<String, Object> receipt =
                map(
                        "schemaVersion",
                        1L,
                        "ownerUid",
                        "a",
                        "operationId",
                        op,
                        "petId",
                        pet,
                        "type",
                        "respond_challenge",
                        "role",
                        "reporter",
                        "baseIdentityVersion",
                        0L,
                        "baseClaimVersion",
                        0L,
                        "baseAuthorityVersion",
                        0L,
                        "result",
                        map("identityVersion", 0L, "claimVersion", 0L, "authorityVersion", 0L),
                        "input",
                        input,
                        "status",
                        "committed",
                        "recordedAt",
                        now);
        assertTrue(PetIdentityRepository.receipt(receipt, "a", op));
        input.put("rawReporter", "another-person");
        assertFalse(PetIdentityRepository.receipt(receipt, "a", op));
    }
}
