import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {initializeTestEnvironment,assertSucceeds,assertFails} from '@firebase/rules-unit-testing';
import {doc,setDoc,getDoc,getDocs,collection,query,limit,orderBy,documentId,updateDoc,serverTimestamp,Timestamp} from 'firebase/firestore';
import {intent,social} from './daily-protocol.mjs';
let env;
const db=(uid='alice',verified=true)=>uid?env.authenticatedContext(uid,{email_verified:verified}).firestore():env.unauthenticatedContext().firestore();
const member=uid=>({uid,schemaVersion:1,status:'active',membershipVersion:1,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
const seed=(path,data)=>env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),path),data));
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-anilove-onboarding',firestore:{host:'127.0.0.1',port:8180,rules:await readFile(process.env.UX_RULES_FILE??'../firestore.rules','utf8')}});});
after(async()=>env?.cleanup());
beforeEach(async()=>{await env.clearFirestore();for(const uid of ['alice','bob','mallory'])await seed(`users/${uid}`,{uid,schemaVersion:1,createdAt:Timestamp.now()});
 await seed('system/communityPolicy',{schemaVersion:1,policyVersion:1,enabled:true,publicationMode:'post_moderated',updatedAt:Timestamp.now()});
 await seed('system/reportPolicy',{schemaVersion:1,policyVersion:1,intakeEnabled:true,supportedTargetKinds:['post','comment'],updatedAt:Timestamp.now()});
});
async function post(){await setDoc(doc(db(),`communityMemberships/alice`),member('alice'));const c=intent('publish',undefined,0,0,{body:'Fictional pet story',bundledIllustrationKey:null,consent:true});await social(db(),'alice',c);return c.postId;}
test('unverified account enrolls ordinary membership; root schema/roles/protected authority unchanged',async()=>{
 const d=db('bob',false);await assertSucceeds(setDoc(doc(d,'communityMemberships/bob'),member('bob')));
 await assertFails(updateDoc(doc(d,'communityMemberships/bob'),{roles:['admin']}));
 await assertFails(updateDoc(doc(d,'users/bob'),{displayName:'Publicly leaked'}));
 await assertFails(setDoc(doc(d,'securitySubjects/bob'),{status:'active',roles:['admin'],updatedAt:serverTimestamp()}));
 await assertFails(setDoc(doc(d,`pets/${randomUUID()}`),{}));await assertFails(setDoc(doc(d,`listings/${randomUUID()}`),{}));
});
test('unverified own like and comment add/edit commit; exact retry gives one count',async()=>{
 const id=await post(),d=db('bob',false);await setDoc(doc(d,'communityMemberships/bob'),member('bob'));
 const like=intent('set_like',id,1,0,{active:true},null,0);await assertSucceeds(social(d,'bob',like));await assertSucceeds(social(d,'bob',like));
 assert.equal((await getDoc(doc(d,`communityCounts/${id}`))).data().likeCount,1);
 const cid=randomUUID();await assertSucceeds(social(d,'bob',intent('add_comment',id,1,0,{body:'A kind comment'},cid,0)));
 await assertSucceeds(social(d,'bob',intent('edit_comment',id,1,0,{body:'Edited kind comment'},cid,1)));
 assert.equal((await getDoc(doc(d,`communityCommentFeed/${id}/comments/${cid}`))).data().body,'Edited kind comment');
});
test('unverified cannot create/publish/show/edit posts or bypass profile publication',async()=>{
 const d=db('bob',false);await setDoc(doc(d,'communityMemberships/bob'),member('bob'));
 for(const command of ['create_private','publish'])await assertFails(social(d,'bob',intent(command,undefined,0,0,{body:'Not allowed',bundledIllustrationKey:null,...(command==='publish'?{consent:true}:{})})));
 const id=await post();await assertFails(social(db('alice',false),'alice',intent('edit_visible',id,1,1,{body:'Unverified edit',bundledIllustrationKey:null})));
 await assertSucceeds(social(db(),'alice',intent('hide',id,1,1)));
 await assertFails(social(db('alice',false),'alice',intent('show',id,1,2,{consent:true})));
 await assertFails(setDoc(doc(d,'publicProfiles/bob'),{displayName:'No implicit public consent'}));
});
test('guest, stale parent, restricted member and forged other-account interaction denied',async()=>{
 const id=await post(),d=db('bob',false);await setDoc(doc(d,'communityMemberships/bob'),member('bob'));
 await assertFails(social(db(null),'bob',intent('set_like',id,1,0,{active:true})));
 await assertFails(social(d,'alice',intent('set_like',id,1,0,{active:true})));
 await assertFails(social(d,'bob',intent('add_comment',id,2,0,{body:'Stale target'},randomUUID())));
 await seed('communityMemberships/bob',{uid:'bob',schemaVersion:1,status:'restricted',membershipVersion:2,createdAt:Timestamp.now(),updatedAt:Timestamp.now()});
 await assertFails(social(d,'bob',intent('set_like',id,1,0,{active:true})));
 await assertFails(social(d,'bob',intent('add_comment',id,1,0,{body:'Restricted'},randomUUID())));
});
test('private name draft works before verification without public/contact side effects',async()=>{
 const d=db('bob',false),path='users/bob/profileDraft/current';
 const draft={ownerUid:'bob',schemaVersion:1,version:1,displayName:'Fictional Member',avatarKey:'default',regionCode:null,bio:'',submission:'draft',lastOperationId:randomUUID(),updatedAt:serverTimestamp()};
 await assertSucceeds(setDoc(doc(d,path),draft));assert.equal((await getDoc(doc(d,path))).data().displayName,'Fictional Member');
 await assertFails(updateDoc(doc(d,path),{displayName:' ',version:2,lastOperationId:randomUUID(),updatedAt:serverTimestamp()}));
 assert.equal((await getDoc(doc(d,'users/bob/private/contact'))).exists(),false);
 assert.equal((await getDoc(doc(d,'publicProfiles/bob'))).exists(),false);
 await assertFails(getDoc(doc(db('alice'),path)));
});
test('own conversation index is private, bounded and cannot forge a participant grant',async()=>{
 const id=randomUUID(),d=db('bob',false);await seed(`users/bob/conversationIndex/${id}`,{reservationId:id,updatedAt:Timestamp.now()});
 const rows=await assertSucceeds(getDocs(query(collection(d,'users/bob/conversationIndex'),orderBy('updatedAt','desc'),orderBy(documentId(),'desc'),limit(20))));assert.equal(rows.size,1);
 await assertFails(getDocs(query(collection(db('alice'),'users/bob/conversationIndex'),limit(20))));
 await assertFails(setDoc(doc(d,`reservationCommunication/${id}`),{hostUid:'bob',applicantUid:'alice',state:'active'}));
 await assertFails(setDoc(doc(d,`conversations/${id}/messages/${randomUUID()}`),{body:'No grant'}));
});
test('selected pair retains history and own block management; outsiders and ungated sends denied',async()=>{
 const id=randomUUID(),mid=randomUUID();await seed(`reservationCommunication/${id}`,{schemaVersion:1,reservationId:id,hostUid:'alice',applicantUid:'bob',grantVersion:1,state:'closed',createdAt:Timestamp.now()});
 await seed(`conversations/${id}/messages/${mid}`,{schemaVersion:1,reservationId:id,messageId:mid,senderUid:'alice',sequence:1,body:'Fictional retained history',source:'participant_message',createdAt:Timestamp.now(),operationId:randomUUID()});
 for(const uid of ['alice','bob'])await assertSucceeds(getDocs(query(collection(db(uid),`conversations/${id}/messages`),orderBy('sequence','desc'),orderBy(documentId(),'desc'),limit(20))));
 await assertFails(getDoc(doc(db('mallory'),`conversations/${id}/messages/${mid}`)));
 const block={uid:'bob',blocked:true,version:1,updatedAt:serverTimestamp(),operationId:randomUUID()};
 await assertSucceeds(setDoc(doc(db('bob'),`conversationBlocks/${id}/members/bob`),block));
 await assertSucceeds(updateDoc(doc(db('bob'),`conversationBlocks/${id}/members/bob`),{blocked:false,version:2,updatedAt:serverTimestamp(),operationId:randomUUID()}));
 await assertFails(setDoc(doc(db('alice'),`conversationBlocks/${id}/members/bob`),block));
 await assertFails(setDoc(doc(db('bob',false),`conversations/${id}/messages/${randomUUID()}`),{body:'No active preparation grant'}));
});
