package com.gmail.manhle240406.project.community;

import com.gmail.manhle240406.project.pet.PetSchema;
import java.util.*;

/** Frozen schema2 intent. SetLike is desired-state, never toggle-on-retry. */
public final class SocialCommand {
  public final String command, postId, targetId, operationId;
  public final long contentVersion, controlVersion, targetVersion;
  public final Map<String, Object> input;

  public SocialCommand(
      String command,
      String postId,
      String targetId,
      long cv,
      long ctl,
      long tv,
      Map<String, Object> input,
      String op) {
    if (!Arrays.asList(
                "create_private",
                "publish",
                "edit_private",
                "edit_visible",
                "show",
                "hide",
                "archive",
                "set_like",
                "add_comment",
                "edit_comment",
                "withdraw_comment")
            .contains(command)
        || !PetSchema.id(postId)
        || targetId != null && !PetSchema.id(targetId)
        || !PetSchema.id(op)
        || cv < 0
        || ctl < 0
        || tv < 0) throw new IllegalArgumentException("invalid");
    this.command = command;
    this.postId = postId;
    this.targetId = targetId;
    this.contentVersion = cv;
    this.controlVersion = ctl;
    this.targetVersion = tv;
    this.operationId = op;
    this.input = Collections.unmodifiableMap(new HashMap<>(input));
  }

  public Map<String, Object> base() {
    Map<String, Object> b = new HashMap<>();
    b.put("contentVersion", contentVersion);
    b.put("controlVersion", controlVersion);
    b.put("targetVersion", targetVersion);
    return b;
  }

  public boolean matches(Map<String, Object> r) {
    return r != null
        && Long.valueOf(2).equals(r.get("schemaVersion"))
        && operationId.equals(r.get("operationId"))
        && command.equals(r.get("command"))
        && postId.equals(r.get("postId"))
        && Objects.equals(targetId, r.get("targetId"))
        && base().equals(r.get("base"))
        && input.equals(r.get("input"));
  }
}
