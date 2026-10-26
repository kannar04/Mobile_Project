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

    private static boolean equivalent(Object a, Object b) {
        if (a instanceof Number && b instanceof Number)
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) == 0;
        if (a instanceof Map && b instanceof Map) {
            Map<?, ?> x = (Map<?, ?>) a, y = (Map<?, ?>) b;
            if (!x.keySet().equals(y.keySet())) return false;
            for (Object key : x.keySet()) if (!equivalent(x.get(key), y.get(key))) return false;
            return true;
        }
        if (a instanceof List && b instanceof List) {
            List<?> x = (List<?>) a, y = (List<?>) b;
            if (x.size() != y.size()) return false;
            for (int i = 0; i < x.size(); i++) if (!equivalent(x.get(i), y.get(i))) return false;
            return true;
        }
        return Objects.equals(a, b);
    }

    public boolean matches(Map<String, Object> r) {
        return r != null
                && equivalent(2, r.get("schemaVersion"))
                && operationId.equals(r.get("operationId"))
                && command.equals(r.get("command"))
                && postId.equals(r.get("postId"))
                && Objects.equals(targetId, r.get("targetId"))
                && equivalent(base(), r.get("base"))
                && equivalent(input, r.get("input"));
    }
}
