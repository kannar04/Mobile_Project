package com.gmail.manhle240406.project.community;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Private references only; canonical reaction and source resolved separately. */
public final class LikedPostsRepository {
 private final FirebaseFirestore db;private final SessionGateway sessions;private final SessionEpoch.Ticket ticket;
 public LikedPostsRepository(FirebaseFirestore db,SessionGateway s,SessionEpoch.Ticket t){this.db=db;sessions=s;ticket=t;}
 private <T> Task<T> scoped(Task<T> task){return Tasks.withTimeout(task,15,TimeUnit.SECONDS).continueWith(done->{if(!sessions.current(ticket))throw new IllegalStateException("session_changed");return done.getResult();});}
 public Task<QuerySnapshot> page(DocumentSnapshot cursor){Query q=db.collection("users/"+ticket.uid+"/likedPosts").orderBy("likedAt",Query.Direction.DESCENDING).orderBy(FieldPath.documentId(),Query.Direction.DESCENDING).limit(20);if(cursor!=null)q=q.startAfter(cursor);return scoped(q.get(Source.SERVER)).continueWith(done->{QuerySnapshot result=done.getResult();for(DocumentSnapshot row:result){Map<String,Object> v=row.getData();if(!v.keySet().equals(new HashSet<>(Arrays.asList("schemaVersion","uid","postId","likeVersion","sourceContentVersion","likedAt","lastOperationId")))||!Long.valueOf(1).equals(v.get("schemaVersion"))||!ticket.uid.equals(v.get("uid"))||!row.getId().equals(v.get("postId"))||!com.gmail.manhle240406.project.pet.PetSchema.id(row.getId())||SocialRepository.number(v,"likeVersion")<1||SocialRepository.number(v,"sourceContentVersion")<1||!(v.get("likedAt") instanceof com.google.firebase.Timestamp)||!com.gmail.manhle240406.project.pet.PetSchema.id(v.get("lastOperationId")))throw new IllegalStateException("liked_index_schema");}return result;});}
 public Task<Map<String,Object>> resolve(String post){Task<DocumentSnapshot> like=db.document("communityPosts/"+post+"/likes/"+ticket.uid).get(Source.SERVER),source=db.document("communityFeed/"+post).get(Source.SERVER);return scoped(Tasks.whenAllComplete(like,source)).continueWith(t->{t.getResult();Map<String,Object> reaction=like.isSuccessful()?like.getResult().getData():null,feed=source.isSuccessful()?source.getResult().getData():null;return SocialRepository.map("like",reaction,"source",reaction!=null&&Boolean.TRUE.equals(reaction.get("active"))&&SocialSchema.feed(feed,post)?feed:null);});}
}
