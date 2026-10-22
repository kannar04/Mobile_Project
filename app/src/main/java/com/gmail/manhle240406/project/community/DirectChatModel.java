package com.gmail.manhle240406.project.community;
import android.app.Application;import androidx.lifecycle.*;
import com.gmail.manhle240406.project.*;import com.gmail.manhle240406.project.foundation.*;import com.gmail.manhle240406.project.media.ManagedMediaClient;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.*;
import static com.gmail.manhle240406.project.adoption.AdoptionFlowModel.*;

/** Private text and receipt intent remain in session RAM; logout destroys both. */
public final class DirectChatModel extends AndroidViewModel {
 public final MutableLiveData<Long> changes=new MutableLiveData<>(0L);public final List<Map<String,Object>> messages=new ArrayList<>();public String id,peerUid,body="",error="";public boolean busy,sending,blocked,blockedByMe,lost,loaded;public Long before;private long generation,event;private ListenerRegistration signal;private boolean signalReady,signalDirty;private final SessionGateway sessions;private final SessionEpoch.Ticket ticket;private Map<String,Object> pending;private final Runnable invalidation=this::clear;
 public DirectChatModel(Application app){super(app);sessions=((AniloveApplication)app).foundation().sessions;ticket=sessions.capture();sessions.onInvalidated(invalidation);if(ticket.uid==null)lost=true;}
 public String uid(){return ticket.uid;}public boolean current(){return !lost&&sessions.current(ticket);}public void changed(){changes.setValue(++event);}
 public void open(String conversation){if(id==null){id=conversation;load(false);}}
 public void load(boolean older){if(!current()||id==null||busy||older&&before==null)return;busy=true;long g=generation;long since=0;for(Map<String,Object> m:messages)since=Math.max(since,number(m.get("sequence")));Map<String,Object> request=map("conversationId",id,"before",older?before:null,"since",since);changed();ManagedMediaClient.call("directThread",request).addOnCompleteListener(t->{if(!current()||g!=generation)return;busy=false;if(!t.isSuccessful()){error="read";changed();return;}Map<String,Object> r=t.getResult();peerUid=(String)r.get("peerUid");blocked=Boolean.TRUE.equals(r.get("blocked"));blockedByMe=Boolean.TRUE.equals(r.get("blockedByMe"));if(!Boolean.TRUE.equals(r.get("unchanged"))){for(Map<String,Object> message:rows(r.get("messages"))){messages.removeIf(m->Objects.equals(m.get("messageId"),message.get("messageId")));messages.add(message);}messages.sort(java.util.Comparator.comparingLong(m->number(m.get("sequence"))));while(messages.size()>100)messages.remove(0);if(older||before==null)before=r.get("next")==null?null:number(r.get("next"));}loaded=true;error="";changed();refreshSignal();});}
 public void startRealtime(){if(signal!=null||id==null||!current())return;signalReady=false;signal=ConversationSnapshots.signal("direct_"+id,(snapshot,failure)->{if(!current())return;if(failure!=null){error="read";changed();return;}if(snapshot==null||snapshot.getMetadata().isFromCache())return;if(!signalReady){signalReady=true;if(!snapshot.exists())return;}else if(!snapshot.exists())return;signalDirty=true;refreshSignal();});}
 private void refreshSignal(){if(!signalDirty||busy)return;signalDirty=false;load(false);}
 public void stopRealtime(){if(signal!=null){signal.remove();signal=null;signalReady=false;signalDirty=false;}}
 public void send(){if(!current()||sending||blocked||id==null)return;if(pending==null){String text=body.trim();if(text.isEmpty()||text.codePointCount(0,text.length())>4000)return;pending=map("conversationId",id,"operationId",UUID.randomUUID().toString(),"body",text);}sending=true;long g=generation;changed();ManagedMediaClient.call("directSend",pending).addOnCompleteListener(t->{if(!current()||g!=generation)return;sending=false;if(t.isSuccessful()){body="";pending=null;error="";load(false);}else error="send";changed();});}
 public boolean pending(){return pending!=null;}
 public void block(){if(!current()||sending||id==null)return;sending=true;long g=generation;ManagedMediaClient.call("directBlock",map("conversationId",id,"operationId",UUID.randomUUID().toString(),"blocked",!blockedByMe)).addOnCompleteListener(t->{if(!current()||g!=generation)return;sending=false;if(t.isSuccessful())load(false);else error="block";changed();});}
 private void clear(){stopRealtime();generation++;lost=true;body="";id=null;peerUid=null;pending=null;messages.clear();busy=false;sending=false;changed();}
 @Override protected void onCleared(){clear();sessions.removeInvalidation(invalidation);}
}
