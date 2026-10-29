package com.gmail.manhle240406.project.auth;

import static org.junit.Assert.*;

import org.junit.Test;

public class AuthSafetyTest {
    @Test
    public void logoutWinsDelayedSignInAndBlocksOverlappingNewMutation() {
        AuthMutationGate gate = new AuthMutationGate(false);
        AuthMutationGate.Ticket old = gate.begin();
        gate.logout();
        assertTrue(gate.visitor());
        assertFalse(gate.current(old));
        assertNull(gate.begin());
        assertEquals(AuthMutationGate.Settlement.RECONCILE_VISITOR, gate.settle(old));
        assertNotNull(gate.begin());
    }

    @Test
    public void staleCallbackCannotSignOutNewAccountOrReleaseItsLock() {
        AuthMutationGate gate = new AuthMutationGate(false);
        AuthMutationGate.Ticket old = gate.begin();
        gate.logout();
        gate.settle(old);
        AuthMutationGate.Ticket next = gate.begin();
        assertEquals(AuthMutationGate.Settlement.DISCARD, gate.settle(old));
        assertTrue(gate.current(next));
        assertTrue(gate.busy());
    }

    @Test
    public void timeoutDoesNotReleaseUnresolvedSdkOperation() {
        AuthMutationGate gate = new AuthMutationGate(false);
        AuthMutationGate.Ticket pending = gate.begin();
        assertNull(gate.begin());
        assertTrue(gate.current(pending));
        assertEquals(AuthMutationGate.Settlement.APPLY, gate.settle(pending));
        assertFalse(gate.busy());
    }

    @Test
    public void persistedVisitorIntentSuppressesRestoredAccountUntilExplicitSignIn() {
        AuthMutationGate restored = new AuthMutationGate(true);
        assertTrue(restored.visitor());
        assertNotNull(restored.begin());
        assertFalse(restored.visitor());
    }

    @Test
    public void successfulOperationCannotBeSettledTwice() {
        AuthMutationGate gate = new AuthMutationGate(false);
        AuthMutationGate.Ticket pending = gate.begin();
        assertEquals(AuthMutationGate.Settlement.APPLY, gate.settle(pending));
        assertEquals(AuthMutationGate.Settlement.DISCARD, gate.settle(pending));
    }

    @Test
    public void existingShortPasswordMaySignInButCannotRegister() {
        assertEquals(
                AuthInput.Error.NONE, AuthInput.validate("test@example.test", "old123", "", false));
        assertEquals(
                AuthInput.Error.PASSWORD_POLICY,
                AuthInput.validate("test@example.test", "old123", "old123", true));
    }

    @Test
    public void passwordWhitespaceIsNotSilentlyTrimmed() {
        assertEquals(
                AuthInput.Error.NONE,
                AuthInput.validate(" test@example.test ", " 1234567890 ", " 1234567890 ", true));
        assertEquals(
                AuthInput.Error.CONFIRMATION,
                AuthInput.validate("test@example.test", " 1234567890 ", "1234567890", true));
    }

    @Test
    public void registrationBoundsAndConfirmationAreEnforced() {
        assertEquals(
                AuthInput.Error.NONE,
                AuthInput.validate("test@example.test", "a".repeat(128), "a".repeat(128), true));
        assertEquals(
                AuthInput.Error.PASSWORD_POLICY,
                AuthInput.validate("test@example.test", "a".repeat(129), "a".repeat(129), true));
        assertEquals(
                AuthInput.Error.CONFIRMATION,
                AuthInput.validate("test@example.test", "longPassword1", "different", true));
    }

    @Test
    public void invalidAddressAndMissingPasswordAreSpecific() {
        assertEquals(AuthInput.Error.EMAIL, AuthInput.validate("bad", "validPassword1", "", false));
        assertEquals(
                AuthInput.Error.PASSWORD_REQUIRED,
                AuthInput.validate("test@example.test", "", "", false));
    }
}
