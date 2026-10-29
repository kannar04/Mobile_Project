import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,setDoc,getDoc,getDocs,updateDoc,deleteDoc,collection,query,where,orderBy,documentId,limit,startAfter,serverTimestamp,Timestamp,writeBatch,runTransaction} from 'firebase/firestore';
import {ref,uploadBytes,getMetadata,getDownloadURL,deleteObject,listAll} from 'firebase/storage';
import {MediaService,hash} from '../firebase-domain/media.mjs';
import {NotificationService} from '../firebase-domain/notifications.mjs';
import {intent,social} from './daily-protocol.mjs';
let env;const projectId='demo-anilove-feature-tests',bucket=`${projectId}.appspot.com`,db=(uid='alice',verified=true)=>env.authenticatedContext(uid,{email_verified:verified}).firestore();
const storage=(uid='alice',verified=true)=>env.authenticatedContext(uid,{email_verified:verified}).storage(`gs://${bucket}`);
const item=(uid='alice',extra={})=>({schemaVersion:1,recipientUid:uid,kind:'comment',routeType:'post',targetId:randomUUID(),sourceVersion:1,templateKey:'comments_on_post',createdAt:Timestamp.now(),updatedAt:Timestamp.now(),expiresAt:Timestamp.fromMillis(Date.now()+86400000),itemVersion:1,state:'active',readAt:null,dismissedAt:null,...extra});
const own=id=>`users/alice/notificationInbox/${id}`;
const seed=async records=>env.withSecurityRulesDisabled(async c=>{for(const [p,d]of Object.entries(records))await setDoc(doc(c.firestore(),p),d);});
before(async()=>{
 assert.equal(process.env.FIRESTORE_EMULATOR_HOST,'127.0.0.1:8280');assert.equal(process.env.FIREBASE_STORAGE_EMULATOR_HOST,'127.0.0.1:9298');
 env=await initializeTestEnvironment({projectId,firestore:{host:'127.0.0.1',port:8280,rules:await readFile('../firestore.rules','utf8')},storage:{host:'127.0.0.1',port:9298,rules:await readFile('../storage.rules','utf8')}});
});after(async()=>env?.cleanup());
beforeEach(async()=>{await env.clearFirestore();await env.clearStorage();await seed(Object.fromEntries(['alice','bob','restricted'].map(uid=>[`users/${uid}`,{uid,schemaVersion:1,createdAt:Timestamp.now()}])));await seed({'securitySubjects/restricted':{status:'restricted',roles:['member'],updatedAt:Timestamp.now()}});});

