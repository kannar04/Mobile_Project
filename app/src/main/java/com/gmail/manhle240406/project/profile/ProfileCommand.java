package com.gmail.manhle240406.project.profile;

import java.util.*;

/** Immutable retry intent stays in the ViewModel's memory, never a Bundle or disk. */
public final class ProfileCommand {
    public enum Kind {
        DRAFT,
        CONTACT,
        SUBMIT,
        VISIBILITY
    }

    public final Kind kind;
    public final long baseVersion;
    public final String operationId;
    public final Map<String, Object> payload;

    public ProfileCommand(Kind kind, long baseVersion, String id, Map<String, Object> payload) {
        if (baseVersion < 0 || !ProfileSchema.validOperationId(id))
            throw new IllegalArgumentException("invalid_command");
        this.kind = kind;
        this.baseVersion = baseVersion;
        this.operationId = id;
        this.payload = Collections.unmodifiableMap(new HashMap<>(payload));
    }

    public boolean isPriorResult(Map<String, Object> record) {
        return record != null
                && operationId.equals(record.get("lastOperationId"))
                && ProfileSchema.version(record) == baseVersion + 1
                && ProfileSchema.samePayload(record, payload);
    }

    public boolean conflicts(Map<String, Object> record) {
        return record != null
                        && operationId.equals(record.get("lastOperationId"))
                        && !isPriorResult(record)
                || ProfileSchema.version(record) != baseVersion && !isPriorResult(record);
    }
}
