// Read-only assertions against the exact isolated run, never the production database.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {managed}=require('./media-admin.cjs');
(async()=>{
 const root=path.resolve(__dirname,'../build/two-device');
 const receipt=JSON.parse(fs.readFileSync(path.join(root,'bridge-receipt.json')));
 assert.match(receipt.projectId,/^demo-anilove-two-device-[a-f0-9]{8}$/);
 const db=new(managed('@google-cloud/firestore').Firestore)({projectId:receipt.projectId,host:'127.0.0.1:8180',ssl:false});
 const read=async p=>(await db.doc(p).get()).data();const cases=[];
 for(const [name,c] of Object.entries(receipt.cases)){
  const d=await read('adoptions/'+c.adoptionId);assert(d,'case_missing');
  assert.equal(d.status,name==='success'?'FINALIZED':'CANCELLED_BEFORE_HANDOVER');assert.equal(d.blockingDispute,false);
  assert.equal((await read('adoptionFlowConversations/'+d.adoptionId)).state,'READ_ONLY');
  for(const uid of d.participantUids)assert.equal((await read('users/'+uid+'/adoptionObligations/'+d.adoptionId)).active,false);
  const gate=await read('petActivityGates/'+d.petId);assert.equal(gate.activeReservationId,null);
  let correctionCount=0;
  if(name==='success'){
   assert.equal(d.reportIds.length,10);assert.equal((await read('pets/'+d.petId)).editorUid,d.adopterUid);
   for(const id of d.reportIds){const p='monitoringCases/'+d.adoptionId+'/reports/'+id;const r=await read(p);assert.equal(r.status,'REVIEWED');assert.equal(r.revision,1);assert.equal((await read(p+'/revisions/v1')).uid,d.adopterUid);if(id==='D03'){const corrections=await db.collection(p+'/corrections').get();correctionCount=corrections.size;assert.equal(correctionCount,1);}}
  }else assert.equal((await read('pets/'+d.petId)).editorUid,d.hostUid);
  cases.push({name,status:d.status,chatReadOnly:true,obligationsCleared:true,reservationReleased:true,reviewedReports:d.reportIds.length,correctionCount});
 }
 const images=receipt.journal.filter(x=>x.operation==='media_read'&&x.bytes>0);
 const readers=new Set(images.map(x=>x.uid));assert.equal(readers.size,2,'Both participants must have read real image bytes');
 assert(receipt.journal.some(x=>x.operation==='denied'&&x.code==='pending_reconcile'));
 assert(receipt.journal.some(x=>x.operation==='denied'&&x.code==='not_ready'));
 assert.equal(receipt.journal.filter(x=>x.operation==='independent_resolution').length,1);
 const result={passed:true,isolated:true,cases,twoDistinctImageReaders:true,actualImageReadCount:images.length,uncertainCommitRecovered:true,disputeBlockedHandover:true,independentResolution:true};
 fs.writeFileSync(path.join(root,'integrity-audit.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));await db.terminate();
})().catch(e=>{console.error({auditFailed:true,assertion:e.message});process.exitCode=1;});
