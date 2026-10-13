package com.gmail.manhle240406.project.community;

import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import com.google.firebase.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Schema2 social transactions. Public deployment remains controlled by operator policy. */
public final class SocialRepository {
    private final FirebaseFirestore db; private final SessionGateway sessions;
    public SocialRepository(FirebaseFirestore db,SessionGateway sessions){this.db=db;this.sessions=sessions;}
    public static Map<String,Object> map(Object...pairs){Map<String,Object> m=new HashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    public static long number(Map<String,Object> m,String key){Object n=m==null?null:m.get(key);if(n==null)return 0;if(!(n instanceof Long))throw new IllegalStateException("schema");return (Long)n;}
    public Task<QuerySnapshot> feed(DocumentSnapshot cursor){Query q=db.collection("communityFeed").whereEqualTo("schemaVersion",2L).whereEqualTo("audience","community").whereEqualTo("state","visible").orderBy("firstPublishedAt",Query.Direction.DESCENDING).orderBy(FieldPath.documentId(),Query.Direction.DESCENDING).limit(20);if(cursor!=null)q=q.startAfter(cursor);return bounded(q.get(Source.SERVER));}
    public Task<QuerySnapshot> around(Timestamp time,String id){return bounded(db.collection("communityFeed").whereEqualTo("schemaVersion",2L).whereEqualTo("audience","community").whereEqualTo("state","visible").orderBy("firstPublishedAt",Query.Direction.DESCENDING).orderBy(FieldPath.documentId(),Query.Direction.DESCENDING).startAt(time,id).limit(20).get(Source.SERVER));}
    public ListenerRegistration head(com.google.firebase.firestore.EventListener<QuerySnapshot> listener){return db.collection("communityFeed").whereEqualTo("schemaVersion",2L).whereEqualTo("audience","community").whereEqualTo("state","visible").orderBy("firstPublishedAt",Query.Direction.DESCENDING).orderBy(FieldPath.documentId(),Query.Direction.DESCENDING).limit(20).addSnapshotListener(listener);}
    public Task<QuerySnapshot> comments(String id,DocumentSnapshot cursor){Query q=db.collection("communityCommentFeed/"+id+"/comments").whereEqualTo("schemaVersion",2L).whereEqualTo("state","visible").orderBy("createdAt").orderBy(FieldPath.documentId()).limit(20);if(cursor!=null)q=q.startAfter(cursor);return bounded(q.get(Source.SERVER));}
    public ListenerRegistration commentPage(String id,DocumentSnapshot start,com.google.firebase.firestore.EventListener<QuerySnapshot> listener){Query q=db.collection("communityCommentFeed/"+id+"/comments").whereEqualTo("schemaVersion",2L).whereEqualTo("state","visible").orderBy("createdAt").orderBy(FieldPath.documentId()).limit(20);if(start!=null)q=q.startAfter(start);return q.addSnapshotListener(listener);}
    public Task<QuerySnapshot> revisions(String id,DocumentSnapshot start){Query q=db.collection("communityPosts/"+id+"/revisions").orderBy("contentVersion",Query.Direction.DESCENDING).orderBy(FieldPath.documentId(),Query.Direction.DESCENDING).limit(20);if(start!=null)q=q.startAfter(start);return bounded(q.get(Source.SERVER));}
    public Task<QuerySnapshot> own(String uid,DocumentSnapshot cursor){Query q=db.collection("communityPosts").whereEqualTo("authorUid",uid).orderBy("updatedAt",Query.Direction.DESCENDING).orderBy(FieldPath.documentId(),Query.Direction.DESCENDING).limit(20);if(cursor!=null)q=q.startAfter(cursor);return bounded(q.get(Source.SERVER));}
    public Task<DocumentSnapshot> read(String path){return bounded(db.document(path).get(Source.SERVER));}
    private <T> Task<T> bounded(Task<T> t){return Tasks.withTimeout(t,15,TimeUnit.SECONDS);}
    private DocumentReference ref(String p){return db.document(p);}
    public Task<Map<String,Object>> execute(SessionEpoch.Ticket ticket,SocialCommand c){
        Task<Map<String,Object>> attempt=db.runTransaction(new TransactionOptions.Builder().setMaxAttempts(3).build(),tx->{
            if(ticket.uid==null||!sessions.current(ticket))throw new IllegalStateException("session_changed");
            String uid=ticket.uid,p="communityPosts/"+c.postId;Object now=FieldValue.serverTimestamp();
            DocumentReference receipt=ref("users/"+uid+"/communityOperations/"+c.operationId);
            Map<String,Object> prior=tx.get(receipt).getData();if(prior!=null){if(!c.matches(prior))throw new IllegalStateException("conflict");return prior;}
            boolean like=c.command.equals("set_like"),comment=c.command.endsWith("comment");
            // Interaction clients cannot read another author's private root.
            Map<String,Object> feed=like||comment?tx.get(ref("communityFeed/"+c.postId)).getData():null;
            Map<String,Object> old=like||comment?null:tx.get(ref(p)).getData();
            Map<String,Object> result;
            if(like){
                Map<String,Object> oldLike=tx.get(ref(p+"/likes/"+uid)).getData();
                Map<String,Object> count=tx.get(ref("communityCounts/"+c.postId)).getData();
                if(number(oldLike,"likeVersion")!=c.targetVersion)throw new IllegalStateException("conflict");
                boolean desired=Boolean.TRUE.equals(c.input.get("active")),previous=oldLike!=null&&Boolean.TRUE.equals(oldLike.get("active"));
                if(desired && (feed==null||number(feed,"contentVersion")!=c.contentVersion))throw new IllegalStateException("conflict");
                long delta=(desired?1:0)-(previous?1:0),lv=c.targetVersion+(delta==0?0:1);
                if(delta!=0){
                    if(count==null)throw new IllegalStateException("schema");
                    tx.set(ref(p+"/likes/"+uid),map("schemaVersion",2L,"uid",uid,"postId",c.postId,"active",desired,"likeVersion",lv,"sourceContentVersion",feed==null?number(oldLike,"sourceContentVersion"):number(feed,"contentVersion"),"updatedAt",now,"lastOperationId",c.operationId));
                    Map<String,Object> next=new HashMap<>(count);next.put("likeCount",number(count,"likeCount")+delta);next.put("countVersion",number(count,"countVersion")+1);next.put("updatedAt",now);next.put("lastOperationId",c.operationId);tx.set(ref("communityCounts/"+c.postId),next);
                }
                result=map("targetVersion",lv);
            }else if(comment){
                String cp=p+"/comments/"+c.targetId;
                Map<String,Object> prev=tx.get(ref(cp)).getData();
                if(number(prev,"commentVersion")!=c.targetVersion)throw new IllegalStateException("conflict");
                boolean withdraw=c.command.equals("withdraw_comment");
                if(!withdraw && (feed==null||number(feed,"contentVersion")!=c.contentVersion))throw new IllegalStateException("conflict");
                if(prev!=null&&!uid.equals(prev.get("authorUid")))throw new IllegalStateException("denied");
                long version=c.targetVersion+1,source=withdraw?number(prev,"sourceContentVersion"):c.contentVersion;
                String body=withdraw?(String)prev.get("body"):(String)c.input.get("body");
                if(!text(body,500,2000))throw new IllegalStateException("invalid");
                Map<String,Object> next=map("schemaVersion",2L,"postId",c.postId,"authorUid",uid,"sourceContentVersion",source,"commentVersion",version,"body",body,"state",withdraw?"withdrawn":"visible","createdAt",prev==null?now:prev.get("createdAt"),"editedAt",prev==null?null:now,"updatedAt",now,"operationId",c.operationId);
                Map<String,Object> revision=map("schemaVersion",2L,"postId",c.postId,"commentId",c.targetId,"authorUid",uid,"commentVersion",version,"sourceContentVersion",source,"body",body,"state",next.get("state"),"createdAt",now,"operationId",c.operationId);
                tx.set(ref(cp),next);tx.set(ref(cp+"/revisions/v"+version),revision);
                if(withdraw)tx.delete(ref("communityCommentFeed/"+c.postId+"/comments/"+c.targetId));
                else tx.set(ref("communityCommentFeed/"+c.postId+"/comments/"+c.targetId),map("schemaVersion",2L,"postId",c.postId,"commentId",c.targetId,"authorUid",uid,"commentVersion",version,"sourceContentVersion",source,"body",body,"state","visible","createdAt",next.get("createdAt"),"editedAt",next.get("editedAt")));
                result=map("targetVersion",version);
            }else{
                if(number(old,"contentVersion")!=c.contentVersion || number(old,"controlVersion")!=c.controlVersion)throw new IllegalStateException("conflict");
                if(old!=null && (!uid.equals(old.get("authorUid"))||!Long.valueOf(2).equals(old.get("schemaVersion"))||!"active".equals(old.get("lifecycle"))))throw new IllegalStateException("denied");
                boolean material=c.command.equals("create_private")||c.command.equals("edit_private")||c.command.equals("edit_visible")||c.command.equals("publish")&&old==null;
                boolean visible=Arrays.asList("publish","edit_visible","show").contains(c.command);
                Map<String,Object> rev=old==null?null:tx.get(ref(p+"/revisions/"+old.get("currentRevisionId"))).getData();
                Map<String,Object> count=visible?tx.get(ref("communityCounts/"+c.postId)).getData():null;
                long cv=c.contentVersion+(material?1:0),ctl=c.controlVersion+1;
                Map<String,Object> next=old==null?map("schemaVersion",2L,"postId",c.postId,"authorUid",uid,"type","community","lifecycle","active","moderatorHold","clear","firstPublishedAt",null,"editedAt",null,"createdAt",now):new HashMap<>(old);
                next.put("contentVersion",cv);next.put("controlVersion",ctl);next.put("currentRevisionId","v"+cv);next.put("visibility",visible?"visible":"hidden");next.put("updatedAt",now);next.put("lastOperationId",c.operationId);
                if(c.command.equals("archive"))next.put("lifecycle","archived");
                if(material){if(!CommunitySchema.fields(map("body",c.input.get("body"),"bundledIllustrationKey",c.input.get("bundledIllustrationKey"))))throw new IllegalStateException("invalid");rev=map("schemaVersion",2L,"postId",c.postId,"authorUid",uid,"type","community","contentVersion",cv,"body",c.input.get("body"),"bundledIllustrationKey",c.input.get("bundledIllustrationKey"),"createdAt",now,"operationId",c.operationId);}
                if(material&&old!=null&&old.get("firstPublishedAt")!=null)next.put("editedAt",now);
                if(visible){if(!Boolean.TRUE.equals(c.input.get("consent")) && !c.command.equals("edit_visible"))throw new IllegalStateException("denied");if(next.get("firstPublishedAt")==null)next.put("firstPublishedAt",now);if(material&&old!=null&&old.get("firstPublishedAt")!=null)next.put("editedAt",now);}
                tx.set(ref(p),next);if(material)tx.set(ref(p+"/revisions/v"+cv),rev);
                if(visible){tx.set(ref("communityFeed/"+c.postId),map("schemaVersion",2L,"postId",c.postId,"authorUid",uid,"type","community","contentVersion",cv,"body",rev.get("body"),"bundledIllustrationKey",rev.get("bundledIllustrationKey"),"audience","community","state","visible","firstPublishedAt",next.get("firstPublishedAt"),"editedAt",next.get("editedAt")));if(count==null)tx.set(ref("communityCounts/"+c.postId),map("schemaVersion",2L,"postId",c.postId,"likeCount",0L,"countVersion",0L,"updatedAt",now,"lastOperationId",c.operationId));}
                else if(old!=null)tx.delete(ref("communityFeed/"+c.postId));
                result=map("contentVersion",cv,"controlVersion",ctl);
            }
            Map<String,Object> r=map("schemaVersion",2L,"ownerUid",uid,"operationId",c.operationId,"postId",c.postId,"targetId",c.targetId,"command",c.command,"base",c.base(),"result",result,"input",c.input,"status","committed","committedAt",now);
            if(!sessions.current(ticket))throw new IllegalStateException("session_changed");tx.set(receipt,r);return r;
        });
        return attempt.continueWithTask(t->{
            if(!sessions.current(ticket))return Tasks.forException(new IllegalStateException("session_changed"));
            if(t.isSuccessful())return Tasks.forResult(t.getResult());
            Exception failure=t.getException();
            return read("users/"+ticket.uid+"/communityOperations/"+c.operationId).continueWithTask(receipt->{
                if(!sessions.current(ticket))return Tasks.forException(new IllegalStateException("session_changed"));
                if(receipt.isSuccessful()&&receipt.getResult().exists()&&c.matches(receipt.getResult().getData()))return Tasks.forResult(receipt.getResult().getData());
                return Tasks.forException(failure==null?new IllegalStateException("unknown"):failure);
            });
        });
    }
    public static boolean text(String s,int max,int bytes){return s!=null&&s.equals(s.trim())&&!s.isBlank()&&s.codePointCount(0,s.length())<=max&&s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=bytes;}
}
