// Deployment-independent trusted domain logic. Only managed, authenticated adapters may call it.
// No service initialization, credentials, trigger registration or cloud deployment lives here.
import {createHash} from 'node:crypto';
export const HOUR=3_600_000, DAY=86_400_000, MAX_BYTES=5*1024*1024;
export const hash=value=>createHash('sha256').update(typeof value==='string'||Buffer.isBuffer(value)?value:JSON.stringify(value)).digest('hex');
export const fail=code=>{throw new Error(code);};
export const uuid=value=>typeof value==='string'&&/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(value);
const ms=value=>value instanceof Date?value.getTime():value?.toMillis?.()??NaN;
const exact=(value,keys)=>value&&Object.keys(value).sort().join('|')===keys.sort().join('|');
const own=(a,uid)=>a&&a.ownerUid===uid;
export class MediaService {
 constructor(store,{clock=Date.now}={}){this.store=store;this.clock=clock;}
 async issue(actor,input){
  if(!actor?.uid||actor.emailVerified!==true)fail('not_verified');
  if(!exact(input,['assetId','operationId','inputHash','purpose'])||!uuid(input.assetId)||!uuid(input.operationId)||!/^[0-9a-f]{64}$/.test(input.inputHash)||input.purpose!=='community_private')fail('invalid_intent');
  const uid=actor.uid,digest=hash(input),receiptPath=`mediaCommands/${uid}/receipts/${input.operationId}`,now=this.clock();
  return this.store.transaction(async tx=>{
   const receipt=await tx.get(receiptPath);if(receipt){if(receipt.inputHash!==digest||receipt.command!=='issue')fail('intent_conflict');return receipt;}
   const policy=await tx.get('mediaPolicy/current'),shell=await tx.get(`users/${uid}`),member=await tx.get(`communityMemberships/${uid}`),subject=await tx.get(`securitySubjects/${uid}`),quota=await tx.get(`mediaQuota/${uid}`),existing=await tx.get(`mediaAssets/${input.assetId}`);
   if(!policy?.enabled||policy.schemaVersion!==1||policy.policyVersion<1)fail('media_disabled');
   if(!exact(shell,['uid','schemaVersion','createdAt'])||shell.uid!==uid||shell.schemaVersion!==1||!Number.isFinite(ms(shell.createdAt))||!exact(member,['uid','schemaVersion','status','membershipVersion','createdAt','updatedAt'])||member.uid!==uid||member.status!=='active'||member.schemaVersion!==1||!Number.isInteger(member.membershipVersion)||member.membershipVersion<1||!Number.isFinite(ms(member.createdAt))||!Number.isFinite(ms(member.updatedAt))||(subject&&(!exact(subject,['status','roles','updatedAt'])||subject.status!=='active'||!Array.isArray(subject.roles)||!Number.isFinite(ms(subject.updatedAt)))))fail('not_ready');
   if(existing)fail('asset_conflict');
   const ledger=(quota?.grants??[]).filter(t=>ms(t)>now-DAY);if(ledger.length>=20)fail('daily_quota');
   if((quota?.outstanding??[]).length>4)fail('quota_corrupt');
   const outstanding=[];for(const id of quota?.outstanding??[]){const a=await tx.get(`mediaAssets/${id}`);if(a&&!['deleted','cancelled','rejected'].includes(a.state)&&ms(a.expiresAt)>now)outstanding.push(id);}
   if(outstanding.length>=4)fail('outstanding_quota');
   const time=new Date(now),expires=new Date(now+HOUR),stagingPath=`staging/${uid}/${input.assetId}/candidate`;
   const ticket={uid,assetId:input.assetId,operationId:input.operationId,purpose:input.purpose,stagingPath,issuedAt:time,expiresAt:expires,policyVersion:policy.policyVersion,maxBytes:MAX_BYTES,state:'issued',inputHash:input.inputHash};
   const asset={schemaVersion:1,ownerUid:uid,assetId:input.assetId,purpose:input.purpose,targetType:null,targetId:null,state:'issued',version:1,createdAt:time,expiresAt:expires,ticketId:input.assetId,inputHash:input.inputHash,originalGeneration:null,variants:{},rejectionCode:null};
   const result={command:'issue',inputHash:digest,assetId:input.assetId,outcome:{stagingPath,expiresAt:expires},serverCreatedAt:time};
   tx.set(`mediaTickets/${input.assetId}`,ticket);tx.set(`mediaAssets/${input.assetId}`,asset);tx.set(`mediaQuota/${uid}`,{grants:[...ledger,time],outstanding:[...outstanding,input.assetId]});tx.set(receiptPath,result);return result;
  });
 }
 // Port returns independently bounded, sanitized JPEG variants; untrusted client dimensions are ignored.
 // prepare is called outside a DB transaction. CAS prevents late/cancelled jobs exposing outputs.
 async finalize(assetId,generation,processor){
  if(!uuid(assetId)||typeof generation!=='string'||!/^\d+$/.test(generation))fail('invalid_generation');
  const now=this.clock();let lease;
  const initial=await this.store.transaction(async tx=>{
   const a=await tx.get(`mediaAssets/${assetId}`),t=await tx.get(`mediaTickets/${assetId}`),p=await tx.get('mediaPolicy/current');
   if(!a||!t)fail('unknown_asset');
   if(a.state==='ready'){if(a.originalGeneration!==generation)fail('generation_conflict');return a;}
   if(!p?.enabled||t.state!=='issued'||ms(t.expiresAt)<=now||['cancelled','cleanup_pending','deleted','rejected'].includes(a.state))fail('finalization_closed');
   if(a.state==='processing'&&ms(a.leaseUntil)>now)fail('lease_busy');
   if(a.originalGeneration&&a.originalGeneration!==generation)fail('generation_conflict');
   lease=a.version+1;const next={...a,state:'processing',version:lease,originalGeneration:generation,leaseUntil:new Date(now+60_000)};tx.set(`mediaAssets/${assetId}`,next);return next;
  });
  if(initial.state==='ready'){await processor.deleteOriginal({path:`staging/${initial.ownerUid}/${assetId}/candidate`,generation});return initial;}
  let variants;
  try {
   variants=await processor.sanitize({stagingPath:`staging/${initial.ownerUid}/${assetId}/candidate`,generation,maxBytes:MAX_BYTES,maxPixels:20_000_000,maxEdge:2048,inputHash:initial.inputHash,assetId,ownerUid:initial.ownerUid});
   if(!exact(variants,['display','thumbnail']))fail('invalid_variants');
   for(const [key,v] of Object.entries(variants))if(!exact(v,['path','generation','width','height','bytes'])||v.path!==`final/${initial.ownerUid}/${assetId}/${key}.jpg`||!/^\d+$/.test(v.generation)||!Number.isInteger(v.width)||!Number.isInteger(v.height)||v.width<1||v.height<1||Math.max(v.width,v.height)>2048||!Number.isInteger(v.bytes)||v.bytes<1||v.bytes>MAX_BYTES)fail('invalid_variants');
  }catch(error){
   await this.store.transaction(async tx=>{const a=await tx.get(`mediaAssets/${assetId}`);if(a?.version===lease&&a.state==='processing')tx.set(`mediaAssets/${assetId}`,{...a,state:'rejected',version:a.version+1,rejectionCode:'invalid_image',variants:{},leaseUntil:null});});
   throw error;
  }
  const result=await this.store.transaction(async tx=>{
   const a=await tx.get(`mediaAssets/${assetId}`),t=await tx.get(`mediaTickets/${assetId}`),p=await tx.get('mediaPolicy/current'),m=await tx.get(`communityMemberships/${initial.ownerUid}`),s=await tx.get(`securitySubjects/${initial.ownerUid}`);
   if(a?.version!==lease||a.state!=='processing'||t?.state!=='issued'||ms(t.expiresAt)<=this.clock()||!p?.enabled||m?.status!=='active'||(s&&s.status!=='active'))fail('finalization_revoked');
   const next={...a,state:'ready',version:a.version+1,expiresAt:new Date(this.clock()+DAY),variants,leaseUntil:null};
   tx.set(`mediaAssets/${assetId}`,next);tx.set(`mediaTickets/${assetId}`,{...t,state:'consumed'});return next;
  });
  // Generation-checked quarantine removal can be retried. Ready never grants staging reads.
  await processor.deleteOriginal({path:`staging/${initial.ownerUid}/${assetId}/candidate`,generation});return result;
 }
 async remove(actor,{assetId,operationId,expectedVersion}){
  if(!actor?.uid||!uuid(assetId)||!uuid(operationId)||!Number.isInteger(expectedVersion)||expectedVersion<1)fail('invalid_removal');
  const digest=hash({assetId,operationId,expectedVersion}),path=`mediaCommands/${actor.uid}/receipts/${operationId}`;
  return this.store.transaction(async tx=>{
   const r=await tx.get(path);if(r){if(r.command!=='remove'||r.inputHash!==digest)fail('intent_conflict');return r;}
   const a=await tx.get(`mediaAssets/${assetId}`),t=await tx.get(`mediaTickets/${assetId}`);
   if(!own(a,actor.uid))fail('not_owner');if(a.version!==expectedVersion)fail('version_conflict');
   if(a.targetType!==null||a.targetId!==null)fail('consumer_adapter_required');
   const next={...a,state:'cleanup_pending',version:a.version+1,variants:a.variants,leaseUntil:null};
   const receipt={command:'remove',inputHash:digest,assetId,outcome:{version:next.version,state:next.state},serverCreatedAt:new Date(this.clock())};
   tx.set(`mediaAssets/${assetId}`,next);if(t)tx.set(`mediaTickets/${assetId}`,{...t,state:'cancelled'});tx.set(path,receipt);return receipt;
  });
 }
 async cleanup(assetId,objects){
  // No attach adapter exists. Future adapters MUST reject cleanup_pending and atomically check this lease.
  const a=await this.store.transaction(async tx=>{
   const a=await tx.get(`mediaAssets/${assetId}`),hold=await tx.get(`mediaRetention/${assetId}`);
   if(!a||a.state==='deleted')return a;if(hold?.held||a.targetType!==null||a.targetId!==null)fail('retained');
   if(a.state!=='cleanup_pending'&&ms(a.expiresAt)>this.clock())fail('not_expired');
   const next={...a,state:'cleanup_pending',version:a.version+1,leaseUntil:null};tx.set(`mediaAssets/${assetId}`,next);return next;
  });
  if(!a||a.state==='deleted')return a;
  const paths=Object.values(a.variants);if(a.originalGeneration)paths.push({path:`staging/${a.ownerUid}/${assetId}/candidate`,generation:a.originalGeneration});
  for(const object of paths)await objects.deleteGeneration(object.path,object.generation);
  return this.store.transaction(async tx=>{const current=await tx.get(`mediaAssets/${assetId}`),hold=await tx.get(`mediaRetention/${assetId}`);if(current.version!==a.version||hold?.held||current.targetId!==null)fail('cleanup_conflict');const next={...current,state:'deleted',version:current.version+1,variants:{}};tx.set(`mediaAssets/${assetId}`,next);return next;});
 }
}
