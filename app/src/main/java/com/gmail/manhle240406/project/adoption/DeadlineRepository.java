package com.gmail.manhle240406.project.adoption;

import com.gmail.manhle240406.project.foundation.*;
import com.google.firebase.firestore.FirebaseFirestore;

/** followup data interface; not wired to app presentation or privileged runtime. */
public final class DeadlineRepository extends SafetyRepository {
    public DeadlineRepository(FirebaseFirestore db, SessionGateway sessions, Commands commands) {
        super(db, sessions, commands, "followup");
    }

    public com.google.android.gms.tasks.Task<java.util.Map<String, Object>> evaluate(
            SessionEpoch.Ticket t, String id, String task) {
        AdoptionValues.listingId(id);
        if (!task.matches("day(1|7|14|30|60|90)")) throw new IllegalArgumentException("task_id");
        return execute(
                t,
                "evaluate_due",
                com.gmail.manhle240406.project.pet.PetSchema.map("planId", id, "task", task));
    }

    public com.google.android.gms.tasks.Task<com.google.firebase.firestore.DocumentSnapshot> plan(
            SessionEpoch.Ticket t, String id) {
        current(t);
        return scoped(
                t,
                db.document("followupPlans/" + AdoptionValues.listingId(id))
                        .get(com.google.firebase.firestore.Source.SERVER));
    }

    public com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> tasks(
            SessionEpoch.Ticket t, String id) {
        current(t);
        return scoped(
                t,
                db.collection("followupPlans/" + AdoptionValues.listingId(id) + "/tasks")
                        .orderBy("offset")
                        .limit(6)
                        .get(com.google.firebase.firestore.Source.SERVER));
    }
}
