package com.gmail.manhle240406.project.auth;
import com.gmail.manhle240406.project.foundation.MembershipRepository;
import org.junit.Test;
import static org.junit.Assert.*;

public final class InteractionReadinessTest {
    private AuthState state(boolean verified,boolean known,boolean pending,boolean setup,MembershipRepository.State membership){
        AuthState state=new AuthState("synthetic",null,pending,setup,verified,false,false,false,AuthState.Authority.MISSING,AuthState.Notice.NONE,known,true,false);state.membership=membership;return state;
    }
    @Test public void unverifiedOrdinaryMemberCanInteractButCannotPublish(){AuthState s=state(false,true,false,true,MembershipRepository.State.READY);assertTrue(s.interactionReady());assertFalse(s.communityReady());}
    @Test public void verifiedMemberCanPublishOnlyWhenTokenVerificationIsKnown(){assertTrue(state(true,true,false,true,MembershipRepository.State.READY).communityReady());assertFalse(state(true,false,false,true,MembershipRepository.State.READY).communityReady());}
    @Test public void restrictedMissingOrUnresolvedMembershipCannotInteract(){for(MembershipRepository.State membership:new MembershipRepository.State[]{MembershipRepository.State.RESTRICTED,MembershipRepository.State.UNKNOWN})assertFalse(state(true,true,false,true,membership).interactionReady());assertFalse(state(false,true,true,true,MembershipRepository.State.READY).interactionReady());assertFalse(state(false,true,false,false,MembershipRepository.State.READY).interactionReady());}
}
