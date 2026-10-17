package com.gmail.manhle240406.project.security;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.gmail.manhle240406.project.security.ClosurePolicy.*;

public class ClosurePolicyTest {
    final Instant now=Instant.parse("2026-10-17T00:00:00Z");
    Source source(String key,DataClass c,boolean ready,boolean held) { return new Source(key,1,4,c,ready,held,1); }
    @Test public void incompleteInventoryStillHasRecordableNeedsInventoryPhase() {
        Manifest m=new Manifest(1,false,Collections.singletonList(source("case",DataClass.CASE_EVIDENCE,false,true)));
        assertEquals(Phase.RECEIVED_NEEDS_INVENTORY,m.initialPhase()); assertFalse(m.executable());
        assertFalse(complete(m,Collections.singletonMap("case",4L),true,true));
    }
    @Test public void heldSourcesAndLineageAreNeverBlindPurges() {
        assertEquals(Action.MINIMIZE_RESTRICTED,source("history",DataClass.LINEAGE,true,false).action());
        assertEquals(Action.MINIMIZE_RESTRICTED,source("draft",DataClass.DISPOSABLE_PERSONAL,true,true).action());
        assertEquals(Action.REVOKE,source("feed",DataClass.PUBLIC_ACCESS,true,false).action());
        assertEquals(Action.REVOKE,source("feed",DataClass.PUBLIC_ACCESS,true,true).action());
        assertEquals(Action.PURGE_IF_UNHELD,source("draft",DataClass.DISPOSABLE_PERSONAL,true,false).action());
    }
    @Test public void providerAbsenceAloneDoesNotProvePrimaryStoreCleanup() {
        Manifest m=new Manifest(1,true,Collections.singletonList(source("feed",DataClass.PUBLIC_ACCESS,true,false)));
        assertFalse(complete(m,Collections.emptyMap(),true,true));
        assertFalse(complete(m,Collections.singletonMap("feed",3L),true,true));
        assertTrue(complete(m,Collections.singletonMap("feed",4L),true,true));
    }
    @Test public void holdReviewIsBoundedAndPolicyNeedsDatasetDates() {
        CoursePolicy p=new CoursePolicy("fixture_operator",1,now,now.plus(Duration.ofDays(10)));
        assertEquals(now.plus(Duration.ofDays(7)),nextHoldReview(now));
        assertEquals(p.purgeAt,terminalRetentionReview(now,p));
        assertThrows(IllegalArgumentException.class,()->new CoursePolicy("",1,now,now));
    }
    @Test public void staleGenerationOversizedBatchAndSixthAutomaticAttemptAreRejected() {
        Source s=source("media",DataClass.PUBLIC_ACCESS,true,false);
        verifyStep(s,4,20,4);
        assertThrows(IllegalStateException.class,()->verifyStep(s,3,20,0));
        assertThrows(IllegalStateException.class,()->verifyStep(s,4,21,0));
        assertThrows(IllegalStateException.class,()->verifyStep(s,4,1,5));
    }
    @Test public void recentAuthRejectsFutureAndStaleServerVerifiedTime() {
        requireRecentAuth(now.minusSeconds(300),now);
        assertThrows(IllegalStateException.class,()->requireRecentAuth(now.minusSeconds(301),now));
        assertThrows(IllegalStateException.class,()->requireRecentAuth(now.plusSeconds(1),now));
    }
    @Test public void duplicateSourceCannotHideOmittedInventory() {
        Source s=source("feed",DataClass.PUBLIC_ACCESS,true,false);
        assertThrows(IllegalArgumentException.class,()->new Manifest(1,true,Arrays.asList(s,s)));
    }
    static class ManagedFake implements AccountClosureRepository.Managed {
        int begins; CompletableFuture<AccountClosureRepository.Status> result=new CompletableFuture<>();
        public CompletionStage<AccountClosureRepository.Status> preflight() { return result; }
        public CompletionStage<AccountClosureRepository.Status> begin(String id,long p,long m) { begins++; return result; }
        public CompletionStage<AccountClosureRepository.Status> status(String id) { return result; }
    }
    @Test public void ownAcceptedRequestSignsOutAndClearsWithoutDirectProviderDeletion() {
        ManagedFake port=new ManagedFake(); AccountSecurityTest.SessionFake session=new AccountSecurityTest.SessionFake();
        int[] clear={0}; AccountClosureRepository r=new AccountClosureRepository(port,session,u->{clear[0]++;return CompletableFuture.completedFuture(null);});
        CompletionStage<AccountClosureRepository.Status> result=r.begin("request_1",1,1);
        assertThrows(IllegalStateException.class,()->r.begin("request_2",1,1));
        port.result.complete(new AccountClosureRepository.Status("A","request_1",1,1,1,Phase.RECEIVED_NEEDS_INVENTORY,true));
        assertEquals(Phase.RECEIVED_NEEDS_INVENTORY,result.toCompletableFuture().join().phase);
        assertTrue(session.signedOut); assertEquals(1,clear[0]); assertEquals(1,port.begins);
    }
    @Test public void forgedOtherOwnerCannotClearOrSignOutCurrentUser() {
        ManagedFake port=new ManagedFake(); AccountSecurityTest.SessionFake session=new AccountSecurityTest.SessionFake();
        AccountClosureRepository r=new AccountClosureRepository(port,session,u->{fail("foreign cleanup");return null;});
        CompletionStage<AccountClosureRepository.Status> result=r.begin("request_1",1,1);
        port.result.complete(new AccountClosureRepository.Status("B","request_1",1,1,1,Phase.CLOSING,true));
        assertThrows(CompletionException.class,()->result.toCompletableFuture().join()); assertFalse(session.signedOut);
        assertThrows(IllegalStateException.class,()->r.begin("request_2",1,1));
    }
    @Test public void responseLostReconcilesOriginalRequestWithoutAnotherBegin() {
        ManagedFake port=new ManagedFake(); AccountSecurityTest.SessionFake session=new AccountSecurityTest.SessionFake();
        AccountClosureRepository r=new AccountClosureRepository(port,session,u->CompletableFuture.completedFuture(null));
        CompletionStage<AccountClosureRepository.Status> result=r.begin("request_1",1,1);
        port.result.completeExceptionally(new IllegalStateException("network"));
        assertThrows(CompletionException.class,()->result.toCompletableFuture().join());
        port.result=CompletableFuture.completedFuture(new AccountClosureRepository.Status("A","request_1",1,1,1,Phase.CLOSING,true));
        assertEquals("request_1",r.reconcile().toCompletableFuture().join().requestId); assertEquals(1,port.begins);
    }
    @Test public void cleanupFailureStillExposesAcceptedStatusAndSignsOut() {
        ManagedFake port=new ManagedFake(); AccountSecurityTest.SessionFake session=new AccountSecurityTest.SessionFake();
        AccountClosureRepository r=new AccountClosureRepository(port,session,u->{CompletableFuture<Void> f=new CompletableFuture<>();f.completeExceptionally(new IllegalStateException("disk"));return f;});
        CompletionStage<AccountClosureRepository.Status> result=r.begin("request_1",1,1);
        port.result.complete(new AccountClosureRepository.Status("A","request_1",1,1,1,Phase.CLOSING,true));
        CompletionException e=assertThrows(CompletionException.class,()->result.toCompletableFuture().join());
        assertTrue(e.getCause() instanceof AccountClosureRepository.AcceptedCleanupFailure);
        assertTrue(((AccountClosureRepository.AcceptedCleanupFailure)e.getCause()).acceptedStatus.accepted); assertTrue(session.signedOut);
    }
}
