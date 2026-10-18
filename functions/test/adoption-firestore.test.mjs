import test from 'node:test';import assert from 'node:assert/strict';import {initializeApp,deleteApp} from 'firebase-admin/app';import {getFirestore} from 'firebase-admin/firestore';import {randomUUID} from 'node:crypto';import {AdoptionStore,AdoptionRuntime} from '../adoption-runtime.mjs';import {AdoptionSourceService,sha} from '../../firebase-domain/adoption-source2.mjs';import {approved,care,host,applicant,now} from '../../firebase-tests/adoption-source2.test.mjs';
const enabled=!!process.env.FIRESTORE_EMULATOR_HOST;
async function fixture(){assert(process.env.FIRESTORE_EMULATOR_HOST?.startsWith('127.0.0.1:'));const app=initializeApp({projectId:'demo-anilove-adoption2'},'adoption-'+randomUUID()),db=getFirestore(app),f=await approved();const batch=db.batch();for(const[p,d]of f.store.docs)batch.set(db.doc(p),d);await batch.commit();return {...f,app,db,service:new AdoptionSourceService(new AdoptionStore(db),{clock:()=>now})};}
test('actual Firestore timestamps preserve review fingerprint, Show and questionnaire binding commit atomically',{skip:!enabled},async()=>{const f=await fixture();try{await f.service.execute(host,'show',{operationId:randomUUID(),listingId:f.listing,expectedControlVersion:1});const pub=await f.db.doc('publicListings/'+f.listing).get(),binding=await f.db.doc(`listingQuestionnaireBindings/${f.listing}/versions/v1`).get();assert(pub.exists);assert(binding.exists);const runtime=new AdoptionRuntime(f.db);const read=await runtime.read(host,'publicDetail',{listingId:f.listing});assert.equal(read.detail.careSnapshot.healthState,'unknown');}finally{await deleteApp(f.app);}});
test('actual Firestore concurrent care saves commit one revision and one revocation',{skip:!enabled},async()=>{const f=await fixture();try{await f.service.execute(host,'show',{operationId:randomUUID(),listingId:f.listing,expectedControlVersion:1});const input={petId:f.pet,expectedCareVersion:1,expectedIdentityVersion:1,expectedCustodyVersion:0,payload:care(),medicationNotes:''};const results=await Promise.allSettled([f.service.execute(host,'care',{...input,operationId:randomUUID()}),f.service.execute(host,'care',{...input,operationId:randomUUID()})]);assert.equal(results.filter(r=>r.status==='fulfilled').length,1);assert.equal((await f.db.doc('petCare/'+f.pet).get()).get('currentCareVersion'),2);assert(!(await f.db.doc('listingDiscovery/'+f.listing).get()).exists);}finally{await deleteApp(f.app);}});

test('source2 runtime requires both reports, finalizes once and never transfers private medication',{skip:!enabled},async()=>{
 const f=await fixture();try{
  const clock=Date.now(),runtime=new AdoptionRuntime(f.db),call=(a,command,values,expectedVersion)=>runtime.execute(a,'placement',{command,operationId:randomUUID(),expectedVersion,...values});
  await f.service.execute(host,'show',{operationId:randomUUID(),listingId:f.listing,expectedControlVersion:1});
  const offer=await runtime.read(applicant,'offerSource',{listingId:f.listing}),applicationId=randomUUID(),reservationId=randomUUID();
  await call(applicant,'submit',{applicationId,listingId:f.listing,sourceHash:offer.sourceHash,templateHash:offer.templateHash,answers:{care:'needs_discussion',routine:'Discuss daily care'},message:''},0);
  await call(host,'select',{applicationId,reservationId,reason:'Synthetic preparation'},1);
  await call(applicant,'acknowledge_preparation',{reservationId},1);
  const meeting=await call(host,'meeting',{reservationId,meeting:{utcMillis:clock+3600000,displayTimeZone:'Asia/Bangkok',location:'Synthetic meeting',notes:''}},2);
  const terms=await call(host,'terms',{reservationId,terms:{templateVersion:1,mode:'fictional_course',followupMode:'none_demo'}},3);
  for(const a of [host,applicant]){
   await call(a,'ack_meeting',{reservationId,meetingHash:meeting.hash},4);
   await call(a,'checklist',{reservationId,expectedOwnVersion:0,checklist:{entries:{known_needs:'yes',transport:'yes',supplies:'yes',responsibility:'yes',questions:'yes'},notes:''}},4);
   await call(a,'accept_terms',{reservationId,expectedOwnVersion:1,termsHash:terms.hash},4);
  }
  const ready=await runtime.read(host,'reservation',{reservationId});assert.equal(typeof ready.confirmationHash,'string');
  await call(host,'confirm',{reservationId,expectedOwnVersion:0,sourceHash:ready.confirmationHash,physicalEventReport:'given',reportedAt:null},4);
  assert(!(await f.db.doc('placements/'+reservationId).get()).exists);
  await call(applicant,'confirm',{reservationId,expectedOwnVersion:0,sourceHash:ready.confirmationHash,physicalEventReport:'received',reportedAt:null},4);
  assert((await f.db.doc('placements/'+reservationId).get()).exists);
  const inherited=await runtime.read(applicant,'pet',{petId:f.pet});assert.equal(inherited.privateCare,null);assert.equal(inherited.pet.editorUid,applicant.uid);
 }finally{await deleteApp(f.app);}
});
