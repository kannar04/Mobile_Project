package com.gmail.manhle240406.project.foundation;

import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;
import com.gmail.manhle240406.project.BuildConfig;

/** No cloud action until the course operator has verified the intended project. */
public final class FirebaseEnvironment {
    public final FoundationResult.Status status;
    public final String alias;
    public final SessionGateway sessions;
    public final FoundationRepository repository;

    private FirebaseEnvironment(FoundationResult.Status status, String alias,
                                SessionGateway sessions, FoundationRepository repository) {
        this.status = status;
        this.alias = alias;
        this.sessions = sessions;
        this.repository = repository;
    }
    public static FirebaseEnvironment initialize(Context context) {
        String alias = BuildConfig.FIREBASE_EMULATORS ? "local_emulator" : "course_demo";
        if (!BuildConfig.FIREBASE_EMULATORS && !BuildConfig.COURSE_PROJECT_VERIFIED)
            return new FirebaseEnvironment(FoundationResult.Status.CONFIGURATION_ERROR, alias, null, null);
        try {
            FirebaseOptions options = FirebaseOptions.fromResource(context);
            if (options == null || options.getProjectId() == null
                    || options.getApplicationId().isEmpty() || options.getApiKey().isEmpty()
                    || (BuildConfig.FIREBASE_EMULATORS && !"demo-anilove".equals(options.getProjectId()))
                    || (!BuildConfig.FIREBASE_EMULATORS && (options.getProjectId().startsWith("demo-")
                        || !options.getProjectId().equals(BuildConfig.EXPECTED_FIREBASE_PROJECT))))
                return new FirebaseEnvironment(FoundationResult.Status.CONFIGURATION_ERROR, alias, null, null);
            FirebaseApp app = FirebaseApp.initializeApp(context, options);
            FirebaseAuth auth = FirebaseAuth.getInstance(app);
            FirebaseFirestore db = FirebaseFirestore.getInstance(app);
            db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder()
                            .setSizeBytes(100L * 1024L * 1024L).build()).build());
            if (BuildConfig.FIREBASE_EMULATORS) {
                // Android emulator reaches host loopback via this fixed alias; no user-supplied endpoints.
                auth.useEmulator("10.0.2.2", 9199);
                db.useEmulator("10.0.2.2", 8180);
            }
            SessionGateway sessions = new SessionGateway(auth);
            return new FirebaseEnvironment(FoundationResult.Status.READY, alias, sessions,
                    new FirestoreFoundationRepository(db, sessions));
        } catch (IllegalArgumentException | IllegalStateException error) {
            return new FirebaseEnvironment(FoundationResult.Status.CONFIGURATION_ERROR, alias, null, null);
        }
    }
}
