package com.gmail.manhle240406.project.adoption;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.time.*;
import com.gmail.manhle240406.project.pet.PetSchema;
import com.gmail.manhle240406.project.community.FeedWindow;

public class AdoptionContractsTest {
    private final String id = "11111111-1111-4111-a111-111111111111";
    @Test public void prefixAccentsWhitespaceAndAsciiSuccessor() {
        DiscoveryQuery q = new DiscoveryQuery("  MÈO  Đen ", "available", Collections.singletonMap("speciesId", "cat"));
        assertEquals("meo den", q.prefix);
        assertEquals("meo deo", q.successor());
        assertEquals("nameKey", q.orderField());
        assertEquals("firstPublishedAt", new DiscoveryQuery("", "available", Collections.emptyMap()).orderField());
        assertThrows(IllegalArgumentException.class, () - > new DiscoveryQuery("猫", "available", Collections.emptyMap()));
        assertThrows(IllegalArgumentException.class, () - > new DiscoveryQuery("cat*", "available", Collections.emptyMap()));
    }
    @Test public void finite48FilterSubsetsAnd96OrderShapes() {
        assertEquals(48, DiscoveryQuery.supportedSubsets().size());
        assertTrue(DiscoveryQuery.supportedSubsets().stream().noneMatch(s - > s.contains("breedId") && !s.contains("speciesId")));
        assertThrows(IllegalArgumentException.class, () - > new DiscoveryQuery("", "available", Collections.singletonMap("breedId", "cat_unknown")));
        assertThrows(IllegalArgumentException.class, () - > new DiscoveryQuery("", "placed", Collections.emptyMap()));
        assertThrows(IllegalArgumentException.class, () - > new DiscoveryQuery("", "available", Collections.singletonMap("offerType", "exchange")));
        assertThrows(IllegalArgumentException.class, () - > new DiscoveryQuery("", "available", Collections.singletonMap("phone", "123")));
        DiscoveryQuery selected = new DiscoveryQuery("", "available", PetSchemaString.map("speciesId", "cat", "breedId", "dog_breed"));
        assertThrows(IllegalArgumentException.class, () - > selected.validateBreed(PetSchema.map("id", "cat", "kind", "species", "state", "active"), PetSchema.map("id", "dog_breed", "kind", "breed", "speciesId", "dog", "state", "active")));
    }
    private static final class PetSchemaString {
        static Map < String, String > map(String...parts) {
            Map < String, String > out = new HashMap < > ();
            for (int i = 0; i < parts.length; i += 2) out.put(parts[i], parts[i + 1]);
            return out;
        }
    }
    @Test public void equivalentFiltersShareSignatureAndAreFrozen() {
        Map < String, String > filters = new HashMap < > ();
        filters.put("speciesId", "cat");
        DiscoveryQuery a = new DiscoveryQuery("Mèo", "available", filters);
        filters.clear();
        DiscoveryQuery b = new DiscoveryQuery("meo", "available", Collections.singletonMap("speciesId", "cat"));
        assertEquals(a.signature, b.signature);
        assertThrows(UnsupportedOperationException.class, () - > a.filters.clear());
    }
    @Test public void unknownCareHasNoHealthyOrVaccinatedAssumption() {
        PetCareContract.validate(PetCareContract.unknown(), "", LocalDate.of(2026, 10, 14));
        assertEquals("unknown", PetCareContract.unknown().get("healthState"));
        assertEquals("unknown", PetCareContract.unknown().get("sterilization"));
    }
    @Test public void exactEstimatedDatesRemainDistinctAndNoFutureAdministration() {
        Map < String, Object > p = PetCareContract.unknown();
        Map < String, Object > v = PetSchema.map("entryId", id, "typeLabel", "Owner reported vaccine", "reportedDate", "2024-02", "datePrecision", "month", "provenance", "owner_report");
        p.put("vaccinations", Collections.singletonList(v));
        PetCareContract.validate(p, "", LocalDate.of(2026, 10, 14));
        assertEquals("2024-02", v.get("reportedDate"));
        v.put("reportedDate", "2027");
        v.put("datePrecision", "year");
        assertThrows(IllegalArgumentException.class, () - > PetCareContract.validate(p, "", LocalDate.of(2026, 10, 14)));
        v.put("reportedDate", "2023-02-29");
        v.put("datePrecision", "exact");
        assertThrows(IllegalArgumentException.class, () - > PetCareContract.validate(p, "", LocalDate.of(2026, 10, 14)));
    }
    @Test public void vaccineIdsAndPayloadCannotBeReusedToInventVerification() {
        Map < String, Object > p = PetCareContract.unknown(), v = PetSchema.map("entryId", id, "typeLabel", "Demo", "reportedDate", null, "datePrecision", "unknown", "provenance", "owner_report");
        p.put("vaccinations", Arrays.asList(v, v));
        assertThrows(IllegalArgumentException.class, () - > PetCareContract.validate(p, "", LocalDate.now()));
        Map < String, Object > spoof = PetCareContract.unknown();
        spoof.put("verified", true);
        assertThrows(IllegalArgumentException.class, () - > PetCareContract.validate(spoof, "", LocalDate.now()));
    }
    @Test public void linkFormatterDisabledUntilVerifiedOriginAndNoPrivatePayload() {
        assertThrows(IllegalStateException.class, () - > new ShareLinks(null, false).format(id));
        ShareLinks links = new ShareLinks("https://anilove.example.test", true);
        assertEquals(id, links.parse(links.format(id)));
        assertFalse(links.genericText(id).contains("petName"));
        for (String bad: Arrays.asList("http://anilove.example.test/l/" + id, "https://evil.test/l/" + id, links.format(id) + "?token=secret", links.format(id) + "#apply", "https://anilove.example.test/l/%2e%2e/" + id, "https://user@anilove.example.test/l/" + id)) assertThrows(IllegalArgumentException.class, () - > links.parse(bad));
    }
    @Test public void linkOriginAndIdRejectAmbiguityAndTraversal() {
        for (String bad: Arrays.asList("https://anilove.example.test/", "https://anilove.example.test:443", "https://user@anilove.example.test", "https://localhost", "https://anilove.example.test?query=x")) assertThrows(IllegalArgumentException.class, () - > new ShareLinks(bad, true));
        assertThrows(IllegalArgumentException.class, () - > new ShareLinks("https://anilove.example.test", true).format("../private"));
    }
    @Test public void safeSignInContinuationHasOneIdentifierAndAccountSwitchClears() {
        ShareLinks.Pending p = new ShareLinks.Pending();
        p.set(null, id);
        p.bindAfterSignIn("A");
        assertEquals(id, p.consume("A"));
        assertNull(p.consume("A"));
        p.set("A", id);
        assertNull(p.consume("B"));
    }
    @Test public void queryGenerationIgnoresOldLoadsAndLogoutDropsWindow() {
        DiscoverySession < String > s = new DiscoverySession < > (new FeedWindow.Identity < String > () {
            public String id(String v) {
                return v;
            }
            public long version(String v) {
                return 1;
            }
        });
        long first = s.apply("A", new DiscoveryQuery("", "available", Collections.emptyMap()));
        assertTrue(s.begin(first, null));
        assertFalse(s.begin(first, null));
        assertTrue(s.accept(first, null, Arrays.asList("one", "two")));
        s.anchor("two", 4);
        s.head(first, Collections.singletonList("new"));
        assertEquals(Arrays.asList("one", "two"), s.rows());
        s.refresh(first);
        assertEquals("new", s.rows().get(0));
        assertEquals("two", s.anchor());
        long next = s.apply("A", new DiscoveryQuery("cat", "available", Collections.emptyMap()));
        assertFalse(s.accept(first, "cursor", Collections.singletonList("old")));
        assertTrue(s.rows().isEmpty());
        s.apply("B", new DiscoveryQuery("cat", "available", Collections.emptyMap()));
        assertFalse(s.accept(next, null, Collections.singletonList("private")));
    }
    @Test public void canonicalInputHashIgnoresMapInsertionOrderAndEscapesControlCharacters() {
        Map < String, Object > a = PetSchema.map("z", "line\n\"quote", "a", 1 L), b = PetSchema.map("a", 1 L, "z", "line\n\"quote");
        assertEquals(AdoptionValues.canonical(a), AdoptionValues.canonical(b));
        assertTrue(AdoptionValues.canonical(a).contains("\\u000a"));
        assertEquals(64, AdoptionValues.digest(AdoptionValues.canonical(a)).length());
    }
    @Test public void nativeCatalogParentAndRetainedRetiredSelectionAreExplicit() {
        Map < String, Object > species = PetSchema.map("id", "cat", "kind", "species", "schemaVersion", 1 L, "version", 1 L, "state", "active"), breed = PetSchema.map("id", "cat_unknown", "kind", "breed", "speciesId", "cat", "schemaVersion", 1 L, "version", 1 L, "state", "active"), region = PetSchema.map("id", "north", "kind", "region", "schemaVersion", 1 L, "version", 1 L, "state", "active");
        CatalogSelection before = new CatalogSelection(species, breed, region, null);
        breed.put("state", "retired");
        breed.put("version", 2 L);
        assertThrows(IllegalArgumentException.class, () - > new CatalogSelection(species, breed, region, null));
        assertEquals("cat_unknown", new CatalogSelection(species, breed, region, before).breedId);
        breed.put("speciesId", "dog");
        assertThrows(IllegalArgumentException.class, () - > new CatalogSelection(species, breed, region, before));
    }
    @Test public void discoveryWhitelistRejectsPrivateExtrasEvenBeforeParsingMissingFields() {
        Map < String, Object > invalid = new HashMap < > ();
        invalid.put("phone", "synthetic-private");
        assertThrows(IllegalArgumentException.class, () - > DiscoveryProjection.validate(invalid, id));
    }
}