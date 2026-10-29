package com.gmail.manhle240406.project.home;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

public final class HomeSpeciesFilterTest {
    @Test
    public void catalogIdFiltersNewSpeciesWithoutBreedDependency() {
        Map<String, Object> listing = new HashMap<>();
        listing.put("speciesId", "fish");
        listing.put("bundledIllustrationKey", "default");
        listing.put("breedId", "fish_unknown");
        assertTrue(HomeFragment.matchesPetSpecies(listing, "fish"));
        assertFalse(HomeFragment.matchesPetSpecies(listing, "cat"));
    }

    @Test
    public void legacyVietnameseIdsRemainCompatible() {
        Map<String, Object> listing = new HashMap<>();
        listing.put("speciesId", "tho_nha");
        listing.put("bundledIllustrationKey", "default");
        assertTrue(HomeFragment.matchesPetSpecies(listing, "rabbit"));
    }
}
