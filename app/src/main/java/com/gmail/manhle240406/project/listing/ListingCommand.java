package com.gmail.manhle240406.project.listing;

import static com.gmail.manhle240406.project.pet.PetSchema.*;

import java.util.*;

/**
 * Frozen intent retained before submission; retries never allocate IDs or replace the candidate.
 */
public final class ListingCommand {
  public final String command, listingId, petId, operationId;
  public final Map<String, Object> base, input;

  public ListingCommand(
      String command,
      String listing,
      String pet,
      String operation,
      Map<String, Object> base,
      Map<String, Object> input) {
    if (!Arrays.asList("create", "edit", "hide", "close", "archive").contains(command)
        || !id(listing)
        || !id(pet)
        || !id(operation)
        || !ListingSchema.versions(base)
        || ("create".equals(command) || "edit".equals(command)
            ? !ListingSchema.fields(input, true)
            : !input.isEmpty())) throw new IllegalArgumentException("invalid_input");
    this.command = command;
    listingId = listing;
    petId = pet;
    operationId = operation;
    this.base = ListingSchema.freeze(base);
    this.input = ListingSchema.freeze(input);
  }

  public boolean matches(Map<String, Object> receipt) {
    return command.equals(receipt.get("command"))
        && listingId.equals(receipt.get("listingId"))
        && petId.equals(receipt.get("petId"))
        && operationId.equals(receipt.get("operationId"))
        && base.equals(receipt.get("base"))
        && input.equals(receipt.get("input"));
  }

  public Map<String, Object> persisted() {
    return map(
        "command",
        command,
        "listingId",
        listingId,
        "petId",
        petId,
        "operationId",
        operationId,
        "base",
        base,
        "input",
        input);
  }

  public static ListingCommand restored(Map<String, Object> m) {
    if (!keys(m, "command", "listingId", "petId", "operationId", "base", "input")
        || !(m.get("base") instanceof Map)
        || !(m.get("input") instanceof Map)) throw new IllegalArgumentException();
    return new ListingCommand(
        (String) m.get("command"),
        (String) m.get("listingId"),
        (String) m.get("petId"),
        (String) m.get("operationId"),
        nested(m, "base"),
        nested(m, "input"));
  }
}
