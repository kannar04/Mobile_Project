package com.gmail.manhle240406.project.foundation;

import org.junit.Test;
import static org.junit.Assert.*;

public class SessionEpochTest {
    @Test public void accountSwitchDropsOldResultsEvenAfterSwitchingBack() {
        SessionEpoch epoch = new SessionEpoch();
        epoch.change("A");
        SessionEpoch.Ticket oldA = epoch.capture();
        epoch.change("B");
        assertFalse(epoch.current(oldA));
        epoch.change("A");
        assertFalse(epoch.current(oldA));
        assertTrue(epoch.current(epoch.capture()));
    }
    @Test public void logoutImmediatelyInvalidatesPendingSameAccountCallbacks() {
        SessionEpoch epoch = new SessionEpoch();
        epoch.change("A");
        SessionEpoch.Ticket old = epoch.capture();
        epoch.invalidate();
        assertFalse(epoch.current(old));
    }
    @Test public void tokenRefreshForSameUidDoesNotResetPendingOperation() {
        SessionEpoch epoch = new SessionEpoch();
        epoch.change("A");
        SessionEpoch.Ticket pending = epoch.capture();
        epoch.change("A");
        assertTrue(epoch.current(pending));
        epoch.change(null);
        assertFalse(epoch.current(pending));
    }
    @Test public void uuidValidationRejectsPathInjectionAndMalformedIds() {
        assertTrue(FoundationDocuments.validOperationId("12345678-1234-1234-1234-123456789abc"));
        assertFalse(FoundationDocuments.validOperationId("../securitySubjects/admin"));
        assertFalse(FoundationDocuments.validOperationId(null));
        assertFalse(FoundationDocuments.validOperationId("12345678-1234-1234-1234-123456789ABc"));
    }
}
