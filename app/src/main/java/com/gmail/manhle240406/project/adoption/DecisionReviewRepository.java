package com.gmail.manhle240406.project.adoption;
import com.gmail.manhle240406.project.foundation.*;
import com.google.firebase.firestore.FirebaseFirestore;
/** decision data interface; not wired to app presentation or privileged runtime. */
public final class DecisionReviewRepository extends SafetyRepository {
    public DecisionReviewRepository(FirebaseFirestore db, SessionGateway sessions, Commands commands) {
        super(db, sessions, commands, "decision");
    }
}