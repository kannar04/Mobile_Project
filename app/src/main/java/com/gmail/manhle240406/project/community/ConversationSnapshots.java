package com.gmail.manhle240406.project.community;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;

/** One bounded snapshot mechanism for direct and selected-adoption conversations. */
final class ConversationSnapshots {
 interface Callback { void accept(QuerySnapshot rows,Exception error); }
 interface SignalCallback { void accept(DocumentSnapshot row,Exception error); }
 private ConversationSnapshots(){}
 static ListenerRegistration direct(String id,Callback callback){return FirebaseFirestore.getInstance().collection("directConversations/"+id+"/messages").orderBy("sequence",Query.Direction.DESCENDING).limit(20).addSnapshotListener(MetadataChanges.INCLUDE,(rows,error)->callback.accept(rows,error));}
 static ListenerRegistration adoption(String id,Callback callback){return FirebaseFirestore.getInstance().collection("adoptionFlowConversations/"+id+"/messages").orderBy("createdAt",Query.Direction.DESCENDING).orderBy(FieldPath.documentId(),Query.Direction.DESCENDING).limit(20).addSnapshotListener(MetadataChanges.INCLUDE,(rows,error)->callback.accept(rows,error));}
 static ListenerRegistration signal(String key,SignalCallback callback){String uid=FirebaseAuth.getInstance().getUid();if(uid==null)throw new IllegalStateException("signed_out");return FirebaseFirestore.getInstance().document("users/"+uid+"/conversationIndex/rt_"+key).addSnapshotListener(MetadataChanges.INCLUDE,(row,error)->callback.accept(row,error));}
 static java.util.List<java.util.Map<String,Object>> maps(QuerySnapshot rows){java.util.ArrayList<java.util.Map<String,Object>> result=new java.util.ArrayList<>();if(rows!=null)for(DocumentSnapshot row:rows)result.add(new java.util.HashMap<>(row.getData()));java.util.Collections.reverse(result);return result;}
}
