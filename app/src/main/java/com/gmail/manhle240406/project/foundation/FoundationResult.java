package com.gmail.manhle240406.project.foundation;

/** No provider exception text or sensitive payload is exposed as an error message. */
public final class FoundationResult<T> {
    public enum Status {
        READY,
        PENDING,
        COMMITTED,
        SERVER,
        CACHED,
        CONFIGURATION_ERROR,
        SIGN_IN_REQUIRED,
        RESTRICTED,
        NOT_FOUND,
        CACHE_MISS,
        PERMISSION_DENIED,
        UNAVAILABLE,
        CHECKING_OUTCOME,
        MISSING_INDEX,
        CONFLICT,
        INVALID_INPUT,
        SESSION_CHANGED
    }

    public final Status status;
    public final T value;

    private FoundationResult(Status status, T value) {
        this.status = status;
        this.value = value;
    }

    public static <T> FoundationResult<T> of(Status status, T value) {
        return new FoundationResult<>(status, value);
    }

    public static <T> FoundationResult<T> error(Status status) {
        return new FoundationResult<>(status, null);
    }
}
