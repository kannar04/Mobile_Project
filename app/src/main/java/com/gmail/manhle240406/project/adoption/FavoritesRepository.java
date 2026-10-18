package com.gmail.manhle240406.project.adoption;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import com.gmail.manhle240406.project.pet.PetSchema;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Private interest only; no snapshots, notification, reservation or protected-domain mutation. */
public final class FavoritesRepository implements AutoCloseable {
    private final FirebaseFirestore db;private final SessionGateway sessions;private ListenerRegistration head;
    private final Runnable clear=this::detach;
    private final java.util.concurrent.atomic.AtomicInteger resolving=new java.util.concurrent.atomic.AtomicInteger();
    public FavoritesRepository(FirebaseFirestore db,SessionGateway s){this.db=db;sessions=s;sessions.onInvalidated(clear);}
    private void current(SessionEpoch.Ticket t){if(t.uid==null||!sessions.current(t))throw new IllegalStateException("session_changed");}
    private String own(SessionEpoch.Ticket t){current(t);return "users/"+t.uid+"/";}
    private <T> Task<T> scoped(SessionEpoch.Ticket t,Task<T> task){return Tasks.withTimeout(task,15,TimeUnit.SECONDS).continueWith(done->{current(t);if(!done.isSuccessful())throw Objects.requireNonNull(done.getException());return done.getResult();});}
    private Query query(SessionEpoch.Ticket t,DocumentSnapshot cursor){String path=own(t)+"favorites";Query q=db.collection(path).orderBy("savedAt",Query.Direction.DESCENDING).orderBy(FieldPath.documentId(),Query.Direction.DESCENDING).limit(20);if(cursor!=null){if(!cursor.getReference().getParent().getPath().equals(path))throw new IllegalArgumentException("foreign_cursor");q=q.startAfter(cursor);}return q;}
    public Task<QuerySnapshot> page(SessionEpoch.Ticket t,DocumentSnapshot cursor){return scoped(t,query(t,cursor).get(Source.SERVER));}
    public Task<ListingRow> resolve(SessionEpoch.Ticket t,ListingDiscoveryRepository reader,String listingId){
        current(t);AdoptionValues.listingId(listingId);if(resolving.incrementAndGet()>5){resolving.decrementAndGet();return Tasks.forException(new IllegalStateException("detail_resolution_limit"));}
        Task<ListingRow> task;try{task=scoped(t,reader.detail(t,listingId)).continueWith(done->{current(t);if(!done.isSuccessful()){Exception error=done.getException();if(error instanceof FirebaseFirestoreException&&((FirebaseFirestoreException)error).getCode()==FirebaseFirestoreException.Code.PERMISSION_DENIED)return null;throw Objects.requireNonNull(error);}return done.getResult();});}catch(RuntimeException failed){resolving.decrementAndGet();throw failed;}
        task.addOnCompleteListener(Runnable::run,done->resolving.decrementAndGet());return task;
    }
    public synchronized void listenHead(SessionEpoch.Ticket t,com.google.firebase.firestore.EventListener<QuerySnapshot> callback){current(t);detach();head=query(t,null).addSnapshotListener((rows,error)->{if(sessions.current(t))callback.onEvent(rows,error);});}
    public synchronized void detach(){if(head!=null){head.remove();head=null;}}
    public Task<Map<String,Object>> setSaved(SessionEpoch.Ticket t,String listingId,boolean desired,String operationId,long favoriteVersion,long metaVersion){
        String own=own(t);AdoptionValues.listingId(listingId);AdoptionValues.listingId(operationId);if(favoriteVersion<0||metaVersion<0)throw new IllegalArgumentException("favorite_base");
        Map<String,Object> base=PetSchema.map("favoriteVersion",favoriteVersion,"metaVersion",metaVersion);String digest=AdoptionValues.digest(AdoptionValues.canonical(PetSchema.map("listingId",listingId,"desiredState",desired,"base",base)));
        return scoped(t,AdoptionApi.call("favorite",AdoptionFlowModel.map("operationId",operationId,"listingId",listingId,"desiredState",desired,"base",base)));
    }
    private Task<Map<String,Object>> commit(SessionEpoch.Ticket t,DocumentReference item,DocumentReference meta,DocumentReference receipt,String listing,boolean desired,String op,Map<String,Object> base,String digest){
        return scoped(t,db.runTransaction(new TransactionOptions.Builder().setMaxAttempts(3).build(),tx->{
            current(t);DocumentSnapshot prior=tx.get(receipt);if(prior.exists()){if(!digest.equals(prior.getString("inputDigest"))||!base.equals(prior.get("base"))||!Boolean.valueOf(desired).equals(prior.getBoolean("desiredState")))throw new IllegalStateException("intent_conflict");return prior.getData();}
            DocumentSnapshot old=tx.get(item),count=tx.get(meta);long fv=old.exists()?AdoptionValues.version(old.getData(),"favoriteVersion"):0,mv=count.exists()?AdoptionValues.version(count.getData(),"metaVersion"):0,active=count.exists()?AdoptionValues.version(count.getData(),"activeCount"):0;
            if(fv!=(Long)base.get("favoriteVersion")||mv!=(Long)base.get("metaVersion"))throw new IllegalStateException("version_conflict");if(active>200||old.exists()&&(!count.exists()||active==0))throw new IllegalStateException("favorite_meta_repair_required");
            int delta=(desired?1:0)-(old.exists()?1:0);if(delta>0){DocumentSnapshot target=tx.get(db.document("listingDiscovery/"+listing));if(!target.exists()||!"visible".equals(target.getString("state")))throw new IllegalStateException("unavailable");if(active==200)throw new IllegalStateException("favorite_cap");}
            if(delta==0&&!count.exists())return PetSchema.map("saved",false,"unchanged",true);
            long nextMv=mv+(delta==0?0:1),nextFv=desired?(old.exists()?fv:1):0;Object now=FieldValue.serverTimestamp();
            Map<String,Object> record=PetSchema.map("schemaVersion",1,"uid",t.uid,"listingId",listing,"operationId",op,"command","desired_saved_or_removed","desiredState",desired,"inputDigest",digest,"base",base,"result",PetSchema.map("favoriteVersion",nextFv,"metaVersion",nextMv,"activeCount",active+delta,"saved",desired),"delta",delta,"serverCreatedAt",now);
            current(t);if(delta!=0){if(desired)tx.set(item,PetSchema.map("schemaVersion",1,"uid",t.uid,"listingId",listing,"savedAt",now,"favoriteVersion",1,"lastOperationId",op));else tx.delete(item);tx.set(meta,PetSchema.map("schemaVersion",1,"uid",t.uid,"activeCount",active+delta,"metaVersion",nextMv,"lastOperationId",op,"updatedAt",now));}tx.set(receipt,record);return record;
        }));
    }
    @Override public void close(){detach();sessions.removeInvalidation(clear);}
}
