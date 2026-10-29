package com.gmail.manhle240406.project.wiring;

import java.util.*;

/**
 * Current Blaze runtime availability. Server rules and callable authorization remain authoritative.
 */
public final class SparkCapabilities {
    private SparkCapabilities() {}

    public static boolean uploadsEnabled() {
        return true;
    }

    public static boolean trustedCommandsEnabled() {
        return true;
    }

    public static final Set<String> MANAGED_ROUTES = Collections.emptySet();
}
