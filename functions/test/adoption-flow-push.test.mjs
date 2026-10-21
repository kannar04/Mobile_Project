import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {initializeApp,deleteApp} from 'firebase-admin/app';
import {getFirestore} from 'firebase-admin/firestore';
import {dispatchAdoptionPush} from '../adoption-flow-push.mjs';

const enabled=!!process.env.FIRESTORE_EMULATOR_HOST;

test('adoption push scans only pending work and retires undeliverable jobs',{skip:!enabled},async()=>{
 const app=initializeApp({projectId:'demo-adoption-push-'+randomUUID().slice(0,8)},randomUUID()),db=getFirestore(app),now=Date.now();
 try{
  await db.doc('adoptionLifecyclePolicy/current').set({enabled:true,pushEnabled:true});
  const batch=db.batch();
  for(let n=0;n<25;n++)batch.set(db.doc(`adoptionFlowOutbox/sent-${String(n).padStart(2,'0')}`),{state:'sent',createdAt:new Date(now)});
  batch.set(db.doc('adoptionFlowOutbox/pending'),{state:'pending',uid:'member',eventId:'event',createdAt:new Date(now)});
  await batch.commit();
  assert.deepEqual(await dispatchAdoptionPush(db,{clock:()=>now,sender:{send:async()=>assert.fail('no installation')}}),{scanned:1});
  assert.equal((await db.doc('adoptionFlowOutbox/pending').get()).get('attempts'),1);
  for(let n=1;n<10;n++)await dispatchAdoptionPush(db,{clock:()=>now+n,sender:{send:async()=>assert.fail('no installation')}});
  assert.equal((await db.doc('adoptionFlowOutbox/pending').get()).get('state'),'expired');
  assert.deepEqual(await dispatchAdoptionPush(db,{clock:()=>now+11,sender:{send:async()=>assert.fail('no installation')}}),{scanned:0});
 }finally{await db.terminate();await deleteApp(app);}
});
