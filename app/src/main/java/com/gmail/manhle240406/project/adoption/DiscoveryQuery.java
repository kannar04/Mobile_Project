package com.gmail.manhle240406.project.adoption;

import java.text.Normalizer;
import java.util.*;

/** Finite F13 equality matrix and ASCII pet-name prefix. No private/full-text search. */
public final class DiscoveryQuery {
    public static final List<String> FILTERS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            "speciesId", "breedId", "regionId", "ageBand", "sex", "offerType"));
    public final String prefix, availability, signature;
    public final Map<String, String> filters;

    public DiscoveryQuery(String raw, String availability, Map<String, String> input) {
        if (!Arrays.asList("available", "reserved").contains(availability))
            throw new IllegalArgumentException("availability");
        prefix = normalize(raw, 32);
        this.availability = availability;
        Map<String, String> checked = new TreeMap<>();
        for (Map.Entry<String, String> e : input.entrySet()) {
            String k = e.getKey(), v = e.getValue();
            if (!FILTERS.contains(k)) throw new IllegalArgumentException("filter");
            if (k.endsWith("Id")) AdoptionValues.catalogId(v);
            else if (k.equals("ageBand")
                    && !Arrays.asList("unknown", "baby", "young", "adult", "senior").contains(v))
                throw new IllegalArgumentException("age_band");
            else if (k.equals("sex") && !Arrays.asList("unknown", "male", "female").contains(v))
                throw new IllegalArgumentException("sex");
            else if (k.equals("offerType") && !"adoption".equals(v))
                throw new IllegalArgumentException("exchange_not_enabled");
            checked.put(k, v);
        }
        if (checked.containsKey("breedId") && !checked.containsKey("speciesId"))
            throw new IllegalArgumentException("breed_requires_species");
        filters = Collections.unmodifiableMap(checked);
        signature =
                AdoptionValues.digest("1|signed_in|" + availability + "|" + prefix + "|" + checked);
    }

    public static String normalize(String name, int max) {
        if (name == null || name.codePointCount(0, name.length()) > 80)
            throw new IllegalArgumentException("name_limit");
        String decomposed =
                Normalizer.normalize(
                                name.toLowerCase(Locale.ROOT).replace('đ', 'd'),
                                Normalizer.Form.NFD)
                        .replaceAll("\\p{M}+", "");
        // Android's ICU Pattern rejects Java's (?U) flag. Normalize Unicode spacing
        // explicitly so device and host tests use the same prefix contract.
        StringBuilder collapsed = new StringBuilder();
        boolean pendingSpace = false;
        for (int i = 0; i < decomposed.length(); ) {
            int cp = decomposed.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0x85) {
                pendingSpace = collapsed.length() > 0;
                continue;
            }
            if (pendingSpace) collapsed.append(' ');
            collapsed.appendCodePoint(cp);
            pendingSpace = false;
        }
        String value = collapsed.toString();
        if (value.length() > max || !value.matches("[a-z0-9 ]*"))
            throw new IllegalArgumentException("unsupported_prefix");
        return value;
    }

    public String successor() {
        if (prefix.isEmpty()) throw new IllegalStateException("no_prefix");
        return prefix.substring(0, prefix.length() - 1)
                + (char) (prefix.charAt(prefix.length() - 1) + 1);
    }

    public String orderField() {
        return prefix.isEmpty() ? "firstPublishedAt" : "nameKey";
    }

    public void validateBreed(Map<String, Object> species, Map<String, Object> breed) {
        if (!filters.containsKey("breedId")) return;
        if (species == null
                || breed == null
                || !filters.get("speciesId").equals(species.get("id"))
                || !filters.get("breedId").equals(breed.get("id"))
                || !filters.get("speciesId").equals(breed.get("speciesId"))
                || !"species".equals(species.get("kind"))
                || !"breed".equals(breed.get("kind"))
                || !"active".equals(species.get("state"))
                || !"active".equals(breed.get("state")))
            throw new IllegalArgumentException("catalog_conflict");
    }

    public static List<List<String>> supportedSubsets() {
        List<List<String>> result = new ArrayList<>();
        for (int mask = 0; mask < 64; mask++) {
            if ((mask & 2) != 0 && (mask & 1) == 0) continue;
            List<String> subset = new ArrayList<>();
            for (int i = 0; i < 6; i++) if ((mask & (1 << i)) != 0) subset.add(FILTERS.get(i));
            result.add(Collections.unmodifiableList(subset));
        }
        return Collections.unmodifiableList(result);
    }
}
