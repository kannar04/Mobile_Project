package com.gmail.manhle240406.project.security;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static com.gmail.manhle240406.project.security.AccountSecurityRepository.*;

public class AccountSecurityTest {
    static <T> CompletableFuture<T> ok(T v) { return CompletableFuture.completedFuture(v); }
    static <T> CompletableFuture<T> fail(Result r) { CompletableFuture<T> f=new CompletableFuture<>(); f.completeExceptionally(new Failure(r)); return f; }
    static class SessionFake implements Session {
        String uid="A"; int epoch; boolean signedOut;
        public Object capture() { return epoch; } public String uid(Object t) { return uid; }
        public boolean current(Object t) { return (int)t==epoch; }
        public void signOut() { signedOut=true; uid=null; epoch++; }
    }
    static class ProviderFake implements Provider {
        int resets,updates; boolean password=true; CompletionStage<Void> reset=ok(null),reauth=ok(null),update=ok(null);
        public CompletionStage<Void> reset(String email,String lang) { resets++; return reset; }
        public CompletionStage<Boolean> hasPassword(String uid) { return ok(password); }
        public CompletionStage<Void> reauthenticate(String uid,String secret) { return reauth; }
        public CompletionStage<Void> update(String uid,String secret) { updates++; return update; }
    }
    final ProviderFake provider=new ProviderFake(); final SessionFake session=new SessionFake();
    final AtomicLong clock=new AtomicLong(); int cleanups;
    AccountSecurityRepository repo(boolean config) { return new AccountSecurityRepository(provider,session,u->{cleanups++;return ok(null);},clock::get,config); }
    static Result await(CompletionStage<Result> stage) { return stage.toCompletableFuture().join(); }
    @Test public void resetReceiptIsNeutralAndCooldownIsMonotonic() {
        AccountSecurityRepository r=repo(true);
        assertEquals(Result.CHECK_EMAIL_IF_ELIGIBLE,await(r.requestReset("unknown@example.test","vi")));
        assertEquals(Result.COOLDOWN,await(r.requestReset("eligible@example.test","en")));
        clock.set(60_000); assertEquals(Result.CHECK_EMAIL_IF_ELIGIBLE,await(r.requestReset("eligible@example.test","en")));
        assertEquals(2,provider.resets); assertFalse(session.signedOut);
    }
    @Test public void configurationAndInputAreCheckedBeforeAnyProviderRequest() {
        assertEquals(Result.CONFIGURATION_REQUIRED,await(repo(false).requestReset("a@example.test","vi")));
        assertEquals(Result.INVALID_INPUT,await(repo(true).requestReset("bad","vi")));
        assertEquals(Result.INVALID_INPUT,await(repo(true).requestReset("a@example.test","fr")));
        assertEquals(0,provider.resets);
    }
    @Test public void duplicateAndLateResetDoNotCrossSessions() {
        CompletableFuture<Void> pending=new CompletableFuture<>(); provider.reset=pending;
        AccountSecurityRepository r=repo(true); CompletionStage<Result> first=r.requestReset("a@example.test","en");
        assertEquals(Result.BUSY,await(r.requestReset("a@example.test","en")));
        session.epoch++; pending.complete(null); assertEquals(Result.SESSION_CHANGED,await(first));
    }
    @Test public void googleOnlyDoesNotReauthenticateOrLinkPassword() {
        provider.password=false;
        assertEquals(Result.PROVIDER_MANAGED,await(repo(true).changePassword("old","new","new")));
        assertEquals(0,provider.updates); assertFalse(session.signedOut);
    }
    @Test public void wrongCredentialNeverUpdates() {
        provider.reauth=fail(Result.WRONG_CREDENTIAL);
        assertEquals(Result.WRONG_CREDENTIAL,await(repo(true).changePassword("old","new","new")));
        assertEquals(0,provider.updates); assertEquals(0,cleanups);
    }
    @Test public void successfulUpdateRequiresExplicitSignInAndCleanup() {
        assertEquals(Result.SIGN_IN_REQUIRED,await(repo(true).changePassword("old","new","new")));
        assertTrue(session.signedOut); assertEquals(1,cleanups); assertEquals(1,provider.updates);
    }
    @Test public void ambiguousUpdateIsNeverSilentlyReplayed() {
        provider.update=fail(Result.NETWORK);
        assertEquals(Result.OUTCOME_UNKNOWN,await(repo(true).changePassword("old","new","new")));
        assertTrue(session.signedOut); assertEquals(1,provider.updates); assertEquals(1,cleanups);
    }
    @Test public void accountSwitchDuringReauthPreventsUpdate() {
        CompletableFuture<Void> reauth=new CompletableFuture<>(); provider.reauth=reauth;
        CompletionStage<Result> result=repo(true).changePassword("old","new","new");
        session.epoch++; session.uid="B"; reauth.complete(null);
        assertEquals(Result.SESSION_CHANGED,await(result)); assertEquals(0,provider.updates); assertFalse(session.signedOut);
    }
    @Test public void failedLocalCleanupCannotBeReportedAsCompleted() {
        AccountSecurityRepository r=new AccountSecurityRepository(provider,session,u->fail(Result.UNAVAILABLE),clock::get,true);
        assertEquals(Result.CLEANUP_REQUIRED,await(r.changePassword("old","new","new"))); assertTrue(session.signedOut);
    }
    @Test public void lateWrongCredentialFromOldSessionIsDiscarded() {
        CompletableFuture<Void> reauth=new CompletableFuture<>(); provider.reauth=reauth;
        CompletionStage<Result> result=repo(true).changePassword("old","new","new");
        session.epoch++; session.uid="B"; reauth.completeExceptionally(new Failure(Result.WRONG_CREDENTIAL));
        assertEquals(Result.SESSION_CHANGED,await(result)); assertEquals(0,provider.updates);
    }
}
