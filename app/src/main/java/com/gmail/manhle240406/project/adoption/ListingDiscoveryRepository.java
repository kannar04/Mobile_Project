package com.gmail.manhle240406.project.adoption;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Gated reader interfaces. Every live writer must be amended/verified before policy activation. */
public final class ListingDiscoveryRepository implements AutoCloseable {
    private final FirebaseFirestore db;private final SessionGateway sessions;private ListenerRegistration head;
    private final Runnable clear=this::detach;
    public ListingDiscoveryRepository(FirebaseFirestore db,SessionGateway s){this.db=db;sessions=s;sessions.onInvalidated(clear);}
    private void current(SessionEpoch.Ticket t){if(t.uid==null||!sessions.current(t))throw new IllegalStateException("session_changed");}
    private <T> Task<T> scoped(SessionEpoch.Ticket t,Task<T> task){return Tasks.withTimeout(task,15,TimeUnit.SECONDS).continueWith(done->{current(t);if(!done.isSuccessful())throw Objects.requireNonNull(done.getException());return done.getResult();});}
    private Query query(DiscoveryQuery spec,DocumentSnapshot cursor){
        Query q=db.collection("listingDiscovery").whereEqualTo("audience","signed_in").whereEqualTo("state","visible").whereEqualTo("availability",spec.availability);
        for(Map.Entry<String,String> filter:spec.filters.entrySet())q=q.whereEqualTo(filter.getKey(),filter.getValue());
        Query.Direction direction=spec.prefix.isEmpty()?Query.Direction.DESCENDING:Query.Direction.ASCENDING;
        if(!spec.prefix.isEmpty())q=q.whereGreaterThanOrEqualTo("nameKey",spec.prefix).whereLessThan("nameKey",spec.successor());
        q=q.orderBy(spec.orderField(),direction).orderBy(FieldPath.documentId(),direction).limit(20);
        if(cursor!=null){if(!cursor.getReference().getParent().getPath().equals("listingDiscovery"))throw new IllegalArgumentException("foreign_cursor");q=q.startAfter(cursor);}return q;
    }
    private Task<Void> validateFilters(SessionEpoch.Ticket t,DiscoveryQuery spec){
        current(t);if(!spec.filters.containsKey("breedId"))return Tasks.forResult(null);
        List<Task<DocumentSnapshot>> reads=Arrays.asList(db.document("catalogSpecies/"+spec.filters.get("speciesId")).get(Source.SERVER),db.document("catalogBreeds/"+spec.filters.get("breedId")).get(Source.SERVER));
        return scoped(t,Tasks.whenAllSuccess(reads)).continueWith(done->{current(t);if(!done.isSuccessful())throw Objects.requireNonNull(done.getException());spec.validateBreed(reads.get(0).getResult().getData(),reads.get(1).getResult().getData());return null;});
    }
    public Task<QuerySnapshot> page(SessionEpoch.Ticket t,DiscoveryQuery spec,DocumentSnapshot cursor){current(t);return validateFilters(t,spec).continueWithTask(validated->{current(t);if(!validated.isSuccessful())throw Objects.requireNonNull(validated.getException());return scoped(t,query(spec,cursor).get(Source.SERVER));}).continueWith(done->{current(t);if(!done.isSuccessful())throw Objects.requireNonNull(done.getException());QuerySnapshot rows=done.getResult();for(DocumentSnapshot row:rows)DiscoveryProjection.validate(row.getData(),row.getId());return rows;});}
    public Task<DocumentSnapshot> detail(SessionEpoch.Ticket t,String id){current(t);return scoped(t,db.document("listingDiscovery/"+AdoptionValues.listingId(id)).get(Source.SERVER)).continueWith(done->{current(t);if(!done.isSuccessful())throw Objects.requireNonNull(done.getException());DocumentSnapshot row=done.getResult();if(row.exists())DiscoveryProjection.validate(row.getData(),row.getId());return row;});}
    public synchronized void listenHead(SessionEpoch.Ticket t,DiscoveryQuery spec,com.google.firebase.firestore.EventListener<QuerySnapshot> callback){current(t);detach();head=query(spec,null).addSnapshotListener((rows,error)->{if(!sessions.current(t))return;if(error!=null){callback.onEvent(null,error);return;}try{if(rows!=null)for(DocumentSnapshot row:rows)DiscoveryProjection.validate(row.getData(),row.getId());callback.onEvent(rows,null);}catch(RuntimeException malformed){callback.onEvent(null,new FirebaseFirestoreException("discovery_schema",FirebaseFirestoreException.Code.DATA_LOSS));}});}
    public Task<Map<String,Map<String,Object>>> hosts(SessionEpoch.Ticket t,com.gmail.manhle240406.project.profile.ProfileRepository profiles,Collection<String> visibleUids){
        current(t);Set<String> ids=new LinkedHashSet<>(visibleUids);if(ids.size()>10)throw new IllegalArgumentException("host_batch_limit");List<Task<Void>> tasks=new ArrayList<>();Map<String,Map<String,Object>> result=Collections.synchronizedMap(new LinkedHashMap<>());
        for(String uid:ids){if(uid==null||uid.isEmpty()||uid.length()>128||uid.contains("/"))throw new IllegalArgumentException("host_id");tasks.add(profiles.readPublic(t,uid).continueWith(done->{current(t);result.put(uid,done.isSuccessful()&&done.getResult().code==com.gmail.manhle240406.project.profile.ProfileRepository.Code.OK?done.getResult().value:null);return null;}));}
        return scoped(t,Tasks.whenAll(tasks)).continueWith(done->{current(t);done.getResult();return Collections.unmodifiableMap(new LinkedHashMap<>(result));});
    }
    public synchronized void detach(){if(head!=null){head.remove();head=null;}}
    @Override public void close(){detach();sessions.removeInvalidation(clear);}
}
