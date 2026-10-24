import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {initializeApp,deleteApp} from 'firebase-admin/app';
import {getFirestore} from 'firebase-admin/firestore';
import {dispatchAdoptionPush} from '../adoption-flow-push.mjs';
import {dispatchActivity} from '../activity-notifications.mjs';

const enabled=process.env.FIRESTORE_EMULATOR_HOST==='127.0.0.1:8186';
const OUTBOX_ROWS=7,LISTING_ROWS=20,PROFILE_ROWS=20;

test('isolated clone measures foreground and recovery read amplification',{skip:!enabled},async()=>{
 const project='demo-anilove-isolation',app=initializeApp({projectId:project},'read-budget'),db=getFirestore(app),now=Date.now();
 try{
  await db.doc('adoptionLifecyclePolicy/current').set({enabled:true,pushEnabled:true});
  const batch=db.batch();
  for(let i=0;i<OUTBOX_ROWS;i++)batch.set(db.doc(`adoptionFlowOutbox/pending-${i}`),{state:'pending',uid:`member-${i}`,eventId:`event-${i}`,createdAt:new Date(now),attempts:0});
  for(let i=0;i<LISTING_ROWS;i++)batch.set(db.doc(`listingDiscovery/listing-${String(i).padStart(2,'0')}`),{audience:'signed_in',state:'visible',availability:'available',firstPublishedAt:new Date(now-i),authorUid:`member-${i}`});
  for(let i=0;i<PROFILE_ROWS;i++)batch.set(db.doc(`publicProfiles/member-${i}`),{schemaVersion:1,displayName:`Synthetic member ${i}`});
  await batch.commit();

  const recovery=await dispatchAdoptionPush(db,{clock:()=>now,sender:{send:async()=>assert.fail('fixture has no installation')}});
  assert.equal(recovery.scanned,OUTBOX_ROWS);
  const activityRecovery=await dispatchActivity(db,{clock:()=>now,sender:{send:async()=>assert.fail('fixture has no activity installation')}});
  assert.equal(activityRecovery.scanned,0);
  const listings=await db.collection('listingDiscovery').where('audience','==','signed_in').where('state','==','visible').where('availability','==','available').orderBy('firstPublishedAt','desc').limit(20).get();
  assert.equal(listings.size,LISTING_ROWS);
  let profileDocuments=0;for(const row of listings.docs){const profile=await db.doc(`publicProfiles/${row.get('authorUid')}`).get();if(profile.exists)profileDocuments++;}
  assert.equal(profileDocuments,PROFILE_ROWS);

  const pulsesPerForegroundHour=Math.floor(3600/25);
  const measured={
   environment:{project,emulatorHost:process.env.FIRESTORE_EMULATOR_HOST,productionTouched:false,syntheticDataOnly:true},
   fixture:{outboxPending:OUTBOX_ROWS,listingPage:LISTING_ROWS,distinctAuthors:PROFILE_ROWS},
   observed:{adoptionRecoveryQueryDocuments:recovery.scanned,activityRecoveryQueryDocuments:activityRecovery.scanned,listingQueryDocuments:listings.size,profileDocuments},
   estimates:{
    adoptionRecoveryReadsPerRun:1+OUTBOX_ROWS+OUTBOX_ROWS,
    adoptionRecoveryReadsPerDayHourly:(1+OUTBOX_ROWS+OUTBOX_ROWS)*24,
    adoptionRecoveryReadsPerDayLegacyMinute:(1+OUTBOX_ROWS+OUTBOX_ROWS)*1440,
    listingReadsPerForegroundHour:LISTING_ROWS*pulsesPerForegroundHour,
    uncachedAuthorReadsPerForegroundHour:PROFILE_ROWS*pulsesPerForegroundHour,
    combinedListingAndAuthorReadsPerForegroundHour:(LISTING_ROWS+PROFILE_ROWS)*pulsesPerForegroundHour
   },
   patchedEstimates:{
    automaticListingReadsPerIdleForegroundHour:0,
    automaticAuthorReadsPerIdleForegroundHour:0,
    initialListingAndAuthorDocumentsPerScreenEntry:LISTING_ROWS+PROFILE_ROWS,
    activityEmptyRecoveryRunsPerDay:24,
    activityEmptyRecoveryRunsPerDayLegacy:1440,
    idleForegroundRecurringReductionPercent:100,
    activityRecoveryRunReductionPercent:Number(((1-24/1440)*100).toFixed(2))
   },
   assumptions:[
    'Firestore bills returned query documents and missing point reads; emulator itself does not expose billing counters.',
    'The adoption estimate includes one policy read, the pending query results, and one missing pushUserBindings point read per synthetic job.',
    'Legacy foreground estimates use the removed 25-second BrowsePulse cadence and a full 20-document page.',
    'Patched foreground sources refresh on screen entry or explicit invalidation and do not poll while idle.',
    'Listener reconnects, rules-dependent reads, retries, and image Storage traffic are excluded.'
   ]
  };
  await mkdir('../build',{recursive:true});await writeFile('../build/read-budget-isolation.json',JSON.stringify(measured,null,2));
  console.log(JSON.stringify(measured));
 }finally{await db.terminate();await deleteApp(app);}
});
