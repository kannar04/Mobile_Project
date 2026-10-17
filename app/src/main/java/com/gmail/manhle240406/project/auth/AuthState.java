package com.gmail.manhle240406.project.auth;

/** Private SDK-derived identity, without passwords, provider tokens or raw errors. */
public final class AuthState {
    public enum Authority { UNKNOWN, MISSING, ACTIVE, RESTRICTED }
    public enum Notice { NONE, CONFIGURATION, INVALID_CREDENTIALS, NETWORK, THROTTLED, CONFLICT,
        CANCELLED, SENT, SEND_FAILED, COOLDOWN, OUTCOME_UNKNOWN, PROVIDER_CLEAR_FAILED, PASSWORD_POLICY }
    public final String uid;
    public final String email;
    public final boolean pending, setupReady, verified, googleLinked, canLink, canSend;
    public final boolean verificationKnown;
    public final boolean configured, setupLoading;
    public final Authority authority;
    public com.gmail.manhle240406.project.foundation.MembershipRepository.State membership = com.gmail.manhle240406.project.foundation.MembershipRepository.State.UNKNOWN;
    public boolean interactionReady() { return uid!=null && setupReady && !pending && membership==com.gmail.manhle240406.project.foundation.MembershipRepository.State.READY; }
    public boolean communityReady() { return interactionReady() && verified && verificationKnown; }
    public final Notice notice;
    public AuthState(String uid, String email, boolean pending, boolean setupReady, boolean verified,
                     boolean googleLinked, boolean canLink, boolean canSend, Authority authority, Notice notice,
                     boolean verificationKnown, boolean configured, boolean setupLoading) {
        this.uid = uid; this.email = email; this.pending = pending; this.setupReady = setupReady;
        this.verified = verified; this.googleLinked = googleLinked; this.canLink = canLink; this.canSend = canSend;
        this.authority = authority; this.notice = notice;
        this.verificationKnown = verificationKnown;
        this.configured = configured; this.setupLoading = setupLoading;
    }
}
