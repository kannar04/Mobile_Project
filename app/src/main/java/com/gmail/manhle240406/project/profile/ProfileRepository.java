package com.gmail.manhle240406.project.profile;

import com.gmail.manhle240406.project.foundation.SessionEpoch;
import com.gmail.manhle240406.project.foundation.SessionGateway;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Fixed UID documents; no listeners, offline writes, directory scans or client publication. */
public final class ProfileRepository {
    public enum Code { OK, CONFLICT, DENIED, UNAVAILABLE, UNKNOWN, MALFORMED, SESSION_CHANGED }
    public static final class Result<T> {
        public final Code code; public final T value;
        Result(Code code,T value) { this.code=code;this.value=value; }
    }
    public static final class Snapshot {
        public final Map<String,Object> draft,contact,control,published;
        public Map<String,Object> moderation;
        public final Code issue;
        Snapshot(Map<String,Object> d,Map<String,Object> c,Map<String,Object> v,Map<String,Object> p) {this(d,c,v,p,Code.OK);}
        Snapshot(Map<String,Object> d,Map<String,Object> c,Map<String,Object> v,Map<String,Object> p,Code issue) {draft=d;contact=c;control=v;published=p;this.issue=issue;}
    }
    public static final class Reviews {
        public final List<Map<String,Object>> rows;
        public final DocumentSnapshot cursor;
        Reviews(List<Map<String,Object>> rows,DocumentSnapshot cursor) {this.rows=Collections.unmodifiableList(rows);this.cursor=cursor;}
    }
    private final FirebaseFirestore db;
    private final SessionGateway sessions;
    public ProfileRepository(FirebaseFirestore db,SessionGateway sessions) {this.db=db;this.sessions=sessions;}
    private DocumentReference ref(String uid,ProfileCommand.Kind kind) {
        if(kind==ProfileCommand.Kind.CONTACT) return db.document("users/"+uid+"/private/contact");
        if(kind==ProfileCommand.Kind.VISIBILITY) return db.document("publicProfileControls/"+uid);
        return db.document("users/"+uid+"/profileDraft/current");
    }
    private Set<String> shape(ProfileCommand.Kind kind) {
        return kind==ProfileCommand.Kind.CONTACT?ProfileSchema.CONTACT:kind==ProfileCommand.Kind.VISIBILITY?ProfileSchema.CONTROL:ProfileSchema.DRAFT;
    }
    private Map<String,Object> checked(DocumentSnapshot doc,Set<String> shape,String uid) {
        if(!doc.exists()) return null;
        Map<String,Object> m=doc.getData();
        String timestamp=shape==ProfileSchema.PUBLIC?"publishedAt":"updatedAt";
        if(!ProfileSchema.validRecord(m,shape,uid) || !(m.get(timestamp) instanceof Timestamp)) throw new IllegalStateException("malformed");
        return Collections.unmodifiableMap(new HashMap<>(m));
    }
    private void shell(DocumentSnapshot doc,String uid) {
        Map<String,Object> m=doc.getData();
        if(m==null || !m.keySet().equals(new HashSet<>(Arrays.asList("uid","schemaVersion","createdAt")))
                || !uid.equals(m.get("uid")) || !Long.valueOf(1).equals(m.get("schemaVersion")) || !(m.get("createdAt") instanceof Timestamp)) throw new IllegalStateException("malformed");
    }
    public Task<Result<Snapshot>> load(SessionEpoch.Ticket ticket) {
        if(!sessions.current(ticket) || ticket.uid==null) return Tasks.forResult(new Result<>(Code.SESSION_CHANGED,null));
        String uid=ticket.uid;
        List<Task<DocumentSnapshot>> reads=Arrays.asList(db.document("users/"+uid).get(Source.SERVER),ref(uid,ProfileCommand.Kind.DRAFT).get(Source.SERVER),
            ref(uid,ProfileCommand.Kind.CONTACT).get(Source.SERVER),ref(uid,ProfileCommand.Kind.VISIBILITY).get(Source.SERVER),db.document("publicProfiles/"+uid).get(Source.SERVER),db.document("profileModeration/"+uid).get(Source.SERVER));
        Task<Snapshot> combined=Tasks.whenAllComplete(reads).continueWith(t->{
            if(!reads.get(0).isSuccessful()) throw Objects.requireNonNull(reads.get(0).getException());
            shell(reads.get(0).getResult(),uid);
            if(!reads.get(3).isSuccessful()) throw Objects.requireNonNull(reads.get(3).getException());
            Map<String,Object> controls=checked(reads.get(3).getResult(),ProfileSchema.CONTROL,uid);
            List<Map<String,Object>> safe=new ArrayList<>();Code issue=Code.OK;
            int[] positions={1,2,4};List<Set<String>> shapes=Arrays.asList(ProfileSchema.DRAFT,ProfileSchema.CONTACT,ProfileSchema.PUBLIC);
            for(int i=0;i<positions.length;i++) {
                Task<DocumentSnapshot> read=reads.get(positions[i]);
                if(!read.isSuccessful()){safe.add(null);issue=Code.UNAVAILABLE;continue;}
                try{safe.add(checked(read.getResult(),shapes.get(i),uid));}
                catch(IllegalStateException malformed){safe.add(null);issue=Code.MALFORMED;}
            }
            // Valid owner controls remain usable to hide even when content cannot be rendered.
            Snapshot result=new Snapshot(safe.get(0),safe.get(1),controls,safe.get(2),issue);
            if(reads.get(5).isSuccessful() && ProfileSchema.validModeration(reads.get(5).getResult().getData(),uid))result.moderation=reads.get(5).getResult().getData();
            return result;
        });
        return wrap(Tasks.withTimeout(combined,15,TimeUnit.SECONDS),ticket,false);
    }
    /** Exact own private draft only; onboarding never queries contact or public profiles. */
    public Task<Result<Map<String,Object>>> ownDraft(SessionEpoch.Ticket ticket) {
        if(ticket.uid==null || !sessions.current(ticket))return Tasks.forResult(new Result<>(Code.SESSION_CHANGED,null));
        Task<Map<String,Object>> read=ref(ticket.uid,ProfileCommand.Kind.DRAFT).get(Source.SERVER)
            .continueWith(done->{if(!done.isSuccessful())throw Objects.requireNonNull(done.getException());return checked(done.getResult(),ProfileSchema.DRAFT,ticket.uid);});
        return wrap(Tasks.withTimeout(read,15,TimeUnit.SECONDS),ticket,false);
    }
    public Task<Result<Map<String,Object>>> execute(SessionEpoch.Ticket ticket,ProfileCommand command) {
        if(!sessions.current(ticket) || ticket.uid==null) return Tasks.forResult(new Result<>(Code.SESSION_CHANGED,null));
        final String uid=ticket.uid;
        Task<Map<String,Object>> write=db.runTransaction(new TransactionOptions.Builder().setMaxAttempts(3).build(),tx->{
            if(!sessions.current(ticket)) throw new IllegalStateException("session_changed");
            shell(tx.get(db.document("users/"+uid)),uid);
            DocumentReference target=ref(uid,command.kind);
            Map<String,Object> current=checked(tx.get(target),shape(command.kind),uid);
            if(command.isPriorResult(current)) return current;
            if(command.conflicts(current)) throw new IllegalStateException("conflict");
            if(command.kind==ProfileCommand.Kind.SUBMIT || command.kind==ProfileCommand.Kind.VISIBILITY && "public".equals(command.payload.get("visibility"))) {
                throw new IllegalStateException("denied");
            }
            Map<String,Object> next=new HashMap<>(command.payload);
            if(command.kind==ProfileCommand.Kind.SUBMIT && (current==null || !"draft".equals(current.get("submission")) || ProfileSchema.sensitive(current))) throw new IllegalStateException("denied");
            if(command.kind==ProfileCommand.Kind.SUBMIT && !ProfileSchema.samePayload(current,ProfileSchema.publicContent(next))) throw new IllegalStateException("conflict");
            next.put("ownerUid",uid);next.put("schemaVersion",1L);next.put("version",command.baseVersion+1);next.put("lastOperationId",command.operationId);
            next.put("updatedAt",FieldValue.serverTimestamp());
            Map<String,Object> validation=new HashMap<>(next);
            if(!ProfileSchema.validRecord(validation,shape(command.kind),uid)) throw new IllegalStateException("malformed");
            if(!sessions.current(ticket)) throw new IllegalStateException("session_changed");
            if(command.kind==ProfileCommand.Kind.DRAFT && current==null) {
                DocumentReference moderation=db.document("profileModeration/"+uid);
                DocumentSnapshot existingModeration=tx.get(moderation);
                if(!existingModeration.exists())tx.set(moderation,moderationDefault(uid));
            }
            if(command.kind==ProfileCommand.Kind.VISIBILITY) {
                Map<String,Object> base=new HashMap<>();base.put("draft",0L);base.put("control",command.baseVersion);base.put("publication",0L);base.put("moderation",0L);
                Map<String,Object> result=new HashMap<>();result.put("control",command.baseVersion+1);result.put("publication",0L);
                Map<String,Object> receipt=new HashMap<>();receipt.put("schemaVersion",1L);receipt.put("ownerUid",uid);receipt.put("operationId",command.operationId);receipt.put("command","hide");receipt.put("base",base);receipt.put("result",result);receipt.put("input",command.payload);receipt.put("consent",false);receipt.put("committedAt",FieldValue.serverTimestamp());
                tx.set(db.document("users/"+uid+"/profileOperations/"+command.operationId),receipt);
            }
            tx.set(target,next);
            return Collections.unmodifiableMap(next);
        });
        // Original task remains observed: timeout does not release the UI write lock.
        return wrap(write.continueWithTask(t->{
            if(!sessions.current(ticket))return Tasks.forException(new IllegalStateException("session_changed"));
            if(t.isSuccessful())return Tasks.forResult(t.getResult());Exception failure=t.getException();
            return Tasks.withTimeout(ref(uid,command.kind).get(Source.SERVER),15,TimeUnit.SECONDS).continueWithTask(read->{
                if(!sessions.current(ticket))return Tasks.forException(new IllegalStateException("session_changed"));
                if(read.isSuccessful()){Map<String,Object> m=checked(read.getResult(),shape(command.kind),uid);if(command.isPriorResult(m))return Tasks.forResult(m);}
                return Tasks.forException(failure==null?new IllegalStateException("unknown"):failure);
            });
        }),ticket,true);
    }
    private Map<String,Object> moderationDefault(String uid) {
        Map<String,Object> m=new HashMap<>();m.put("schemaVersion",1L);m.put("ownerUid",uid);m.put("hold","clear");m.put("moderationVersion",1L);m.put("reasonCode",null);m.put("decisionId",null);m.put("updatedAt",FieldValue.serverTimestamp());return m;
    }
    public Task<Result<Map<String,Object>>> publish(SessionEpoch.Ticket ticket,String op,long draftVersion,long controlVersion,long publicationVersion,long moderationVersion,Map<String,Object> candidate) {
        if(!sessions.current(ticket))return Tasks.forResult(new Result<>(Code.SESSION_CHANGED,null));
        String uid=ticket.uid;
        Map<String,Object> base=new HashMap<>();base.put("draft",draftVersion);base.put("control",controlVersion);base.put("publication",publicationVersion);base.put("moderation",moderationVersion);
        Map<String,Object> frozen=Collections.unmodifiableMap(new HashMap<>(candidate));
        return wrap(db.runTransaction(new TransactionOptions.Builder().setMaxAttempts(3).build(),tx->{
            if(!sessions.current(ticket))throw new IllegalStateException("session_changed");
            DocumentReference receipt=db.document("users/"+uid+"/profileOperations/"+op);
            Map<String,Object> prior=tx.get(receipt).getData();
            if(prior!=null){if(!base.equals(prior.get("base")) || !frozen.equals(prior.get("input")) || !Boolean.TRUE.equals(prior.get("consent")))throw new IllegalStateException("conflict");return prior;}
            Map<String,Object> d=checked(tx.get(ref(uid,ProfileCommand.Kind.DRAFT)),ProfileSchema.DRAFT,uid);
            Map<String,Object> c=checked(tx.get(ref(uid,ProfileCommand.Kind.VISIBILITY)),ProfileSchema.CONTROL,uid);
            Map<String,Object> publicRecord=checked(tx.get(db.document("publicProfiles/"+uid)),ProfileSchema.PUBLIC,uid);
            Map<String,Object> hold=tx.get(db.document("profileModeration/"+uid)).getData();
            if(d==null || ProfileSchema.version(d)!=draftVersion || ProfileSchema.version(c)!=controlVersion || (publicRecord==null?0:(Long)publicRecord.get("publicationVersion"))!=publicationVersion || !ProfileSchema.publicContent(d).equals(frozen))throw new IllegalStateException("conflict");
            if(hold==null || !"clear".equals(hold.get("hold")) || !Long.valueOf(moderationVersion).equals(hold.get("moderationVersion")))throw new IllegalStateException("denied");
            Map<String,Object> projection=new HashMap<>(frozen);projection.put("ownerUid",uid);projection.put("schemaVersion",1L);projection.put("publicationVersion",publicationVersion+1);projection.put("sourceDraftVersion",draftVersion);projection.put("publishedAt",FieldValue.serverTimestamp());
            Map<String,Object> control=new HashMap<>();control.put("ownerUid",uid);control.put("schemaVersion",1L);control.put("version",controlVersion+1);control.put("visibility","public");control.put("lastOperationId",op);control.put("updatedAt",FieldValue.serverTimestamp());
            Map<String,Object> r=new HashMap<>();r.put("schemaVersion",1L);r.put("ownerUid",uid);r.put("operationId",op);r.put("command","publish");r.put("base",base);r.put("result",new HashMap<String,Object>(){{put("control",controlVersion+1);put("publication",publicationVersion+1);}});r.put("input",frozen);r.put("consent",true);r.put("committedAt",FieldValue.serverTimestamp());
            if(!sessions.current(ticket))throw new IllegalStateException("session_changed");
            tx.set(db.document("publicProfiles/"+uid),projection);tx.set(ref(uid,ProfileCommand.Kind.VISIBILITY),control);tx.set(receipt,r);return r;
        }),ticket,true);
    }
    public Task<Result<Map<String,Object>>> operation(SessionEpoch.Ticket ticket,String op){return wrap(Tasks.withTimeout(db.document("users/"+ticket.uid+"/profileOperations/"+op).get(Source.SERVER).continueWith(t->{if(!t.isSuccessful())throw Objects.requireNonNull(t.getException());Map<String,Object> r=t.getResult().getData();if(r!=null && (!ticket.uid.equals(r.get("ownerUid")) || !op.equals(r.get("operationId")) || !Long.valueOf(1).equals(r.get("schemaVersion")) || !(r.get("committedAt") instanceof Timestamp) || r.size()!=9))throw new IllegalStateException("malformed");return r;}),15,TimeUnit.SECONDS),ticket,false);}
    public Task<Result<Map<String,Object>>> readPublic(SessionEpoch.Ticket ticket,String uid) {
        if(uid==null || uid.isEmpty() || uid.contains("/")) return Tasks.forResult(new Result<>(Code.DENIED,null));
        Task<Map<String,Object>> task=db.document("publicProfiles/"+uid).get(Source.SERVER).continueWith(t->{
            if(!t.isSuccessful()) throw Objects.requireNonNull(t.getException());
            return checked(t.getResult(),ProfileSchema.PUBLIC,uid);
        });
        return wrap(Tasks.withTimeout(task,15,TimeUnit.SECONDS),ticket,false);
    }
    public Task<Result<Reviews>> reviews(SessionEpoch.Ticket ticket,DocumentSnapshot cursor) {
        if(!sessions.current(ticket) || ticket.uid==null) return Tasks.forResult(new Result<>(Code.SESSION_CHANGED,null));
        Query q=db.collection("users/"+ticket.uid+"/profileReviews").orderBy("reviewedAt",Query.Direction.DESCENDING).orderBy(FieldPath.documentId(),Query.Direction.DESCENDING).limit(20);
        if(cursor!=null) q=q.startAfter(cursor);
        Task<Reviews> read=q.get(Source.SERVER).continueWith(t->{
            if(!t.isSuccessful()) throw Objects.requireNonNull(t.getException());
            List<Map<String,Object>> rows=new ArrayList<>();
            for(DocumentSnapshot d:t.getResult()) {
                Map<String,Object> m=d.getData();
                Set<String> required=new HashSet<>(Arrays.asList("draftVersion","decision","feedback","reviewedAt","reviewerRef"));
                Set<String> allowed=new HashSet<>(required);allowed.add("publicationVersion");
                if(m==null || !allowed.containsAll(m.keySet()) || !m.keySet().containsAll(required) || !ProfileSchema.positive(m.get("draftVersion"))
                    || !Arrays.asList("changes_requested","published","withdrawn").contains(m.get("decision")) || !(m.get("feedback") instanceof String)
                    || ProfileSchema.length((String)m.get("feedback"))>1000 || !(m.get("reviewerRef") instanceof String) || !(m.get("reviewedAt") instanceof Timestamp)
                    || (m.containsKey("publicationVersion") && !ProfileSchema.positive(m.get("publicationVersion")))) throw new IllegalStateException("malformed");
                rows.add(Collections.unmodifiableMap(new HashMap<>(m)));
            }
            List<DocumentSnapshot> docs=t.getResult().getDocuments();
            return new Reviews(rows,docs.size()==20?docs.get(docs.size()-1):null);
        });
        return wrap(Tasks.withTimeout(read,15,TimeUnit.SECONDS),ticket,false);
    }
    public Task<Result<Reviews>> operations(SessionEpoch.Ticket ticket,DocumentSnapshot start){Query q=db.collection("users/"+ticket.uid+"/profileOperations").orderBy("committedAt",Query.Direction.DESCENDING).orderBy(FieldPath.documentId(),Query.Direction.DESCENDING).limit(20);if(start!=null)q=q.startAfter(start);return wrap(Tasks.withTimeout(q.get(Source.SERVER).continueWith(t->{if(!t.isSuccessful())throw Objects.requireNonNull(t.getException());List<Map<String,Object>> rows=new ArrayList<>();for(DocumentSnapshot d:t.getResult()){Map<String,Object> r=d.getData();if(r==null || r.size()!=9 || !ticket.uid.equals(r.get("ownerUid")) || !d.getId().equals(r.get("operationId")) || !Long.valueOf(1).equals(r.get("schemaVersion")) || !Arrays.asList("publish","hide").contains(r.get("command")) || !(r.get("committedAt") instanceof Timestamp))throw new IllegalStateException("malformed");rows.add(Collections.unmodifiableMap(new HashMap<>(r)));}List<DocumentSnapshot> docs=t.getResult().getDocuments();return new Reviews(rows,docs.size()==20?docs.get(19):null);}),15,TimeUnit.SECONDS),ticket,false);}
    private <T> Task<Result<T>> wrap(Task<T> task,SessionEpoch.Ticket ticket,boolean write) {
        return task.continueWith(t->{
            if(!sessions.current(ticket)) return new Result<>(Code.SESSION_CHANGED,null);
            if(t.isSuccessful()) return new Result<>(Code.OK,t.getResult());
            Throwable e=t.getException();
            while(e!=null) {
                if("conflict".equals(e.getMessage())) return new Result<>(Code.CONFLICT,null);
                if("malformed".equals(e.getMessage())) return new Result<>(Code.MALFORMED,null);
                if("denied".equals(e.getMessage())) return new Result<>(Code.DENIED,null);
                if("session_changed".equals(e.getMessage())) return new Result<>(Code.SESSION_CHANGED,null);
                if(e instanceof FirebaseFirestoreException) {
                    FirebaseFirestoreException.Code c=((FirebaseFirestoreException)e).getCode();
                    if(c==FirebaseFirestoreException.Code.PERMISSION_DENIED || c==FirebaseFirestoreException.Code.UNAUTHENTICATED) return new Result<>(Code.DENIED,null);
                    if(c==FirebaseFirestoreException.Code.ABORTED) return new Result<>(Code.CONFLICT,null);
                }
                e=e.getCause();
            }
            return new Result<>(write?Code.UNKNOWN:Code.UNAVAILABLE,null);
        });
    }
}
