package com.gmail.manhle240406.project.pet;

import com.gmail.manhle240406.project.foundation.SessionEpoch;
import com.gmail.manhle240406.project.foundation.SessionGateway;
import com.google.android.gms.tasks.*;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.gmail.manhle240406.project.pet.PetSchema.*;

/** F06 client commands only; protected decisions are never performed by this app. */
public final class PetIdentityRepository {
    public enum Code { COMMITTED, SERVER, NOT_FOUND, CONFLICT, DENIED, UNKNOWN_SCHEMA, UNAVAILABLE, UNKNOWN_OUTCOME, SESSION_CHANGED }
    public static final class Result<T>{public final Code code;public final T value;Result(Code c,T v){code=c;value=v;}}
    public static final class Detail {
        public final Map<String,Object> pet,revision,claim,authority,slot,gate;
        Detail(Map<String,Object> p,Map<String,Object> r,Map<String,Object> c,Map<String,Object> a,Map<String,Object> s,Map<String,Object> g){pet=p;revision=r;claim=c;authority=a;slot=s;gate=g;}
    }
    public static final class Page {public final List<Map<String,Object>> rows;public final DocumentSnapshot cursor;Page(List<Map<String,Object>> r,DocumentSnapshot c){rows=Collections.unmodifiableList(r);cursor=c;}}
    private final FirebaseFirestore db;
    private final SessionGateway sessions;
    public PetIdentityRepository(FirebaseFirestore db,SessionGateway sessions){this.db=db;this.sessions=sessions;}
    private DocumentReference ref(String path){return db.document(path);}
    private Map<String,Object> data(DocumentSnapshot s){return s.exists()?Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(s.getData()))):null;}
    private void require(boolean condition,String reason){if(!condition)throw new IllegalStateException(reason);}
    private String petPath(String id){return "pets/"+id;}
    public Task<Result<Detail>> load(SessionEpoch.Ticket t,String id){
        if(!valid(t)||!PetSchema.id(id))return Tasks.forResult(new Result<>(Code.SESSION_CHANGED,null));
        Task<Detail> read=ref(petPath(id)).get(Source.SERVER).continueWithTask(first->{
            if(!first.isSuccessful())throw Objects.requireNonNull(first.getException());
            Map<String,Object> p=data(first.getResult());require(p!=null,"not_found");require(root(p,id),"unknown_schema");
            List<Task<DocumentSnapshot>> rest=Arrays.asList(ref(petPath(id)+"/identityRevisions/"+p.get("currentRevisionId")).get(Source.SERVER),ref(petPath(id)+"/claims/"+t.uid).get(Source.SERVER),ref("petOfferAuthorities/"+id).get(Source.SERVER),ref("petListingSlots/"+id).get(Source.SERVER),ref("petActivityGates/"+id).get(Source.SERVER));
            return Tasks.whenAllSuccess(rest).continueWith(done->{
                if(!done.isSuccessful())throw Objects.requireNonNull(done.getException());
                Map<String,Object> r=data(rest.get(0).getResult()),c=data(rest.get(1).getResult()),a=data(rest.get(2).getResult()),s=data(rest.get(3).getResult()),g=data(rest.get(4).getResult());
                require(revision(r,id,version(p,"identityVersion"))&&claim(c,id,t.uid)&&PetOfferAuthority.authority(a,id)&&PetOfferAuthority.slot(s,id)&&PetOfferAuthority.gate(g,id),"unknown_schema");
                return new Detail(p,r,c,a,s,g);
            });
        });return wrap(Tasks.withTimeout(read,15,TimeUnit.SECONDS),t,false);
    }
    public Task<Result<Page>> list(SessionEpoch.Ticket t,String kind,DocumentSnapshot cursor){
        if(!valid(t))return Tasks.forResult(new Result<>(Code.SESSION_CHANGED,null));
        Query q;
        if("pets".equals(kind))q=db.collection("pets").whereEqualTo("editorUid",t.uid).orderBy("updatedAt",Query.Direction.DESCENDING);
        else if("receipts".equals(kind)||"challenges".equals(kind))q=db.collection("users/"+t.uid+("receipts".equals(kind)?"/petReceipts":"/petChallenges")).orderBy("receipts".equals(kind)?"recordedAt":"submittedAt",Query.Direction.DESCENDING);
        else return Tasks.forResult(new Result<>(Code.DENIED,null));
        q=q.orderBy(FieldPath.documentId(),Query.Direction.DESCENDING).limit(20);if(cursor!=null)q=q.startAfter(cursor);
        Task<Page> read=q.get(Source.SERVER).continueWith(task->{
            if(!task.isSuccessful())throw Objects.requireNonNull(task.getException());
            List<Map<String,Object>> rows=new ArrayList<>();for(DocumentSnapshot d:task.getResult()){
                Map<String,Object> row=data(d);require("pets".equals(kind)?root(row,d.getId())&&t.uid.equals(row.get("editorUid")):"receipts".equals(kind)?receipt(row,t.uid,d.getId()):challenge(row,t.uid,d.getId()),"unknown_schema");rows.add(row);
            }
            List<DocumentSnapshot> docs=task.getResult().getDocuments();return new Page(rows,docs.size()==20?docs.get(19):null);
        });return wrap(Tasks.withTimeout(read,15,TimeUnit.SECONDS),t,false);
    }
    public Task<Result<Map<String,Object>>> reconcile(SessionEpoch.Ticket t,String operation){
        if(!valid(t)||!PetSchema.id(operation))return Tasks.forResult(new Result<>(Code.SESSION_CHANGED,null));
        Task<Map<String,Object>> read=ref("users/"+t.uid+"/petReceipts/"+operation).get(Source.SERVER).continueWith(task->{
            if(!task.isSuccessful())throw Objects.requireNonNull(task.getException());Map<String,Object> r=data(task.getResult());require(r!=null,"not_found");require(receipt(r,t.uid,operation),"unknown_schema");return r;
        });return wrap(Tasks.withTimeout(read,15,TimeUnit.SECONDS),t,false);
    }
    private boolean valid(SessionEpoch.Ticket t){return t!=null&&t.uid!=null&&sessions.current(t);}
    public Task<Result<Map<String,Object>>> execute(SessionEpoch.Ticket t,PetCommand c){
        if(!valid(t))return Tasks.forResult(new Result<>(Code.SESSION_CHANGED,null));

        Task<Map<String,Object>> operation=db.runTransaction(new TransactionOptions.Builder().setMaxAttempts(3).build(),tx->{
            require(valid(t),"session_changed");String uid=t.uid,base=petPath(c.petId);
            DocumentReference receiptRef=ref("users/"+uid+"/petReceipts/"+c.operationId);Map<String,Object> previous=data(tx.get(receiptRef));
            if(previous!=null){require(receipt(previous,uid,c.operationId),"unknown_schema");require(c.matchesReceipt(previous),"conflict");return previous;}
            if(!Arrays.asList("submit_challenge","withdraw_claim","suspend_offering").contains(c.type)){
                Map<String,Object> shell=data(tx.get(ref("users/"+uid))),subject=data(tx.get(ref("securitySubjects/"+uid)));
                require(keys(shell,"uid","schemaVersion","createdAt")&&uid.equals(shell.get("uid"))&&Long.valueOf(1).equals(shell.get("schemaVersion"))&&shell.get("createdAt") instanceof Timestamp&&subject!=null&&"active".equals(subject.get("status")),"denied");
            }
            Map<String,Object> p=Arrays.asList("submit_challenge","suspend_offering").contains(c.type)?null:data(tx.get(ref(base)));
            Map<String,Map<String,Object>> writes=new LinkedHashMap<>();Object now=FieldValue.serverTimestamp();long resultIdentity=c.identityVersion,resultClaim=c.claimVersion,resultAuthority=c.authorityVersion;String role;
            if("register_pet".equals(c.type)){
                require(p==null,"conflict");require(keys(c.input,"identity","claim")&&identity(nested(c.input,"identity"))&&declaration(nested(c.input,"claim")),"unknown_schema");
                Map<String,Object> identity=nested(c.input,"identity"),declaration=nested(c.input,"claim");
                writes.put(base,map("schemaVersion",1L,"petId",c.petId,"registrarUid",uid,"editorUid",uid,"identityVersion",1L,"currentRevisionId",c.operationId,"custodyVersion",0L,"lifecycle","active","createdAt",now,"updatedAt",now,"lastOperationId",c.operationId));
                writes.put(base+"/identityRevisions/"+c.operationId,revisionRecord(uid,c.petId,1,c.operationId,identity,now));
                Map<String,Object> claim=map("schemaVersion",1L,"petId",c.petId,"claimantUid",uid,"claimVersion",1L,"identityVersion",1L,"reviewState","draft","submittedRevisionId",null,"submittedAt",null,"createdAt",now,"updatedAt",now,"lastOperationId",c.operationId);claim.putAll(declaration);writes.put(base+"/claims/"+uid,claim);
                writes.put("petOfferAuthorities/"+c.petId,map("schemaVersion",1L,"petId",c.petId,"offerUid",null,"claimUid",uid,"claimVersion",1L,"identityVersion",1L,"authorityVersion",0L,"status","blocked","hold","clear","decisionId",null,"custodyVersion",0L,"updatedAt",now,"lastOperationId",c.operationId));
                writes.put("petListingSlots/"+c.petId,map("schemaVersion",1L,"petId",c.petId,"activeListingId",null,"slotVersion",0L,"updatedAt",now));
                writes.put("petActivityGates/"+c.petId,map("schemaVersion",1L,"petId",c.petId,"state","none","activeReservationId",null,"activePlacementId",null,"gateVersion",0L,"custodyVersion",0L,"updatedAt",now));
                role="registrar";resultIdentity=1;resultClaim=1;resultAuthority=0;
            }else if("submit_challenge".equals(c.type)){
                require(keys(c.input,"statement")&&text(c.input.get("statement"),1,1000,3000),"unknown_schema");role="reporter";resultIdentity=0;resultClaim=0;resultAuthority=0;
                writes.put("users/"+uid+"/petChallenges/"+c.operationId,map("schemaVersion",1L,"ownerUid",uid,"operationId",c.operationId,"targetPetId",c.petId,"statement",c.input.get("statement"),"challengeVersion",1L,"status","received","response","","submittedAt",now));
            }else if("suspend_offering".equals(c.type)){
                Map<String,Object> authority=data(tx.get(ref("petOfferAuthorities/"+c.petId)));require(PetOfferAuthority.authority(authority,c.petId),"unknown_schema");
                require(uid.equals(authority.get("offerUid"))&&"enabled".equals(authority.get("status")),"denied");require(c.authorityVersion==version(authority,"authorityVersion")&&c.identityVersion==version(authority,"identityVersion")&&c.claimVersion==version(authority,"claimVersion")&&c.input.isEmpty(),"conflict");
                Map<String,Object> next=new HashMap<>(authority);next.putAll(map("status","suspended","authorityVersion",c.authorityVersion+1,"updatedAt",now,"lastOperationId",c.operationId));writes.put("petOfferAuthorities/"+c.petId,next);role="offer_actor";resultAuthority++;
            }else{
                require(root(p,c.petId),"unknown_schema");require(uid.equals(p.get("editorUid")),"denied");
                Map<String,Object> claim=data(tx.get(ref(base+"/claims/"+uid))),authority=data(tx.get(ref("petOfferAuthorities/"+c.petId)));
                require(PetSchema.claim(claim,c.petId,uid)&&PetOfferAuthority.authority(authority,c.petId),"unknown_schema");
                require(c.authorityVersion==version(authority,"authorityVersion"),"conflict");
                if("suspend_offering".equals(c.type)){
                    require(uid.equals(authority.get("offerUid"))&&"enabled".equals(authority.get("status")),"denied");
                    require(c.identityVersion==version(authority,"identityVersion")&&c.claimVersion==version(authority,"claimVersion")&&c.input.isEmpty(),"conflict");
                    Map<String,Object> next=new HashMap<>(authority);next.putAll(map("status","suspended","authorityVersion",c.authorityVersion+1,"updatedAt",now,"lastOperationId",c.operationId));writes.put("petOfferAuthorities/"+c.petId,next);role="offer_actor";resultAuthority++;
                }else{
                    require(c.identityVersion==version(p,"identityVersion")&&c.claimVersion==version(claim,"claimVersion"),"conflict");
                    if("revise_identity".equals(c.type)){
                        require("active".equals(p.get("lifecycle"))&&keys(c.input,"identity")&&identity(nested(c.input,"identity")),"denied");
                        Map<String,Object> next=new HashMap<>(p);next.putAll(map("identityVersion",c.identityVersion+1,"currentRevisionId",c.operationId,"updatedAt",now,"lastOperationId",c.operationId));writes.put(base,next);
                        writes.put(base+"/identityRevisions/"+c.operationId,revisionRecord(uid,c.petId,c.identityVersion+1,c.operationId,nested(c.input,"identity"),now));role="editor";resultIdentity++;
                    }else{
                        require(Arrays.asList("submit_claim","revise_claim","withdraw_claim").contains(c.type)&&keys(c.input,"claim")&&declaration(nested(c.input,"claim")),"denied");
                        Map<String,Object> declared=nested(c.input,"claim"),next=new HashMap<>(claim);next.putAll(declared);next.putAll(map("claimVersion",c.claimVersion+1,"identityVersion",c.identityVersion,"updatedAt",now,"lastOperationId",c.operationId,"submittedRevisionId",null,"submittedAt",null,"reviewState","withdraw_claim".equals(c.type)?"withdrawn":"draft"));
                        if("submit_claim".equals(c.type)){
                            require("draft".equals(claim.get("reviewState"))&&declared.equals(declarationOf(claim)),"conflict");next.putAll(map("reviewState","submitted","submittedAt",now,"submittedRevisionId",c.operationId));
                            writes.put(base+"/claims/"+uid+"/revisions/"+c.operationId,map("schemaVersion",1L,"petId",c.petId,"claimantUid",uid,"claimVersion",c.claimVersion+1,"identityVersion",c.identityVersion,"declaration",declared,"operationId",c.operationId,"createdAt",now));
                        }else if(uid.equals(authority.get("claimUid"))&&"enabled".equals(authority.get("status"))){Map<String,Object> nextAuthority=new HashMap<>(authority);nextAuthority.putAll(map("status","suspended","authorityVersion",c.authorityVersion+1,"updatedAt",now,"lastOperationId",c.operationId));writes.put("petOfferAuthorities/"+c.petId,nextAuthority);resultAuthority++;}
                        writes.put(base+"/claims/"+uid,next);role="claimant";resultClaim++;
                    }
                }
            }
            Map<String,Object> receipt=map("schemaVersion",1L,"ownerUid",uid,"operationId",c.operationId,"petId",c.petId,"type",c.type,"role",role,"baseIdentityVersion",c.identityVersion,"baseClaimVersion",c.claimVersion,"baseAuthorityVersion",c.authorityVersion,"result",map("identityVersion",resultIdentity,"claimVersion",resultClaim,"authorityVersion",resultAuthority),"input",c.input,"status","committed","recordedAt",now);
            require(valid(t),"session_changed");for(Map.Entry<String,Map<String,Object>> e:writes.entrySet())tx.set(ref(e.getKey()),e.getValue());tx.set(receiptRef,receipt);return receipt;
        });return wrap(operation,t,true).onSuccessTask(result->{
            if(result.code!=Code.DENIED&&result.code!=Code.UNKNOWN_OUTCOME)return Tasks.forResult(result);
            return reconcile(t,c.operationId).continueWith(task->{Result<Map<String,Object>> observed=task.getResult();if(observed.code==Code.SERVER)return new Result<>(c.matchesReceipt(observed.value)?Code.COMMITTED:Code.CONFLICT,c.matchesReceipt(observed.value)?observed.value:null);return result;});
        });
    }
    public Task<Result<Map<String,Object>>> ownAuthority(SessionEpoch.Ticket t,String pet){if(!valid(t)||!PetSchema.id(pet))return Tasks.forResult(new Result<>(Code.DENIED,null));Task<Map<String,Object>> task=ref("petOfferAuthorities/"+pet).get(Source.SERVER).continueWith(read->{if(!read.isSuccessful())throw Objects.requireNonNull(read.getException());Map<String,Object> a=data(read.getResult());require(PetOfferAuthority.authority(a,pet),"unknown_schema");require(t.uid.equals(a.get("offerUid")),"denied");return a;});return wrap(Tasks.withTimeout(task,15,TimeUnit.SECONDS),t,false);}

    private Map<String,Object> revisionRecord(String uid,String pet,long v,String op,Map<String,Object> identity,Object now){return map("schemaVersion",1L,"petId",pet,"version",v,"actorUid",uid,"identity",identity,"operationId",op,"createdAt",now);}
    public static boolean receipt(Map<String,Object> r,String uid,String id){
        if(!keys(r,"schemaVersion","ownerUid","operationId","petId","type","role","baseIdentityVersion","baseClaimVersion","baseAuthorityVersion","result","input","status","recordedAt")||!Long.valueOf(1).equals(r.get("schemaVersion"))||!uid.equals(r.get("ownerUid"))||!id.equals(r.get("operationId"))||!PetSchema.id(id)||!PetSchema.id(r.get("petId"))||!number(r.get("baseIdentityVersion"),0)||!number(r.get("baseClaimVersion"),0)||!number(r.get("baseAuthorityVersion"),0)||!"committed".equals(r.get("status"))||!(r.get("recordedAt") instanceof Timestamp)||!(r.get("result") instanceof Map)||!(r.get("input") instanceof Map))return false;
        Map<String,Object> result=nested(r,"result"),input=nested(r,"input");if(!keys(result,"identityVersion","claimVersion","authorityVersion")||!number(result.get("identityVersion"),0)||!number(result.get("claimVersion"),0)||!number(result.get("authorityVersion"),0))return false;
        if(!(r.get("type") instanceof String)||!(r.get("role") instanceof String))return false;
        switch((String)r.get("type")){
            case "register_pet":return "registrar".equals(r.get("role"))&&keys(input,"identity","claim")&&input.get("identity") instanceof Map&&input.get("claim") instanceof Map&&identity(nested(input,"identity"))&&declaration(nested(input,"claim"));
            case "revise_identity":return "editor".equals(r.get("role"))&&keys(input,"identity")&&input.get("identity") instanceof Map&&identity(nested(input,"identity"));
            case "submit_claim":case "revise_claim":case "withdraw_claim":return "claimant".equals(r.get("role"))&&keys(input,"claim")&&input.get("claim") instanceof Map&&declaration(nested(input,"claim"));
            case "suspend_offering":return "offer_actor".equals(r.get("role"))&&input.isEmpty();
            case "submit_challenge":return "reporter".equals(r.get("role"))&&keys(input,"statement")&&text(input.get("statement"),1,1000,3000);
            case "review_claim":case "apply_hold":case "archive_pet":case "duplicate_relation":case "respond_challenge":return ("respond_challenge".equals(r.get("type"))?"reporter":"claimant").equals(r.get("role"))&&keys(input,"decision","reason","decisionId")&&r.get("type").equals(input.get("decision"))&&PetSchema.id(input.get("decisionId"))&&text(input.get("reason"),1,500,2000);
            default:return false;
        }
    }
    public static boolean challenge(Map<String,Object> c,String uid,String id){return keys(c,"schemaVersion","ownerUid","operationId","targetPetId","statement","challengeVersion","status","response","submittedAt")&&Long.valueOf(1).equals(c.get("schemaVersion"))&&uid.equals(c.get("ownerUid"))&&id.equals(c.get("operationId"))&&PetSchema.id(id)&&PetSchema.id(c.get("targetPetId"))&&text(c.get("statement"),1,1000,3000)&&number(c.get("challengeVersion"),1)&&Arrays.asList("received","triaged","closed").contains(c.get("status"))&&text(c.get("response"),0,1000,3000)&&c.get("submittedAt") instanceof Timestamp;}
    private <T> Task<Result<T>> wrap(Task<T> task,SessionEpoch.Ticket t,boolean write){return task.continueWith(done->{
        if(!valid(t))return new Result<>(Code.SESSION_CHANGED,null);if(done.isSuccessful())return new Result<>(write?Code.COMMITTED:Code.SERVER,done.getResult());
        Throwable error=done.getException();while(error!=null){String m=error.getMessage();if("conflict".equals(m))return new Result<>(Code.CONFLICT,null);if("denied".equals(m))return new Result<>(Code.DENIED,null);if("not_found".equals(m))return new Result<>(Code.NOT_FOUND,null);if("unknown_schema".equals(m))return new Result<>(Code.UNKNOWN_SCHEMA,null);if(error instanceof FirebaseFirestoreException){FirebaseFirestoreException.Code code=((FirebaseFirestoreException)error).getCode();if(code==FirebaseFirestoreException.Code.PERMISSION_DENIED||code==FirebaseFirestoreException.Code.UNAUTHENTICATED)return new Result<>(Code.DENIED,null);if(code==FirebaseFirestoreException.Code.ABORTED)return new Result<>(Code.CONFLICT,null);}error=error.getCause();}
        return new Result<>(write?Code.UNKNOWN_OUTCOME:Code.UNAVAILABLE,null);
    });}
}
