package com.gmail.manhle240406.project.extensions;

/** A wall-clock change never extends an offline audience lease. Reboot fails closed. */
public final class OfflineLease {
    public static final long MAX_MILLIS = 24L * 60 * 60 * 1000;

    private OfflineLease() {}

    public static boolean valid(
            long validatedElapsed,
            long nowElapsed,
            long validatedBoot,
            long currentBoot,
            long duration) {
        return validatedBoot >= 0
                && validatedBoot == currentBoot
                && validatedElapsed >= 0
                && nowElapsed >= validatedElapsed
                && duration > 0
                && duration <= MAX_MILLIS
                && nowElapsed - validatedElapsed < duration;
    }
}
