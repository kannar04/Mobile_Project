package com.gmail.manhle240406.project;

import android.app.Application;
import com.gmail.manhle240406.project.foundation.FirebaseEnvironment;
import com.gmail.manhle240406.project.auth.AuthCoordinator;

/** App-owned container; activity recreation never repeats initialization or diagnostics. */
public final class AniloveApplication extends Application {
    private FirebaseEnvironment foundation;
    private AuthCoordinator auth;
    private com.gmail.manhle240406.project.listing.LocalListingDraftStore listingDrafts;
    @Override public void onCreate() {
        super.onCreate();
        foundation = FirebaseEnvironment.initialize(this);
        listingDrafts = new com.gmail.manhle240406.project.listing.LocalListingDraftStore(this, foundation.sessions);
        listingDrafts.clearOnSignedOutStartup();
        auth = new AuthCoordinator(this, foundation);
    }
    public FirebaseEnvironment foundation() { return foundation; }
    public AuthCoordinator auth() { return auth; }
    public com.gmail.manhle240406.project.listing.LocalListingDraftStore listingDrafts() { return listingDrafts; }
}
