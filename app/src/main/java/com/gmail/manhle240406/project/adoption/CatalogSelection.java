package com.gmail.manhle240406.project.adoption;
import java.util.*;

/** Selection hint only; future canonical adapters must revalidate current authoritative references. */
public final class CatalogSelection {
    public final String speciesId, breedId, regionId;
    public final long speciesVersion, breedVersion, regionVersion;
    public CatalogSelection(Map < String, Object > species, Map < String, Object > breed, Map < String, Object > region, CatalogSelection retained) {
        validate(species, "species");
        validate(breed, "breed");
        validate(region, "region");
        if (!species.get("id").equals(breed.get("speciesId"))) throw new IllegalArgumentException("breed_parent");
        speciesId = (String) species.get("id");
        breedId = (String) breed.get("id");
        regionId = (String) region.get("id");
        speciesVersion = AdoptionValues.version(species, "version");
        breedVersion = AdoptionValues.version(breed, "version");
        regionVersion = AdoptionValues.version(region, "version");
        if (!"active".equals(species.get("state")) && (retained == null || !speciesId.equals(retained.speciesId)) || !"active".equals(breed.get("state")) && (retained == null || !breedId.equals(retained.breedId)) || !"active".equals(region.get("state")) && (retained == null || !regionId.equals(retained.regionId))) throw new IllegalArgumentException("retired_selection");
    }
    private static void validate(Map < String, Object > entry, String kind) {
        if (entry == null || !kind.equals(entry.get("kind")) || !Long.valueOf(1).equals(entry.get("schemaVersion")) || !Arrays.asList("active", "retired").contains(entry.get("state")) || AdoptionValues.version(entry, "version") < 1) throw new IllegalArgumentException("catalog_schema");
        AdoptionValues.catalogId((String) entry.get("id"));
    }
}