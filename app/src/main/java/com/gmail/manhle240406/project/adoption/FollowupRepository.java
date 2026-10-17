package com.gmail.manhle240406.project.adoption;
import com.gmail.manhle240406.project.foundation.*;
import com.google.firebase.firestore.FirebaseFirestore;
/** followup data interface; not wired to app presentation or privileged runtime. */
public final class FollowupRepository extends SafetyRepository {
 public FollowupRepository(FirebaseFirestore db,SessionGateway sessions,Commands commands){super(db,sessions,commands,"followup");}
 public com.google.android.gms.tasks.Task<com.google.firebase.firestore.DocumentSnapshot> plan(SessionEpoch.Ticket t,String id){current(t);return scoped(t,db.document("followupPlans/"+AdoptionValues.listingId(id)).get(com.google.firebase.firestore.Source.SERVER));}
 public com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> tasks(SessionEpoch.Ticket t,String id){current(t);return scoped(t,db.collection("followupPlans/"+AdoptionValues.listingId(id)+"/tasks").orderBy("offset").limit(6).get(com.google.firebase.firestore.Source.SERVER));}
}
