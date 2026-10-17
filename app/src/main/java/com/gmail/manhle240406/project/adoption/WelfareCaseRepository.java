package com.gmail.manhle240406.project.adoption;
import com.gmail.manhle240406.project.foundation.*;
import com.google.firebase.firestore.FirebaseFirestore;
/** welfare data interface; not wired to app presentation or privileged runtime. */
public final class WelfareCaseRepository extends SafetyRepository {
 public WelfareCaseRepository(FirebaseFirestore db,SessionGateway sessions,Commands commands){super(db,sessions,commands,"welfare");}
 public com.google.android.gms.tasks.Task<java.util.Map<String,Object>> evidence(SessionEpoch.Ticket t,String id,String purpose){AdoptionValues.listingId(id);AdoptionValues.text(purpose,200);if(purpose.isEmpty())throw new IllegalArgumentException("read_purpose");return execute(t,"read_evidence",com.gmail.manhle240406.project.pet.PetSchema.map("caseId",id,"purpose",purpose));}
}
