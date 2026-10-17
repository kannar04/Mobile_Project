import {CareService} from '../firebase-domain/care.mjs';
import {port} from './safety-firestore-port.mjs';
import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,getDoc,setDoc,updateDoc,deleteDoc,getDocs,collection,query,where,orderBy,documentId,limit,startAfter,runTransaction,serverTimestamp,Timestamp,writeBatch} from 'firebase/firestore';
import {register,registration} from './pet-protocol.mjs';
import {canonical,CatalogService,ReviewService,availability,selection} from '../firebase-domain/adoption.mjs';
import {hash} from '../firebase-domain/media.mjs';
let env;const db=(uid='alice',verified=true)=>env.authenticatedContext(uid,{email_verified:verified}).firestore();
const seed=records=>env.withSecurityRulesDisabled(async c=>{for(const [p,d]of Object.entries(records))await setDoc(doc(c.firestore(),p),d);});
const unknown=()=>({healthState:'unknown',color:'',behaviorNotes:'',careNeeds:'',sterilization:'unknown',vaccinations:[]});
before(async()=>{assert.equal(process.env.FIRESTORE_EMULATOR_HOST,'127.0.0.1:8280');env=await initializeTestEnvironment({projectId:'demo-anilove-feature-tests',firestore:{host:'127.0.0.1',port:8280,rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});});after(async()=>env?.cleanup());
beforeEach(async()=>{await env.clearFirestore();await seed({'safetyRuntimePolicy/current':{schemaVersion:1,careEnabled:true}});for(const uid of ['alice','bob','reviewer'])await seed({[`users/${uid}`]:{uid,schemaVersion:1,createdAt:Timestamp.now()},[`securitySubjects/${uid}`]:{status:'active',roles:['member'],updatedAt:Timestamp.now()}});});
async function pet(){return (await register(db(),'alice')).pet;}
async function care(petId,payload=unknown(),medicationNotes='',base={careVersion:0,identityVersion:1,custodyVersion:0},operationId=randomUUID(),transform=null){
 const d=db(),root=`petCare/${petId}`,v=base.careVersion+1,now=serverTimestamp(),input={payload,medicationNotes},digest=hash(canonical(input));
 const writes=[
  [root,{schemaVersion:1,petId,currentCareVersion:v,controlVersion:v,identityVersion:base.identityVersion,custodyVersion:base.custodyVersion,updatedAt:now,lastOperationId:operationId}],
  [`${root}/revisions/v${v}`,{schemaVersion:1,petId,careVersion:v,actorUid:'alice',source:'owner_report',reportedAt:now,payload,identityVersion:base.identityVersion,custodyVersion:base.custodyVersion,digest,operationId}],
  [`petCarePrivate/${petId}/revisions/v${v}`,{schemaVersion:1,petId,careVersion:v,actorUid:'alice',medicationNotes,operationId,createdAt:now}],
  [`${root}/operations/${operationId}`,{schemaVersion:1,petId,actorUid:'alice',command:'save',operationId,inputDigest:digest,base,result:{careVersion:v},input,committedAt:now}]
 ];
 if(transform){const batch=writeBatch(d);for(const [p,value]of transform(writes))batch.set(doc(d,p),value);await batch.commit();}else await env.withSecurityRulesDisabled(c=>new CareService(port(c.firestore())).save({uid:'alice',emailVerified:true},{petId,operationId,expectedCareVersion:base.careVersion,expectedIdentityVersion:base.identityVersion,expectedCustodyVersion:base.custodyVersion,payload,medicationNotes}));return {operationId,writes};
}
const discovery=(id=randomUUID(),extra={})=>({schemaVersion:1,listingId:id,petId:randomUUID(),ownerUid:'alice',contentVersion:1,publicationVersion:1,audience:'signed_in',state:'visible',firstPublishedAt:Timestamp.now(),updatedAt:Timestamp.now(),title:'Synthetic offer',petName:'Mèo Mun',nameKey:'meo mun',speciesId:'cat',breedId:'cat_unknown',regionId:'north',regionLabel:'Northern area',sex:'unknown',ageBand:'unknown',offerType:'adoption',availability:'available',careCues:{source:'owner_report',healthState:'unknown'},bundledIllustrationKey:'cat',mediaDescriptors:[],...extra});
async function target(id=randomUUID()){
 const row=discovery(id),r=registration('alice',row.petId),records=Object.fromEntries(r.writes);
 records[`pets/${row.petId}/claims/alice`]={...records[`pets/${row.petId}/claims/alice`],reviewState:'accepted_for_demo'};
 records[`petOfferAuthorities/${row.petId}`]={...records[`petOfferAuthorities/${row.petId}`],offerUid:'alice',status:'enabled',authorityVersion:1,decisionId:randomUUID()};
 records[`petListingSlots/${row.petId}`]={...records[`petListingSlots/${row.petId}`],activeListingId:id,slotVersion:1};
 records[`listings/${id}`]={schemaVersion:1,listingId:id,ownerUid:'alice',petId:row.petId,contentVersion:1,activeRevisionId:'v1',lifecycle:'open',visibility:'visible',controlVersion:1,createdAt:Timestamp.now(),updatedAt:Timestamp.now(),lastOperationId:randomUUID(),closureReason:null,closedAt:null,archivedAt:null};
 records[`listingPublication/${id}`]={schemaVersion:1,listingId:id,petId:row.petId,ownerUid:'alice',approvedContentVersion:1,decisionId:randomUUID(),publicationVersion:1,state:'eligible',blockedReasonCode:null};
 records['listingDiscoveryPolicy/current']={schemaVersion:1,enabled:true,writerSetVersion:1};records[`listingDiscovery/${id}`]=row;await seed(records);return id;
}
async function favorite(uid,id,desired,op=randomUUID(),transform=w=>w,verified=true){
 const d=db(uid,verified),prefix=`users/${uid}/`,f=`${prefix}favorites/${id}`,m=`${prefix}favoriteMeta/main`,rp=`${prefix}favoriteOperations/${op}`;
 return runTransaction(d,async tx=>{const prior=(await tx.get(doc(d,rp))).data();if(prior){assert.equal(prior.desiredState,desired);assert.equal(prior.listingId,id);return prior;}
  const old=(await tx.get(doc(d,f))).data(),meta=(await tx.get(doc(d,m))).data();if(!meta&&old)throw Error('repair_required');const mv=meta?.metaVersion??0,fv=old?.favoriteVersion??0,count=meta?.activeCount??0,delta=Number(desired)-Number(!!old),base={favoriteVersion:fv,metaVersion:mv},now=serverTimestamp();
  if(delta>0)await tx.get(doc(d,`listingDiscovery/${id}`));
  if(delta===0&&!meta)return {saved:false};
  const record={schemaVersion:1,uid,listingId:id,operationId:op,command:'desired_saved_or_removed',desiredState:desired,inputDigest:hash(canonical({listingId:id,desiredState:desired,base})),base,result:{favoriteVersion:desired?(old?fv:1):0,metaVersion:mv+Number(delta!==0),activeCount:count+delta,saved:desired},delta,serverCreatedAt:now};
  const writes=[[rp,record]];if(delta){writes.push([m,{schemaVersion:1,uid,activeCount:count+delta,metaVersion:mv+1,lastOperationId:op,updatedAt:now}]);writes.push([f,desired?{schemaVersion:1,uid,listingId:id,savedAt:now,favoriteVersion:1,lastOperationId:op}:null]);}
  for(const [p,value]of transform(writes))value===null?tx.delete(doc(d,p)):tx.set(doc(d,p),value);return record;
 });
}
function trustedStore(d){return {transaction:work=>runTransaction(d,tx=>work({get:async p=>(await tx.get(doc(d,p))).data(),set:(p,v)=>tx.set(doc(d,p),v),delete:p=>tx.delete(doc(d,p))}))};}
const catalog=(kind,id,extra={})=>({schemaVersion:1,id,kind,labels:{vi:'Mẫu',en:'Demo'},normalizedLabel:'demo',sortKey:id,state:'active',version:1,updatedAt:new Date(),...extra});

test('F11 catalogs authenticated bounded selectors; retirement retains exact history and ordinary Admin forgery denied',async()=>{
 await seed({'catalogSpecies/cat':catalog('species','cat'),'catalogRegions/north':catalog('region','north',{level:'coarse',parentRegionId:null}),'catalogBreeds/cat_unknown':catalog('breed','cat_unknown',{speciesId:'cat',variant:'unknown'})});
 await assertSucceeds(getDocs(query(collection(db(),'catalogSpecies'),where('state','==','active'),orderBy('sortKey'),orderBy(documentId()),limit(50))));await assertSucceeds(getDocs(query(collection(db(),'catalogBreeds'),where('state','==','active'),where('speciesId','==','cat'),orderBy('sortKey'),orderBy(documentId()),limit(50))));await assertFails(getDocs(query(collection(db(),'catalogSpecies'),limit(51))));await assertFails(setDoc(doc(db(),'catalogSpecies/dog'),catalog('species','dog')));
 await seed({'catalogSpecies/cat':catalog('species','cat',{state:'retired'})});await assertSucceeds(getDoc(doc(db(),'catalogSpecies/cat')));await assertFails(getDocs(query(collection(db(),'catalogBreeds'),where('state','==','active'),where('speciesId','==','cat'),limit(50))));
});
test('F11 trusted revision transaction preserves parent/variant/ID and retries without duplicate revision',async()=>{
 await env.withSecurityRulesDisabled(async c=>{const store=trustedStore(c.firestore()),authorize=async(tx,actor,capability)=>({uid:actor.uid,capability}),service=new CatalogService(store,{authorize});let command={kind:'species',entry:catalog('species','cat'),expectedVersion:0,operationId:randomUUID(),reason:'Synthetic initial catalog'};await service.execute({uid:'reviewer'},command);await service.execute({uid:'reviewer'},command);
  await service.execute({uid:'reviewer'},{kind:'breed',entry:catalog('breed','cat_mixed',{speciesId:'cat',variant:'mixed'}),expectedVersion:0,operationId:randomUUID(),reason:'Unknown pedigree allowed'});
  await assert.rejects(service.execute({uid:'reviewer'},{kind:'breed',entry:catalog('breed','cat_mixed',{speciesId:'dog',variant:'mixed'}),expectedVersion:1,operationId:randomUUID(),reason:'Unsafe reparent'}),/immutable/);
  assert.equal((await getDoc(doc(c.firestore(),'catalogSpecies/cat'))).get('version'),1);
 });
});
test('F08 unknown care four-record commit keeps private text out of owner-reported revision',async()=>{
 const id=await pet();await assertSucceeds(care(id,unknown(),'Synthetic private instructions'));const r=await getDoc(doc(db(),`petCare/${id}/revisions/v1`));assert.equal(r.get('payload.healthState'),'unknown');assert.equal(r.get('source'),'owner_report');assert.equal(r.get('medicationNotes'),undefined);await assertFails(getDoc(doc(db('bob'),`petCare/${id}`)));await assertFails(getDoc(doc(db('bob'),`petCarePrivate/${id}/revisions/v1`)));
});
test('F08 r4 max20 exact vaccinations commit through managed transaction; client/history immutable',async()=>{
 const id=await pet(),payload=unknown();payload.vaccinations=Array.from({length:20},(_,i)=>({entryId:randomUUID(),typeLabel:'x'.repeat(80),reportedDate:'2024-02-29',datePrecision:'exact',provenance:'owner_report'}));await assertSucceeds(care(id,payload));await assertFails(updateDoc(doc(db(),`petCare/${id}/revisions/v1`),{'payload.healthState':'no_concerns_reported'}));await assertSucceeds(getDocs(query(collection(db(),`petCare/${id}/revisions`),orderBy('careVersion','desc'),limit(20))));
});
test('F08 dates retain month/year/unknown precision; duplicate IDs and joint8KiB body rejected',async()=>{
 const id=await pet(),payload=unknown();payload.vaccinations=[{entryId:randomUUID(),typeLabel:'Demo',reportedDate:'2024-02',datePrecision:'month',provenance:'owner_report'},{entryId:randomUUID(),typeLabel:'Demo',reportedDate:'2024',datePrecision:'year',provenance:'owner_report'},{entryId:randomUUID(),typeLabel:'Demo',reportedDate:null,datePrecision:'unknown',provenance:'owner_report'}];await assertSucceeds(care(id,payload));const duplicate=unknown();duplicate.vaccinations=[payload.vaccinations[0],payload.vaccinations[0]];await assert.rejects(care(id,duplicate,'',{careVersion:1,identityVersion:1,custodyVersion:0}));await assert.rejects(care(id,{...unknown(),behaviorNotes:'🐈'.repeat(1000),careNeeds:'🐈'.repeat(1000)},'🐈'.repeat(1000),{careVersion:1,identityVersion:1,custodyVersion:0}));
});
test('F08 omit any companion or spoof verified/oversized/future/impossible date fails',async()=>{
 const id=await pet();for(let omit=0;omit<4;omit++)await assert.rejects(care(id,unknown(),'',undefined,randomUUID(),w=>w.filter((x,i)=>i!==omit)));
 for(const patch of [{healthState:'verified_healthy'},{careNeeds:'x'.repeat(1001)},{vaccinations:[{entryId:randomUUID(),typeLabel:'Demo',reportedDate:'2999-01-01',datePrecision:'exact',provenance:'owner_report'}]},{vaccinations:[{entryId:randomUUID(),typeLabel:'Demo',reportedDate:'2023-02-29',datePrecision:'exact',provenance:'owner_report'}]}])await assert.rejects(care(id,{...unknown(),...patch}));
});
test('F08 identity/custody/reservation/public-bound listing races deny stale care; new editor gets no old private notes',async()=>{
 const id=await pet();await care(id);await seed({[`petActivityGates/${id}`]:{schemaVersion:1,petId:id,state:'reserved',activeReservationId:randomUUID(),activePlacementId:null,gateVersion:1,custodyVersion:0,updatedAt:Timestamp.now()}});await assert.rejects(care(id,unknown(),'',{careVersion:1,identityVersion:1,custodyVersion:0}));
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),`pets/${id}`),{editorUid:'bob',custodyVersion:1}));await assertFails(getDoc(doc(db('bob'),`petCarePrivate/${id}/revisions/v1`)));await assert.rejects(care(id,unknown(),'',{careVersion:1,identityVersion:1,custodyVersion:0}));
});
test('F12 live gate off denies fixtures; signed-in page20 only, projections/private roots cannot be forged',async()=>{
 const id=randomUUID();await seed({[`listingDiscovery/${id}`]:discovery(id)});const q=()=>query(collection(db(),'listingDiscovery'),where('audience','==','signed_in'),where('state','==','visible'),where('availability','==','available'),orderBy('firstPublishedAt','desc'),orderBy(documentId(),'desc'),limit(20));await assertFails(getDocs(q()));await target(id);await assertSucceeds(getDocs(q()));await assertFails(getDocs(query(collection(db(),'listingDiscovery'),limit(20))));await assertFails(setDoc(doc(db(),`listingDiscovery/${randomUUID()}`),discovery()));await assertFails(setDoc(doc(db(),'listingDiscoveryPolicy/current'),{enabled:true}));
});
test('F12 extra contact/evidence projection rejects exact access; query sanitation requires verified trusted writers',async()=>{
 const id=await target();await seed({[`listingDiscovery/${id}`]:discovery(id,{phone:'+84999999999'})});await assertFails(getDoc(doc(db(),`listingDiscovery/${id}`)));
 // Unknown/malformed imported data must keep the writer policy OFF, not be queried.
 await seed({'listingDiscoveryPolicy/current':{schemaVersion:1,enabled:false,writerSetVersion:0}});await assertFails(getDocs(query(collection(db(),'listingDiscovery'),where('audience','==','signed_in'),where('state','==','visible'),where('availability','==','available'),limit(20))));
});
test('F13 enumerate96 supported optional-filter/order queries on synthetic sanitized projections',async()=>{
 const id=await target(),optional=['speciesId','breedId','regionId','ageBand','sex','offerType'],values=['cat','cat_unknown','north','unknown','unknown','adoption'];let shapes=0;
 for(let mask=0;mask<64;mask++){if(mask&2&&!(mask&1))continue;for(const prefix of [false,true]){const filters=[where('audience','==','signed_in'),where('state','==','visible'),where('availability','==','available')];for(let i=0;i<6;i++)if(mask&(1<<i))filters.push(where(optional[i],'==',values[i]));if(prefix)filters.push(where('nameKey','>=','meo'),where('nameKey','<','mep'));filters.push(orderBy(prefix?'nameKey':'firstPublishedAt',prefix?'asc':'desc'),orderBy(documentId(),prefix?'asc':'desc'),limit(20));const result=await assertSucceeds(getDocs(query(collection(db(),'listingDiscovery'),...filters)));assert.equal(result.size,1);shapes++;}}
 assert.equal(shapes,96);
});
test('F14 desired-state save/remove retry counts once; copied snapshots and foreign access denied',async()=>{
 // New-save client exact reads require canonical fixture gates; test collects writes without that read
 // to verify the operation-linked save predicate against the synthetic projection.
 const id=await target();await assertSucceeds(favorite('alice',id,true));await assertSucceeds(favorite('alice',id,true));assert.equal((await getDoc(doc(db(),'users/alice/favoriteMeta/main'))).get('activeCount'),1);await assertFails(getDoc(doc(db('bob'),`users/alice/favorites/${id}`)));
 await seed({[`listingDiscovery/${id}`]:discovery(id,{state:'hidden'})});await assertSucceeds(favorite('alice',id,false));assert.equal((await getDoc(doc(db(),'users/alice/favoriteMeta/main'))).get('activeCount'),0);await assertSucceeds(favorite('alice',id,false));await assertFails(favorite('alice',id,true));
});
test('F14 missing companion, standalone meta/receipt, extra copied fields and multi-target counter bypass denied',async()=>{
 const id=await target();await assertFails(favorite('alice',id,true,randomUUID(),w=>w.filter(([p])=>!p.includes('favoriteMeta'))));await assertFails(favorite('alice',id,true,randomUUID(),w=>w.filter(([p])=>!p.includes('favoriteOperations'))));await assertFails(favorite('alice',id,true,randomUUID(),w=>w.map(([p,d])=>[p,p.includes('/favorites/')?{...d,title:'Copied private title'}:d])));
 const other=await target();await assertFails(favorite('alice',id,true,randomUUID(),w=>[...w,[`users/alice/favorites/${other}`,w.find(([p])=>p.includes('/favorites/'))[1]]]));await assertFails(deleteDoc(doc(db(),'users/alice/favoriteMeta/main')));
});
test('F14 cap200 permits idempotent existing Save and restricted/unverified Remove independent of target',async()=>{
 const id=await target(),op=randomUUID();await seed({[`users/alice/favorites/${id}`]:{schemaVersion:1,uid:'alice',listingId:id,savedAt:Timestamp.now(),favoriteVersion:1,lastOperationId:op},'users/alice/favoriteMeta/main':{schemaVersion:1,uid:'alice',activeCount:200,metaVersion:200,lastOperationId:op,updatedAt:Timestamp.now()}});await assertSucceeds(favorite('alice',id,true));const other=await target();await assertFails(favorite('alice',other,true));await seed({'securitySubjects/alice':{status:'restricted',roles:['member'],updatedAt:Timestamp.now()}});await assertSucceeds(favorite('alice',id,false,randomUUID(),w=>w,false));
});
test('F10 ordinary members cannot mint review/decision/control or assign themselves capability',async()=>{
 const id=randomUUID();for(const path of [`listingReviewRequests/${id}/versions/v1`,`listingReviews/${id}/versions/v1/decisions/${randomUUID()}`,`listingPublication/${id}`,`listingReviewOperations/${randomUUID()}`])await assertFails(setDoc(doc(db(),path),{ownerUid:'alice',state:'approved'}));await assertFails(updateDoc(doc(db(),'securitySubjects/alice'),{roles:['admin']}));
});
test('F14 concurrent duplicate saves reconcile one row/count; deletion cannot smuggle a second target',async()=>{
 const id=await target();const concurrent=await Promise.allSettled([favorite('alice',id,true),favorite('alice',id,true)]);assert(concurrent.some(r=>r.status==='fulfilled'));assert.equal((await getDoc(doc(db(),'users/alice/favoriteMeta/main'))).get('activeCount'),1);await assertSucceeds(favorite('alice',id,true));const other=await target();await favorite('alice',other,true);
 await assertFails(favorite('alice',id,false,randomUUID(),w=>[...w,[`users/alice/favorites/${other}`,null]]));assert.equal((await getDoc(doc(db(),'users/alice/favoriteMeta/main'))).get('activeCount'),2);
});
test('F14 namespace with missing/corrupt meta is not reset by the ordinary command',async()=>{
 const id=await target();await seed({[`users/alice/favorites/${id}`]:{schemaVersion:1,uid:'alice',listingId:id,savedAt:Timestamp.now(),favoriteVersion:1,lastOperationId:randomUUID()}});await assert.rejects(favorite('alice',id,false),/repair_required/);
 await seed({'users/alice/favoriteMeta/main':{schemaVersion:1,uid:'alice',activeCount:0,metaVersion:1,lastOperationId:randomUUID(),updatedAt:Timestamp.now()}});await assertFails(favorite('alice',id,false));
});
test('F10 owners can read reason/history while other members cannot read or rewrite a decision',async()=>{
 const id=await target(),decision=randomUUID(),path=`listingReviews/${id}/versions/v1/decisions/${decision}`;await seed({[path]:{schemaVersion:1,reviewerUid:'reviewer',decision:'reject',reason:'Synthetic actionable reason',sourceFingerprint:'a'.repeat(64),serverCreatedAt:Timestamp.now(),operationId:decision}});await assertSucceeds(getDoc(doc(db(),path)));await assertFails(getDoc(doc(db('bob'),path)));await assertFails(updateDoc(doc(db(),path),{decision:'approve'}));await assertSucceeds(getDocs(query(collection(db(),`listingReviews/${id}/versions/v1/decisions`),orderBy('serverCreatedAt','desc'),orderBy(documentId(),'desc'),limit(20))));
});
