package com.gmail.manhle240406.project.adoption;

import com.gmail.manhle240406.project.foundation.SessionEpoch;
import com.gmail.manhle240406.project.foundation.SessionGateway;
import com.google.firebase.firestore.FirebaseFirestore;

/** account data interface; not wired to app presentation or privileged runtime. */
public final class AccountAdminRepository extends SafetyRepository {
 public AccountAdminRepository(FirebaseFirestore db,SessionGateway sessions,Commands commands){super(db,sessions,commands,"account");}
 public com.google.android.gms.tasks.Task<java.util.Map<String,Object>> lookup(SessionEpoch.Ticket t,String uid,String email){if((uid==null)==(email==null))throw new IllegalArgumentException("exact_lookup");return execute(t,uid!=null?"lookup_uid":"lookup_email",com.gmail.manhle240406.project.pet.PetSchema.map(uid!=null?"uid":"email",uid!=null?uid:email));}
}
