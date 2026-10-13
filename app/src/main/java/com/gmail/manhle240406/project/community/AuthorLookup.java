package com.gmail.manhle240406.project.community;
import com.gmail.manhle240406.project.foundation.*;
import com.gmail.manhle240406.project.profile.ProfileRepository;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;

/** At most 20 session-memory aliases; never imports provider identity or private profile fields. */
public final class AuthorLookup implements AutoCloseable {
    private final SessionGateway sessions;private final SessionEpoch.Ticket ticket;private final ProfileRepository profiles;private final FirebaseFirestore db;
    private final LinkedHashMap<String,Map<String,Object>> cache=new LinkedHashMap<>();private final Map<String,Task<Map<String,Object>>> pending=new HashMap<>();private final Runnable invalidation=this::clear;
    public AuthorLookup(SessionGateway sessions){this.sessions=sessions;ticket=sessions.capture();db=FirebaseFirestore.getInstance();profiles=new ProfileRepository(db,sessions);sessions.onInvalidated(invalidation);}
    public void clear(){cache.clear();pending.clear();}
    public Task<Map<String,Object>> get(String uid){
        if(!sessions.current(ticket))return Tasks.forResult(null);
        if(cache.containsKey(uid))return Tasks.forResult(cache.get(uid));if(pending.containsKey(uid))return pending.get(uid);if(pending.size()>=4)return Tasks.forResult(null);
        Task<Boolean> permitted;
        if(uid.equals(ticket.uid))permitted=Tasks.withTimeout(Tasks.whenAllSuccess(db.document("publicProfileControls/"+uid).get(Source.SERVER),db.document("profileModeration/"+uid).get(Source.SERVER)),15,java.util.concurrent.TimeUnit.SECONDS).continueWith(t->{if(!t.isSuccessful())return false;List<Object> docs=t.getResult();return "public".equals(((DocumentSnapshot)docs.get(0)).getString("visibility"))&&"clear".equals(((DocumentSnapshot)docs.get(1)).getString("hold"));});
        else permitted=Tasks.forResult(true);
        Task<Map<String,Object>> task=permitted.continueWithTask(t->{if(!sessions.current(ticket)||!t.isSuccessful()||!Boolean.TRUE.equals(t.getResult()))return Tasks.forResult(null);return profiles.readPublic(ticket,uid).continueWith(r->r.isSuccessful()&&r.getResult().code==ProfileRepository.Code.OK?r.getResult().value:null);});
        pending.put(uid,task);task.addOnCompleteListener(r->{pending.remove(uid);if(!sessions.current(ticket))return;cache.put(uid,r.isSuccessful()?r.getResult():null);while(cache.size()>20)cache.remove(cache.keySet().iterator().next());});return task;
    }
    @Override public void close(){clear();sessions.removeInvalidation(invalidation);}
}