test('own inbox requires shell; restricted help ownership preserved; foreign/global/unbounded reads denied',async()=>{
 const id='a'.repeat(64);await seed({[own(id)]:item(),[`users/restricted/notificationInbox/${id}`]:item('restricted')});
 await assertSucceeds(getDoc(doc(db(),own(id))));await assertFails(getDoc(doc(db('bob'),own(id))));await assertSucceeds(getDoc(doc(db('restricted',false),`users/restricted/notificationInbox/${id}`)));
 await assertFails(getDocs(collection(db(),'users/alice/notificationInbox')));await assertSucceeds(getDocs(query(collection(db(),'users/alice/notificationInbox'),where('state','in',['active','unavailable']),orderBy('createdAt','desc'),orderBy(documentId(),'desc'),limit(20))));
 await assertFails(getDocs(query(collection(db(),'users/alice/notificationInbox'),limit(21))));await seed({'users/alice':{uid:'alice',schemaVersion:1,createdAt:Timestamp.now(),roles:['admin']}});await assertFails(getDoc(doc(db(),own(id))));
});
test('clients cannot fabricate/delete inbox or replace routes, source, timestamps or recipient',async()=>{
 const id='a'.repeat(64);await assertFails(setDoc(doc(db(),own(id)),item()));await seed({[own(id)]:item()});
 for(const patch of [{recipientUid:'bob'},{targetId:randomUUID()},{templateKey:'private_body'},{createdAt:serverTimestamp()},{sourceVersion:2},{body:'secret'}])await assertFails(updateDoc(doc(db(),own(id)),{...patch,readAt:serverTimestamp(),updatedAt:serverTimestamp(),itemVersion:2}));
 await assertFails(deleteDoc(doc(db(),own(id))));
});
test('read is monotonic; dismiss retains prior read; reverse/foreign/stale updates denied',async()=>{
 const id='a'.repeat(64),ref=doc(db(),own(id));await seed({[own(id)]:item()});await assertSucceeds(updateDoc(ref,{readAt:serverTimestamp(),updatedAt:serverTimestamp(),itemVersion:2}));const read=(await getDoc(ref)).data().readAt;
 await assertFails(updateDoc(ref,{readAt:null,updatedAt:serverTimestamp(),itemVersion:3}));await assertFails(updateDoc(ref,{readAt:serverTimestamp(),updatedAt:serverTimestamp(),itemVersion:3}));await assertFails(updateDoc(ref,{state:'dismissed',dismissedAt:serverTimestamp(),updatedAt:serverTimestamp(),itemVersion:2}));
 await assertSucceeds(updateDoc(ref,{state:'dismissed',dismissedAt:serverTimestamp(),updatedAt:serverTimestamp(),itemVersion:3}));assert((await getDoc(ref)).data().readAt.isEqual(read));await assertFails(updateDoc(ref,{state:'active',updatedAt:serverTimestamp(),itemVersion:4}));
});
test('bounded20 mark-read batch fits access budget, unseen rows remain unread and dot query works',async()=>{
 const records={};for(let i=0;i<21;i++)records[own(i.toString(16).padStart(64,'0'))]=item();await seed(records);const d=db(),batch=writeBatch(d);for(const path of Object.keys(records).slice(0,20))batch.update(doc(d,path),{readAt:serverTimestamp(),itemVersion:2,updatedAt:serverTimestamp()});await assertSucceeds(batch.commit());
 const dot=await assertSucceeds(getDocs(query(collection(db(),'users/alice/notificationInbox'),where('readAt','==',null),where('expiresAt','>',Timestamp.now()),orderBy('expiresAt'),limit(1))));assert.equal(dot.size,1);assert.equal(dot.docs[0].id,'14'.padStart(64,'0'));
});
test('read/dismiss races return one version winner; dismissed cannot remain unread',async()=>{
 const id='a'.repeat(64);await seed({[own(id)]:item()});const ref=doc(db(),own(id));const results=await Promise.allSettled([updateDoc(ref,{readAt:serverTimestamp(),itemVersion:2,updatedAt:serverTimestamp()}),updateDoc(ref,{readAt:serverTimestamp(),state:'dismissed',dismissedAt:serverTimestamp(),itemVersion:2,updatedAt:serverTimestamp()})]);assert.equal(results.filter(r=>r.status==='fulfilled').length,1);assert.equal((await getDoc(ref)).data().itemVersion,2);
});
test('expired candidate pagination advances raw cursor through empty visible pages',async()=>{
 const records={};for(let i=0;i<42;i++)records[own(i.toString(16).padStart(64,'0'))]=item('alice',{createdAt:Timestamp.fromMillis(100000-i),expiresAt:Timestamp.fromMillis(i<40?100:Date.now()+10000)});await seed(records);
 const q=cursor=>query(collection(db(),'users/alice/notificationInbox'),where('state','in',['active','unavailable']),orderBy('createdAt','desc'),orderBy(documentId(),'desc'),...(cursor?[startAfter(cursor)]:[]),limit(20));const p1=await getDocs(q()),p2=await getDocs(q(p1.docs.at(-1))),p3=await getDocs(q(p2.docs.at(-1)));assert.equal(p1.size,20);assert.equal(p2.size,20);assert.equal(p3.size,2);assert(p3.docs.every(d=>d.get('expiresAt').toMillis()>Date.now()));
});
test('preferences start off and require explicit versioned opt-in; no owner override/global access',async()=>{
 const path='users/alice/notificationPreferences/main',value={schemaVersion:1,pushEnabled:false,commentsPush:false,ownStatusPush:false,version:1,updatedAt:serverTimestamp()};await assertFails(setDoc(doc(db(),path),{...value,pushEnabled:true}));await assertSucceeds(setDoc(doc(db(),path),value));
 await assertSucceeds(updateDoc(doc(db(),path),{pushEnabled:true,commentsPush:true,version:2,updatedAt:serverTimestamp()}));await assertFails(updateDoc(doc(db(),path),{version:2,updatedAt:serverTimestamp()}));await assertFails(updateDoc(doc(db(),path),{pushEnabled:false,version:3,updatedAt:serverTimestamp()}));await assertFails(getDoc(doc(db('bob'),path)));await assertFails(deleteDoc(doc(db(),path)));
});
test('all source/job/token/challenge/quota/operator writes and reads stay private',async()=>{
 for(const path of ['notificationSources/x','pushJobs/x','pushInstallations/x','pushTokenOwners/x','pushBindingChallenges/x','notificationQuota/alice/days/2026-10-14','pushUserBindings/alice','pushBindingRates/alice','pushBindingRequests/alice/receipts/x','mediaTickets/x','mediaQuota/alice','mediaRetention/x']){await assertFails(setDoc(doc(db(),path),{uid:'alice'}));await seed({[path]:{uid:'alice'}});await assertFails(getDoc(doc(db(),path)));}
 await assertFails(setDoc(doc(db(),'notificationPolicy/current'),{enabled:true}));await assertFails(setDoc(doc(db(),'mediaPolicy/current'),{enabled:true}));await seed({'notificationPolicy/current':{enabled:false},'mediaPolicy/current':{enabled:false}});await assertFails(getDoc(doc(db(),'notificationPolicy/current')));await assertFails(getDoc(doc(db(),'mediaPolicy/current')));
});
test('owner asset exact/paged reads only; ready/variants/access and receipts cannot be forged',async()=>{
 const id=randomUUID(),asset={schemaVersion:1,assetId:id,ownerUid:'alice',createdAt:Timestamp.now(),state:'ready'};await seed({[`mediaAssets/${id}`]:asset});await assertSucceeds(getDoc(doc(db(),`mediaAssets/${id}`)));await assertFails(getDoc(doc(db('bob'),`mediaAssets/${id}`)));await assertSucceeds(getDocs(query(collection(db(),'mediaAssets'),where('ownerUid','==','alice'),orderBy('createdAt','desc'),orderBy(documentId(),'desc'),limit(20))));await assertFails(getDocs(query(collection(db(),'mediaAssets'),limit(20))));await assertFails(updateDoc(doc(db(),`mediaAssets/${id}`),{state:'ready',variants:{}}));await assertFails(setDoc(doc(db(),'mediaAccess/target'),{state:'visible'}));await assertFails(setDoc(doc(db(),`mediaCommands/alice/receipts/${randomUUID()}`),{outcome:'ready'}));
});
async function ticket(id=randomUUID(),extra={}){await seed({[`mediaTickets/${id}`]:{uid:'alice',assetId:id,purpose:'community_private',stagingPath:`staging/alice/${id}/candidate`,state:'issued',expiresAt:Timestamp.fromMillis(Date.now()+3600000),maxBytes:5242880,...extra},'communityMemberships/alice':{uid:'alice',schemaVersion:1,status:'active'}});return id;}
const jpeg=Buffer.from([255,216,255,217]);
test('Storage service gate denies even valid issued owner upload; staging read/list/delete and foreign upload denied',async()=>{
 const id=await ticket(),path=`staging/alice/${id}/candidate`;await assertFails(uploadBytes(ref(storage(),path),jpeg,{contentType:'image/jpeg'}));await env.withSecurityRulesDisabled(c=>uploadBytes(ref(c.storage(`gs://${bucket}`),path),jpeg,{contentType:'image/jpeg'}));await assertFails(uploadBytes(ref(storage(),path),jpeg,{contentType:'image/jpeg'}));await assertFails(getMetadata(ref(storage(),path)));await assertFails(deleteObject(ref(storage(),path)));await assertFails(listAll(ref(storage(),'staging/alice')));
 const other=await ticket();await assertFails(uploadBytes(ref(storage('bob'),`staging/alice/${other}/candidate`),jpeg,{contentType:'image/jpeg'}));
});
test('synthetic probe proves Firebase-minted bearer token bypasses read rules; client upload gate must remain closed',async()=>{
 const id=randomUUID(),path=`staging/alice/${id}/candidate`;let url;
 await env.withSecurityRulesDisabled(async c=>{const object=ref(c.storage(`gs://${bucket}`),path);await uploadBytes(object,jpeg,{contentType:'image/jpeg'});url=await getDownloadURL(object);});
 await assertFails(getMetadata(ref(storage(),path)));const unauthenticated=await fetch(url);assert.equal(unauthenticated.status,200);assert.equal((await unauthenticated.arrayBuffer()).byteLength,jpeg.length);
 const valid=await ticket();await assertFails(uploadBytes(ref(storage(),`staging/alice/${valid}/candidate`),jpeg,{contentType:'image/jpeg'}));
});
test('Storage expired/cancelled ticket, unverified/restricted member, metadata injection, bad MIME and oversized upload denied',async()=>{
 for(const extra of [{expiresAt:Timestamp.fromMillis(1)},{state:'cancelled'}]){const id=await ticket(undefined,extra);await assertFails(uploadBytes(ref(storage(),`staging/alice/${id}/candidate`),jpeg,{contentType:'image/jpeg'}));}
 const id=await ticket(),path=`staging/alice/${id}/candidate`;await assertFails(uploadBytes(ref(storage('alice',false),path),jpeg,{contentType:'image/jpeg'}));await assertFails(uploadBytes(ref(storage(),path),jpeg,{contentType:'text/plain'}));await assertFails(uploadBytes(ref(storage(),path),jpeg,{contentType:'image/jpeg',customMetadata:{firebaseStorageDownloadTokens:'leak'}}));await assertFails(uploadBytes(ref(storage(),path),Buffer.alloc(5242881),{contentType:'image/jpeg'}));
 await seed({'communityMemberships/alice':{uid:'alice',schemaVersion:1,status:'restricted'}});await assertFails(uploadBytes(ref(storage(),path),jpeg,{contentType:'image/jpeg'}));
});
test('Storage final only owner-ready exact generation; hide/expiry/foreign/access bridge does not grant bytes',async()=>{
 const id=randomUUID(),path=`final/alice/${id}/display.jpg`;let metadata;
 await env.withSecurityRulesDisabled(async c=>{metadata=(await uploadBytes(ref(c.storage(`gs://${bucket}`),path),jpeg,{contentType:'image/jpeg'})).metadata;});
 const asset={schemaVersion:1,assetId:id,ownerUid:'alice',state:'ready',expiresAt:Timestamp.fromMillis(Date.now()+86400000),variants:{display:{path,generation:metadata.generation}}};await seed({[`mediaAssets/${id}`]:asset});await assertSucceeds(getMetadata(ref(storage(),path)));await assertFails(getMetadata(ref(storage('bob'),path)));await assertFails(uploadBytes(ref(storage(),path),jpeg,{contentType:'image/jpeg'}));await assertFails(deleteObject(ref(storage(),path)));
 for(const patch of [{state:'cleanup_pending'},{expiresAt:Timestamp.fromMillis(1)},{variants:{display:{path,generation:'0'}}}]){await seed({[`mediaAssets/${id}`]:{...asset,...patch}});await assertFails(getMetadata(ref(storage(),path)));}
 await seed({'mediaAccess/fake':{state:'visible',permittedAssetIds:[id]}});await assertFails(getMetadata(ref(storage('bob'),path)));
});
test('trusted media kernel commits real Firestore transaction while client issuance stays denied',async()=>{
 await seed({'mediaPolicy/current':{schemaVersion:1,policyVersion:1,enabled:true},'communityMemberships/alice':{uid:'alice',schemaVersion:1,status:'active',membershipVersion:1,createdAt:Timestamp.now(),updatedAt:Timestamp.now()}});
 await env.withSecurityRulesDisabled(async c=>{const d=c.firestore(),store={transaction:work=>runTransaction(d,tx=>work({get:async p=>(await tx.get(doc(d,p))).data(),set:(p,v)=>tx.set(doc(d,p),v)}))},service=new MediaService(store),input={assetId:randomUUID(),operationId:randomUUID(),purpose:'community_private',inputHash:'a'.repeat(64)};await service.issue({uid:'alice',emailVerified:true},input);await service.issue({uid:'alice',emailVerified:true},input);assert.equal((await getDoc(doc(d,'mediaQuota/alice'))).get('grants').length,1);});
});
test('real F31 client comment witness derives one owner inbox transaction; self/held source excluded',async()=>{
 const postId=randomUUID(),commentId=randomUUID(),operationId=randomUUID();
 await seed({'notificationPolicy/current':{schemaVersion:1,enabled:true,pushEnabled:false,allowedAdapterVersions:['comment_v1'],activationWatermark:Timestamp.fromMillis(Date.now()-10000)},'communityMemberships/bob':{uid:'bob',schemaVersion:1,status:'active',membershipVersion:1,createdAt:Timestamp.now(),updatedAt:Timestamp.now()}});
 await env.withSecurityRulesDisabled(c=>social(c.firestore(),'alice',intent('publish',postId,0,0,{body:'Synthetic published fixture',bundledIllustrationKey:null,consent:true})));
 await social(db('bob'),'bob',intent('add_comment',postId,1,0,{body:'Actual client-committed comment'},commentId,0,operationId));
 let itemId;await env.withSecurityRulesDisabled(async c=>{const d=c.firestore(),store={transaction:work=>runTransaction(d,tx=>work({get:async p=>(await tx.get(doc(d,p))).data(),set:(p,v)=>tx.set(doc(d,p),v)}))},service=new NotificationService(store),event={writerUid:'bob',postId,commentId,operationId};const a=await service.deriveComment(event),b=await service.deriveComment(event);assert.equal(a.itemId,b.itemId);itemId=a.itemId;});
 const row=await assertSucceeds(getDoc(doc(db(),own(itemId))));assert.equal(row.get('templateKey'),'comments_on_post');assert.equal(row.get('readAt'),null);assert.equal(Object.keys(row.data()).length,14);await assertFails(getDoc(doc(db('bob'),own(itemId))));
});
