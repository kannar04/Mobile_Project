package com.gmail.manhle240406.project.adoption;
import com.gmail.manhle240406.project.foundation.*;import com.google.android.gms.tasks.*;import java.util.*;
/** Private status-card cache: bounded six pages/60 seconds, session-cleared, coalesced reads. */
public final class OwnedPets {
 private OwnedPets(){}private static SessionGateway owner;private static long generation;
 private static final class Entry {final Map<String,Object> value;final long at=android.os.SystemClock.elapsedRealtime();Entry(Map<String,Object> v){value=v;}}
 private static final LinkedHashMap<String,Entry> cache=new LinkedHashMap<>(6,.75f,true);private static final Map<String,Task<Map<String,Object>>> pending=new HashMap<>();
 public static synchronized void clear(){generation++;cache.clear();pending.clear();}
 public static synchronized Task<Map<String,Object>> read(SessionGateway sessions,int limit,String after){SessionEpoch.Ticket ticket=sessions.capture();if(ticket.uid==null)return Tasks.forException(new IllegalStateException("session_changed"));if(owner!=sessions){clear();owner=sessions;sessions.onInvalidated(OwnedPets::clear);}String key=ticket.uid+":"+limit+":"+after;Entry e=cache.get(key);if(e!=null&&android.os.SystemClock.elapsedRealtime()-e.at<60000)return Tasks.forResult(e.value);if(pending.containsKey(key))return pending.get(key);if(pending.size()>=6)return Tasks.forException(new IllegalStateException("read_limit"));long g=generation;Task<Map<String,Object>> task=AdoptionApi.call("petWorkspace",AdoptionFlowModel.map("limit",limit,"after",after)).continueWith(done->{if(!sessions.current(ticket))throw new IllegalStateException("session_changed");return done.getResult();});pending.put(key,task);task.addOnCompleteListener(done->{synchronized(OwnedPets.class){if(g!=generation)return;pending.remove(key);if(done.isSuccessful()){cache.put(key,new Entry(done.getResult()));while(cache.size()>6)cache.remove(cache.keySet().iterator().next());}}});return task;}
}
