package com.gmail.manhle240406.project.adoption;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import com.gmail.manhle240406.project.pet.PetSchema;
import com.gmail.manhle240406.project.listing.ListingSchema;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** F08 private-only immutable care. No listing binding, public summary or media expansion. */
public final class PetCareRepository {
    public interface Commands {Task<Map<String,Object>> save(Map<String,Object> intent);}
    public static final Commands UNAVAILABLE=intent->Tasks.forException(new IllegalStateException("care_callable_not_authorized"));
    private final FirebaseFirestore db;private final SessionGateway sessions;private final Commands commands;private final java.util.concurrent.atomic.AtomicBoolean pending=new java.util.concurrent.atomic.AtomicBoolean();
    public PetCareRepository(FirebaseFirestore db,SessionGateway s){this(db,s,UNAVAILABLE);}
    public PetCareRepository(FirebaseFirestore db,SessionGateway s,Commands commands){this.db=db;sessions=s;this.commands=Objects.requireNonNull(commands);}
    private void current(SessionEpoch.Ticket t){if(t.uid==null||!sessions.current(t))throw new IllegalStateException("session_changed");}
    private <T> Task<T> scoped(SessionEpoch.Ticket t,Task<T> task){return Tasks.withTimeout(task,15,TimeUnit.SECONDS).continueWith(done->{current(t);if(!done.isSuccessful())throw Objects.requireNonNull(done.getException());return done.getResult();});}
    public Task<DocumentSnapshot> read(SessionEpoch.Ticket t,String petId){current(t);return scoped(t,db.document("petCare/"+AdoptionValues.listingId(petId)).get(Source.SERVER));}
    public Task<QuerySnapshot> history(SessionEpoch.Ticket t,String petId,DocumentSnapshot cursor){current(t);String path="petCare/"+AdoptionValues.listingId(petId)+"/revisions";Query q=db.collection(path).orderBy("careVersion",Query.Direction.DESCENDING).limit(20);if(cursor!=null){if(!cursor.getReference().getParent().getPath().equals(path))throw new IllegalArgumentException("foreign_cursor");q=q.startAfter(cursor);}return scoped(t,q.get(Source.SERVER));}
    public Task<Map<String,Object>> save(SessionEpoch.Ticket t,String petId,String op,long careVersion,long identityVersion,long custodyVersion,Map<String,Object> payload,String medication){
        current(t);AdoptionValues.listingId(petId);AdoptionValues.listingId(op);if(careVersion<0||identityVersion<1||custodyVersion<0)throw new IllegalArgumentException("care_base");
        PetCareContract.validate(payload,medication,java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        Map<String,Object> intent=ListingSchema.freeze(PetSchema.map("petId",petId,"operationId",op,"expectedCareVersion",careVersion,"expectedIdentityVersion",identityVersion,"expectedCustodyVersion",custodyVersion,"payload",payload,"medicationNotes",medication));
        if(!pending.compareAndSet(false,true))return Tasks.forException(new IllegalStateException("care_request_in_flight"));
        try{return scoped(t,commands.save(intent)).addOnCompleteListener(done->pending.set(false));}catch(RuntimeException failure){pending.set(false);throw failure;}
    }
    public Task<DocumentSnapshot> receipt(SessionEpoch.Ticket t,String pet,String op){current(t);return scoped(t,db.document("petCare/"+AdoptionValues.listingId(pet)+"/operations/"+AdoptionValues.listingId(op)).get(Source.SERVER));}
}
