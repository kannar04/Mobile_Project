// F22 protected, explicit bounded materialization. No deployed background worker.
import {canonical} from './adoption.mjs';
import {hash,uuid,fail} from './media.mjs';
export class HistoryMaterializer {
 constructor(store,{authorize=async()=>fail('history_adapter_not_authorized'),clock=Date.now}={}){this.store=store;this.authorize=authorize;this.clock=clock;}
 async execute(actor,input){
  if(!actor?.uid||!input||Object.keys(input).sort().join('|')!=='operationId|sourceId|sourceType|sourceVersion'||!['application','placement'].includes(input.sourceType)||!uuid(input.sourceId)||!uuid(input.operationId)||!Number.isSafeInteger(input.sourceVersion)||input.sourceVersion<1)fail('history_intent');
  const frozen=structuredClone(input),inputDigest=hash(canonical(frozen));
  return this.store.transaction(async tx=>{
   if(await this.authorize(tx,actor,'history_materialize')!==true)fail('history_adapter_not_authorized');
   const path=`historyMaterialization/${frozen.sourceType}_${frozen.sourceId}_v${frozen.sourceVersion}`,old=await tx.get(path);
   if(old){if(old.sourceType!==frozen.sourceType||old.sourceId!==frozen.sourceId||old.sourceVersion!==frozen.sourceVersion)fail('history_lineage');return old.result;}
   const source=await tx.get(`${frozen.sourceType==='application'?'applications':'placements'}/${frozen.sourceId}`);
   if(!source||source.schemaVersion!==1||!source.hostUid||!source.applicantUid||source.hostUid===source.applicantUid||!uuid(source.petId)||!uuid(source.listingId))fail('history_source');
   const version=frozen.sourceType==='application'?source.applicationVersion:source.sourceOutcomeVersion;
   if(version!==frozen.sourceVersion)fail('history_source_changed');
   const states=['pending','under_review','selected','preparing','cancellation_requested','withdrawn','rejected','cancelled','completed','closed_not_selected'];
   if(frozen.sourceType==='application'&&!states.includes(source.status))fail('history_source');
   if(frozen.sourceType==='placement'){
    const event=await tx.get(`petCustodyEvents/${source.petId}/events/${source.eventId}`);
    if(source.state!=='completed'||source.placementId!==frozen.sourceId||!event||event.placementId!==source.placementId||event.fromUid!==source.hostUid||event.toUid!==source.applicantUid||event.newCustodyVersion!==event.oldCustodyVersion+1)fail('history_source');
   }
   const id=`${frozen.sourceType}_${frozen.sourceId}`,parties=[[source.hostUid,'Host'],[source.applicantUid,frozen.sourceType==='application'?'applicant':'recipient']],existing=[];
   for(const[uid]of parties){if(uid.includes('/'))fail('history_source');existing.push(await tx.get(`users/${uid}/adoptionHistory/${id}`));}
   const created=frozen.sourceType==='application'?source.submittedAt:source.recordedCompletedAt;
   if(!created||!(created instanceof Date||typeof created.toMillis==='function'))fail('history_source');
   const now=new Date(this.clock());
   for(let n=0;n<parties.length;n++){
    const[uid,role]=parties[n],prior=existing[n];
    if(prior&&(prior.sourceType!==frozen.sourceType||prior.sourceId!==frozen.sourceId||prior.uid!==uid||prior.role!==role||prior.petId!==source.petId||prior.listingId!==source.listingId))fail('history_lineage');
    if(prior&&prior.sourceOutcomeVersion>=version)continue;
    tx.set(`users/${uid}/adoptionHistory/${id}`,{schemaVersion:1,uid,recordId:id,sourceType:frozen.sourceType,sourceId:frozen.sourceId,petId:source.petId,listingId:source.listingId,role,recordCreatedAt:prior?.recordCreatedAt??created,sourceOutcomeVersion:version,outcome:frozen.sourceType==='application'?source.status:'completed',updatedAt:now});
   }
   const result={recordId:id,sourceVersion:version};tx.set(path,{schemaVersion:1,sourceType:frozen.sourceType,sourceId:frozen.sourceId,sourceVersion:version,inputDigest,state:'complete',result,recordedAt:now});return result;
  });
 }
}
