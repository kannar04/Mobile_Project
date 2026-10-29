package com.gmail.manhle240406.project;

import android.app.Application;

import com.gmail.manhle240406.project.auth.AuthCoordinator;
import com.gmail.manhle240406.project.foundation.FirebaseEnvironment;

/** App-owned container; activity recreation never repeats initialization or diagnostics. */
public final class AniloveApplication extends Application {
    private FirebaseEnvironment foundation;
    private AuthCoordinator auth;
    private com.gmail.manhle240406.project.community.CommunityOutbox communityOutbox;
    private com.gmail.manhle240406.project.wiring.AppServices services;
    private com.gmail.manhle240406.project.listing.LocalListingDraftStore listingDrafts;
    private com.gmail.manhle240406.project.adoption.SpeciesCatalogStore speciesCatalog;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(
                new com.gmail.manhle240406.project.wiring.MainNavigation());
        foundation = FirebaseEnvironment.initialize(this);
        com.gmail.manhle240406.project.media.SessionImageCache.initialize(this);
        com.gmail.manhle240406.project.media.ManagedMediaClient.configure(foundation.sessions);
        if (foundation.sessions != null)
            foundation.sessions.onInvalidated(
                    () -> {
                        com.gmail.manhle240406.project.community.SessionAliases.clear();
                        com.gmail.manhle240406.project.media.SessionImageCache.clearAll();
                    });
        communityOutbox =
                new com.gmail.manhle240406.project.community.CommunityOutbox(
                        this, foundation.sessions);
        listingDrafts =
                new com.gmail.manhle240406.project.listing.LocalListingDraftStore(
                        this, foundation.sessions);
        listingDrafts.clearOnSignedOutStartup();
        auth = new AuthCoordinator(this, foundation);
        services = new com.gmail.manhle240406.project.wiring.AppServices(this, foundation);
        speciesCatalog =
                new com.gmail.manhle240406.project.adoption.SpeciesCatalogStore(
                        this,
                        com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                        foundation.sessions);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        com.gmail.manhle240406.project.media.SessionImageCache.clear();
        com.gmail.manhle240406.project.community.SessionAliases.clear();
    }

    public FirebaseEnvironment foundation() {
        return foundation;
    }

    public AuthCoordinator auth() {
        return auth;
    }

    public com.gmail.manhle240406.project.community.CommunityOutbox communityOutbox() {
        return communityOutbox;
    }

    public com.gmail.manhle240406.project.wiring.AppServices services() {
        return services;
    }

    public com.gmail.manhle240406.project.listing.LocalListingDraftStore listingDrafts() {
        return listingDrafts;
    }

    public com.gmail.manhle240406.project.adoption.SpeciesCatalogStore speciesCatalog() {
        return speciesCatalog;
    }
}
