package com.gmail.manhle240406.project.pet;

import java.util.*;

/** Frozen command snapshot: UUIDs and bases can survive interruption; private input cannot. */
public final class PetCommand {
    public final String type, petId, operationId;
    public final long identityVersion, claimVersion, authorityVersion, privateVersion;
    public final Map<String, Object> input;

    public PetCommand(
            String type,
            String pet,
            String operation,
            long identity,
            long claim,
            long authority,
            long privateVersion,
            Map<String, Object> input) {
        if (!PetSchema.id(pet) || !PetSchema.id(operation))
            throw new IllegalArgumentException("invalid_id");
        this.type = type;
        petId = pet;
        operationId = operation;
        identityVersion = identity;
        claimVersion = claim;
        authorityVersion = authority;
        this.privateVersion = privateVersion;
        this.input = freeze(input);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> freeze(Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> e : input.entrySet())
            result.put(
                    e.getKey(),
                    e.getValue() instanceof Map
                            ? freeze((Map<String, Object>) e.getValue())
                            : e.getValue());
        return Collections.unmodifiableMap(result);
    }

    public boolean matchesReceipt(Map<String, Object> r) {
        return r != null
                && type.equals(r.get("type"))
                && petId.equals(r.get("petId"))
                && operationId.equals(r.get("operationId"))
                && Long.valueOf(identityVersion).equals(r.get("baseIdentityVersion"))
                && Long.valueOf(claimVersion).equals(r.get("baseClaimVersion"))
                && Long.valueOf(authorityVersion).equals(r.get("baseAuthorityVersion"))
                && input.equals(r.get("input"));
    }
}
