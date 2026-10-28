import {initializeTestEnvironment,assertSucceeds,assertFails} from '@firebase/rules-unit-testing';
import {readFileSync} from 'node:fs';
import {test,after,before} from 'node:test';
import {randomUUID} from 'node:crypto';
import assert from 'node:assert/strict';
import {doc,setDoc,getDoc,getDocs,collection,query,where,orderBy,documentId,limit,writeBatch,serverTimestamp,Timestamp,deleteDoc} from 'firebase/firestore';
const projectId='demo-anilove-account-tests'; let env,A,B,guest;
const now=Timestamp.fromMillis(1_790_000_000_000);
const id2=randomUUID(),id3=randomUUID();
const projection=(id,schema=3)=>({schemaVersion:schema,postId:id,authorUid:'A',type:'community',contentVersion:1,
 ...(schema===3?{controlVersion:1}:{}),body:'Fictional pet story',bundledIllustrationKey:'cat',audience:'community',state:'visible',firstPublishedAt:now,editedAt:null});
const seed=async(path,data)=>env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),path),data));
before(async()=>{
 env=await initializeTestEnvironment({projectId,firestore:{host:'127.0.0.1',port:Number(process.env.ANILOVE_ACCOUNT_EMULATOR_PORT??8280),rules:readFileSync(new URL(process.env.ANILOVE_RULES_FILE??'../firestore.rules',import.meta.url),'utf8')}});
 await env.clearFirestore(); // This dedicated demo project contains only this suite's synthetic fixtures.
 A=env.authenticatedContext('A',{email_verified:true}).firestore(); B=env.authenticatedContext('B',{email_verified:true}).firestore();guest=env.unauthenticatedContext().firestore();
 await seed('users/A',{uid:'A',schemaVersion:1,createdAt:now});
 await seed('communityMemberships/A',{uid:'A',schemaVersion:1,status:'active',membershipVersion:1,createdAt:now,updatedAt:now});
 await seed('system/communityPolicy',{schemaVersion:1,policyVersion:1,enabled:true,publicationMode:'post_moderated',updatedAt:now});
 await seed('system/reportPolicy',{schemaVersion:1,policyVersion:1,intakeEnabled:true,supportedTargetKinds:['post','comment'],updatedAt:now});
 await seed(`communityFeed/${id2}`,projection(id2,2)); await seed(`communityFeed/${id3}`,projection(id3));
});
after(async()=>{await env?.cleanup();});
test('signed-in mixed schema query supports chronological bounded pages',async()=>{
 const q=query(collection(B,'communityFeed'),where('schemaVersion','in',[2,3]),where('audience','==','community'),where('state','==','visible'),orderBy('firstPublishedAt','desc'),orderBy(documentId(),'desc'),limit(20));
 const rows=await assertSucceeds(getDocs(q));assert.ok(rows.size>=2);
 await assertFails(getDocs(query(collection(B,'communityFeed'),limit(21))));
});
test('guest and malformed extra private projection field denied',async()=>{
 await assertFails(getDoc(doc(guest,`communityFeed/${id3}`)));
 const bad=randomUUID(); await seed(`communityFeed/${bad}`,{...projection(bad),privateEvidence:'never public'});
 await assertFails(getDoc(doc(B,`communityFeed/${bad}`)));
});
async function publish({mirror=1,schema=3,held=false}={}) {
 const id=randomUUID(),op=randomUUID(),time=serverTimestamp(),b=writeBatch(A);
 b.set(doc(A,`communityPosts/${id}`),{schemaVersion:2,postId:id,authorUid:'A',type:'community',lifecycle:'active',contentVersion:1,currentRevisionId:'v1',controlVersion:1,visibility:'visible',moderatorHold:held?'held':'clear',firstPublishedAt:time,editedAt:null,createdAt:time,updatedAt:time,lastOperationId:op});
 b.set(doc(A,`communityPosts/${id}/revisions/v1`),{schemaVersion:2,postId:id,authorUid:'A',type:'community',contentVersion:1,body:'Fictional pet story',bundledIllustrationKey:'cat',createdAt:time,operationId:op});
 const feed={...projection(id,schema),firstPublishedAt:time};if(schema===3)feed.controlVersion=mirror;
 b.set(doc(A,`communityFeed/${id}`),feed);
 b.set(doc(A,`communityCounts/${id}`),{schemaVersion:2,postId:id,likeCount:0,countVersion:0,updatedAt:time,lastOperationId:op});
 b.set(doc(A,`users/A/communityOperations/${op}`),{schemaVersion:2,ownerUid:'A',operationId:op,postId:id,targetId:null,command:'publish',base:{contentVersion:0,controlVersion:0,targetVersion:0},result:{contentVersion:1,controlVersion:1},input:{body:'Fictional pet story',bundledIllustrationKey:'cat',consent:true},status:'committed',committedAt:time});
 return {commit:b.commit(),id};
}
test('schema3 owner publication atomically mirrors root control and preserves root schema2',async()=>{
 const {commit,id}=await publish();await assertSucceeds(commit);
 const feed=(await getDoc(doc(B,`communityFeed/${id}`))).data();assert.equal(feed.controlVersion,1);assert.equal(feed.schemaVersion,3);
 assert.equal((await getDoc(doc(A,`communityPosts/${id}`))).data().schemaVersion,2);
 await assertFails(getDoc(doc(B,`communityPosts/${id}`)));
});
test('forged control and legacy new publication cannot bypass schema3 mirror',async()=>{
 await assertFails((await publish({mirror:99})).commit);
 await assertFails((await publish({schema:2})).commit);
 await assertFails((await publish({held:true})).commit);
});
test('closed rollout policy still denies publication',async()=>{
 await seed('system/communityPolicy',{schemaVersion:1,policyVersion:2,enabled:false,publicationMode:'post_moderated',updatedAt:now});
 await assertFails((await publish()).commit);
 await seed('system/communityPolicy',{schemaVersion:1,policyVersion:3,enabled:true,publicationMode:'post_moderated',updatedAt:now});
});
test('edit schema3 preserves chronology, counts and conversation; hide removes access',async()=>{
 const {commit,id}=await publish();await commit;
 const root=(await getDoc(doc(A,`communityPosts/${id}`))).data();
 const countPath=`communityCounts/${id}`,comment=randomUUID();
 await seed(countPath,{schemaVersion:2,postId:id,likeCount:3,countVersion:3,updatedAt:now,lastOperationId:randomUUID()});
 await seed(`communityCommentFeed/${id}/comments/${comment}`,{schemaVersion:2,state:'visible',body:'Existing conversation'});
 const op=randomUUID(),time=serverTimestamp(),b=writeBatch(A);
 const next={...root,contentVersion:2,controlVersion:2,currentRevisionId:'v2',editedAt:time,updatedAt:time,lastOperationId:op};
 b.set(doc(A,`communityPosts/${id}`),next);
 b.set(doc(A,`communityPosts/${id}/revisions/v2`),{schemaVersion:2,postId:id,authorUid:'A',type:'community',contentVersion:2,body:'Edited pet story',bundledIllustrationKey:'cat',createdAt:time,operationId:op});
 b.set(doc(A,`communityFeed/${id}`),{...projection(id),contentVersion:2,controlVersion:2,body:'Edited pet story',firstPublishedAt:root.firstPublishedAt,editedAt:time});
 b.set(doc(A,`users/A/communityOperations/${op}`),{schemaVersion:2,ownerUid:'A',operationId:op,postId:id,targetId:null,command:'edit_visible',base:{contentVersion:1,controlVersion:1,targetVersion:0},result:{contentVersion:2,controlVersion:2},input:{body:'Edited pet story',bundledIllustrationKey:'cat'},status:'committed',committedAt:time});
 await assertSucceeds(b.commit());
 const updated=(await getDoc(doc(B,`communityFeed/${id}`))).data();
 assert.ok(updated.firstPublishedAt.isEqual(root.firstPublishedAt));assert.equal(updated.controlVersion,2);
 assert.equal((await getDoc(doc(B,countPath))).data().likeCount,3);
 assert.equal((await getDoc(doc(B,`communityCommentFeed/${id}/comments/${comment}`))).data().body,'Existing conversation');
 await assertFails(setDoc(doc(A,`communityFeed/${id}`),{...updated,controlVersion:99}));
 const current=(await getDoc(doc(A,`communityPosts/${id}`))).data(),hide=randomUUID(),t=serverTimestamp(),h=writeBatch(A);
 h.set(doc(A,`communityPosts/${id}`),{...current,visibility:'hidden',controlVersion:3,lastOperationId:hide,updatedAt:t});
 h.delete(doc(A,`communityFeed/${id}`));
 h.set(doc(A,`users/A/communityOperations/${hide}`),{schemaVersion:2,ownerUid:'A',operationId:hide,postId:id,targetId:null,command:'hide',base:{contentVersion:2,controlVersion:2,targetVersion:0},result:{contentVersion:2,controlVersion:3},input:{},status:'committed',committedAt:t});
 await assertSucceeds(h.commit());
 assert.equal((await getDoc(doc(B,`communityFeed/${id}`))).exists(),false);
 await assertFails(getDoc(doc(B,`communityCommentFeed/${id}/comments/${comment}`)));
});
test('schema3 parent still gates comments after removal',async()=>{
 const comment=randomUUID();await seed(`communityCommentFeed/${id3}/comments/${comment}`,{schemaVersion:2,state:'visible'});
 await assertSucceeds(getDoc(doc(B,`communityCommentFeed/${id3}/comments/${comment}`)));
 await env.withSecurityRulesDisabled(c=>deleteDoc(doc(c.firestore(),`communityFeed/${id3}`)));
 await assertFails(getDoc(doc(B,`communityCommentFeed/${id3}/comments/${comment}`)));
});
test('closure view is own exact minimized get, including restricted account',async()=>{
 const path='users/A/closureViews/request_one';
 await seed(path,{schemaVersion:1,ownerUid:'A',requestId:'request_one',phase:'received_needs_inventory',version:1,policyVersion:1,manifestVersion:1,acceptedAt:now,updatedAt:now});
 await seed('securitySubjects/A',{status:'restricted',roles:[],updatedAt:now});
 await assertSucceeds(getDoc(doc(A,path)));await assertFails(getDoc(doc(B,path)));await assertFails(getDoc(doc(guest,path)));
 await assertFails(getDocs(query(collection(A,'users/A/closureViews'),limit(10))));
 await assertFails(setDoc(doc(A,path),{phase:'completed_scope'}));
});
test('no client lifecycle/job/retention/provider privilege or adoption role grant',async()=>{
 for(const path of ['accountLifecycle/A','accountDeletionRequests/request_one','deletionWork/request_one/steps/feed','retentionHolds/hold_one','retiredPrincipals/A']) {
  await assertFails(setDoc(doc(A,path),{uid:'A',state:'closed'}));await assertFails(getDoc(doc(A,path)));
 }
 await assertFails(setDoc(doc(A,'securitySubjects/A'),{status:'active',roles:['admin','member'],updatedAt:now}));
});
