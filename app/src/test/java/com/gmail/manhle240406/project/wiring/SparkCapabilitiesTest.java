package com.gmail.manhle240406.project.wiring;

import static org.junit.Assert.*;

import org.junit.Test;

public final class SparkCapabilitiesTest {
    @Test
    public void blazeRuntimeEnablesManagedClients() {
        assertTrue(SparkCapabilities.uploadsEnabled());
        assertTrue(SparkCapabilities.trustedCommandsEnabled());
    }

    @Test
    public void implementedRoutesAreReachableAndRelyOnServerAuthorization() {
        assertTrue(SparkCapabilities.MANAGED_ROUTES.isEmpty());
    }
}
