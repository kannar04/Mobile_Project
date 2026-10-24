import {randomUUID} from 'node:crypto';
import {fail,uuid} from './media.mjs';
// Explicit trusted course operation, not a scheduler. Store.pageApplications must
// execute listingId equality + documentId ASC + startAfter cursor + limit20.
export class ApplicationClosureWorker {
 constructor(store,service,{authorize=async()=>fail('closure_worker_not_authorized')}={}){this.store=store;this.service=service;this.authorize=authorize;}
 async page(actor,placementId){
  if(!uuid(placementId))fail('closure_id');
  const checkpoint=await this.store.transaction(async tx=>{
   if(await this.authorize(tx,actor,'application_close')!==true)fail('closure_worker_not_authorized');
   const work=await tx.get(`applicationClosureWork/${placementId}`),outcome=work?await tx.get(`listingOutcome/${work.listingId}`):null;
   if(!work||work.schemaVersion!==1||!outcome?.finalized||outcome.placementId!==placementId||outcome.outcomeVersion!==work.outcomeVersion)fail('closure_lineage');return {...work,selectedApplicationId:outcome.selectedApplicationId};
  });
  if(checkpoint.state==='complete')return {state:'complete',count:0};
  if(typeof this.store.pageApplications!=='function')fail('closure_query_adapter_unavailable');
  const rows=await this.store.pageApplications({listingId:checkpoint.listingId,after:checkpoint.cursor,limit:20,order:'documentId_asc'});
  if(!Array.isArray(rows)||rows.length>20||rows.some((r,i)=>!uuid(r.applicationId)||r.listingId!==checkpoint.listingId||!Number.isSafeInteger(r.applicationVersion)||r.applicationId<=(i?rows[i-1].applicationId:checkpoint.cursor??'')))fail('closure_page');
  for(const a of rows){
   if(a.applicationId===checkpoint.selectedApplicationId)continue;
   const result=await this.service.execute(actor,{command:'close_application',operationId:randomUUID(),expectedVersion:a.applicationVersion,applicationId:a.applicationId,placementId});
   // The selected application is already complete; never submit it for closure.
   if(!result)fail('closure_result');
  }
  return this.store.transaction(async tx=>{
   if(await this.authorize(tx,actor,'application_close')!==true)fail('closure_worker_not_authorized');
   const work=await tx.get(`applicationClosureWork/${placementId}`),outcome=await tx.get(`listingOutcome/${checkpoint.listingId}`);
   if(!work||work.cursor!==checkpoint.cursor||work.outcomeVersion!==checkpoint.outcomeVersion||!outcome?.finalized||outcome.placementId!==placementId)fail('closure_checkpoint_conflict');
   const state=rows.length<20?'complete':'pending',cursor=rows.length?rows.at(-1).applicationId:work.cursor;
   tx.set(`applicationClosureWork/${placementId}`,{...work,cursor,state});return {state,count:rows.length,cursor};
  });
 }
}
