package com.gmail.manhle240406.project.adoption;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Private minimized history; no current phone/care/profile join and no business writes. */
public final class MemberHistoryRepository implements AutoCloseable {
 public static final class Filter {
  public final String key,value,signature;public final Timestamp from,to;
  public Filter(String key,String value,Timestamp from,Timestamp to){if(key!=null&&!Arrays.asList("role","sourceType","outcome").contains(key)||key==null&&value!=null||key!=null&&(value==null||!value.matches("[A-Za-z_]{1,40}"))||from!=null&&to!=null&&from.compareTo(to)>=0)throw new IllegalArgumentException("history_filter");if(key!=null&&!("role".equals(key)?Arrays.asList("Host","applicant","recipient"):"sourceType".equals(key)?Arrays.asList("application","placement"):Arrays.asList("pending","under_review","selected","preparing","cancellation_requested","withdrawn","rejected","cancelled","completed","closed_not_selected")).contains(value))throw new IllegalArgumentException("history_filter_value");this.key=key;this.value=value;this.from=from;this.to=to;signature=String.valueOf(key)+":"+value+":"+from+":"+to;}
 }
 public static final class Cursor {public final String uid,signature;public final DocumentSnapshot document;public Cursor(String uid,Filter f,DocumentSnapshot d){this.uid=uid;signature=f.signature;document=d;}}
 private final FirebaseFirestore db;private final SessionGateway sessions;private ListenerRegistration head;private final Runnable invalidation=this::stop;
 public MemberHistoryRepository(FirebaseFirestore db,SessionGateway sessions){this.db=db;this.sessions=sessions;sessions.onInvalidated(invalidation);}
 private void current(SessionEpoch.Ticket t){if(t.uid==null||!sessions.current(t))throw new IllegalStateException("session_changed");}
 private <T> Task<T> scoped(SessionEpoch.Ticket t,Task<T> task){return Tasks.withTimeout(task,15,TimeUnit.SECONDS).continueWith(done->{current(t);if(!done.isSuccessful())throw Objects.requireNonNull(done.getException());return done.getResult();});}
 private Query query(SessionEpoch.Ticket t,Filter f){Query q=db.collection("users/"+t.uid+"/adoptionHistory");if(f.key!=null)q=q.whereEqualTo(f.key,f.value);if(f.from!=null)q=q.whereGreaterThanOrEqualTo("recordCreatedAt",f.from);if(f.to!=null)q=q.whereLessThan("recordCreatedAt",f.to);return q.orderBy("recordCreatedAt",Query.Direction.DESCENDING).orderBy(FieldPath.documentId(),Query.Direction.DESCENDING).limit(20);}
 public Task<QuerySnapshot> page(SessionEpoch.Ticket t,Filter f,Cursor cursor){current(t);Query q=query(t,f);if(cursor!=null){if(!t.uid.equals(cursor.uid)||!f.signature.equals(cursor.signature)||!cursor.document.getReference().getParent().getPath().equals("users/"+t.uid+"/adoptionHistory"))throw new IllegalArgumentException("foreign_cursor");q=q.startAfter(cursor.document);}return scoped(t,q.get(Source.SERVER));}
 public synchronized void head(SessionEpoch.Ticket t,Filter f,com.google.firebase.firestore.EventListener<QuerySnapshot> listener){current(t);stop();head=query(t,f).addSnapshotListener((rows,error)->{if(sessions.current(t))listener.onEvent(rows,error);});}
 public Task<QuerySnapshot> timeline(SessionEpoch.Ticket t,String placement,DocumentSnapshot cursor){current(t);String path="placementHistory/"+AdoptionValues.listingId(placement)+"/events";Query q=db.collection(path).orderBy("recordedAt").orderBy(FieldPath.documentId()).limit(20);if(cursor!=null){if(!cursor.getReference().getParent().getPath().equals(path))throw new IllegalArgumentException("foreign_cursor");q=q.startAfter(cursor);}return scoped(t,q.get(Source.SERVER));}
 public Task<DocumentSnapshot> snapshot(SessionEpoch.Ticket t,String placement,long version){current(t);if(version<1)throw new IllegalArgumentException("snapshot_version");return scoped(t,db.document("placementSnapshots/"+AdoptionValues.listingId(placement)+"/versions/v"+version).get(Source.SERVER));}
 public synchronized void stop(){if(head!=null){head.remove();head=null;}}
 @Override public synchronized void close(){stop();sessions.removeInvalidation(invalidation);}
}
