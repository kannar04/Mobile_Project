package com.gmail.manhle240406.project.community;

import static com.gmail.manhle240406.project.pet.PetSchema.*;

import java.util.*;

public final class CommunityCommand {
  public final String command, postId, operationId;
  public final long contentVersion, controlVersion;
  public final Map<String, Object> input;

  public CommunityCommand(
      String type, String post, String op, long cv, long ctl, Map<String, Object> input) {
    if (!Arrays.asList("create", "edit", "hide", "archive").contains(type)
        || !id(post)
        || !id(op)
        || cv < 0
        || ctl < 0
        || ("create".equals(type) ? cv != 0 || ctl != 0 : cv < 1 || ctl < 1)
        || ("create".equals(type) || "edit".equals(type)
            ? !CommunitySchema.fields(input)
            : !input.isEmpty())) throw new IllegalArgumentException("invalid_input");
    command = type;
    postId = post;
    operationId = op;
    contentVersion = cv;
    controlVersion = ctl;
    this.input = Collections.unmodifiableMap(new HashMap<>(input));
  }

  public Map<String, Object> base() {
    return map("contentVersion", contentVersion, "controlVersion", controlVersion);
  }

  public boolean matches(Map<String, Object> r) {
    return command.equals(r.get("command"))
        && postId.equals(r.get("postId"))
        && operationId.equals(r.get("operationId"))
        && base().equals(r.get("base"))
        && input.equals(r.get("input"));
  }
}
