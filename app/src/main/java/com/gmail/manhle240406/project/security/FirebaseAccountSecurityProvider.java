package com.gmail.manhle240406.project.security;

import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.*;
import com.gmail.manhle240406.project.foundation.*;
import java.util.concurrent.*;
import static com.gmail.manhle240406.project.security.AccountSecurityRepository.*;

/** Explicitly injected Firebase adapter; never probes arbitrary email sign-in methods. */
public final class FirebaseAccountSecurityProvider implements Provider {
    private final FirebaseAuth auth;
    public FirebaseAccountSecurityProvider(FirebaseAuth auth) { this.auth=auth; }
    private FirebaseUser own(String uid) {
        FirebaseUser u=auth.getCurrentUser();
        if(u==null || !uid.equals(u.getUid())) throw new Failure(Result.SESSION_CHANGED);
        return u;
    }
    private static <T> CompletionStage<T> stage(Task<T> task,boolean reset) {
        CompletableFuture<T> result=new CompletableFuture<>();
        com.google.android.gms.tasks.Tasks.withTimeout(task,15,TimeUnit.SECONDS).addOnCompleteListener(Runnable::run,t->{
            if(t.isSuccessful()) { result.complete(t.getResult()); return; }
            Exception e=t.getException(); Result code=Result.UNAVAILABLE;
            if(e instanceof FirebaseNetworkException) code=Result.NETWORK;
            else if(e instanceof FirebaseTooManyRequestsException) code=Result.THROTTLED;
            else if(e instanceof FirebaseAuthWeakPasswordException) code=Result.POLICY;
            else if(e instanceof FirebaseAuthInvalidCredentialsException) code=Result.WRONG_CREDENTIAL;
            else if(e instanceof FirebaseAuthRecentLoginRequiredException) code=Result.WRONG_CREDENTIAL;
            // Some projects still enumerate via USER_NOT_FOUND. Normalize this reset outcome locally.
            if(reset && e instanceof FirebaseAuthException && java.util.Arrays.asList(
                    "ERROR_USER_NOT_FOUND","ERROR_USER_DISABLED").contains(((FirebaseAuthException)e).getErrorCode())) {
                result.complete(null); return;
            }
            result.completeExceptionally(new Failure(code));
        }); return result;
    }
    @Override public CompletionStage<Void> reset(String email,String language) {
        auth.setLanguageCode(language);
        return stage(auth.sendPasswordResetEmail(email),true); // Hosted handler; no caller continue URL.
    }
    @Override public CompletionStage<Boolean> hasPassword(String uid) {
        return stage(own(uid).reload(),false).thenApply(v->{
            for(UserInfo p:own(uid).getProviderData()) if("password".equals(p.getProviderId())) return true;
            return false;
        });
    }
    @Override public CompletionStage<Void> reauthenticate(String uid,String password) {
        FirebaseUser u=own(uid); String email=u.getEmail();
        if(email==null) throw new Failure(Result.PROVIDER_MANAGED);
        return stage(u.reauthenticate(EmailAuthProvider.getCredential(email,password)),false);
    }
    @Override public CompletionStage<Void> update(String uid,String password) { return stage(own(uid).updatePassword(password),false); }
    public static Session session(SessionGateway gateway) {
        return new Session() {
            public Object capture() { return gateway.capture(); }
            public String uid(Object t) { return ((SessionEpoch.Ticket)t).uid; }
            public boolean current(Object t) { return gateway.current((SessionEpoch.Ticket)t); }
            public void signOut() { gateway.logout(); }
        };
    }
}
