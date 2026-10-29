// F11/F10 trusted domain kernels. No service, mobile Admin or publication activation.
import {scopedRestriction} from './safety-common.mjs';
import {hash,fail,uuid} from './media.mjs';
export const canonical=v=>v===null?'null':typeof v==='string'?'"'+v.replace(/["\\\x00-\x1f]/g,c=>c==='"'||c==='\\'?'\\'+c:'\\u'+c.charCodeAt(0).toString(16).padStart(4,'0'))+'"':Array.isArray(v)?'['+v.map(canonical).join(',')+']':typeof v==='object'?'{'+Object.keys(v).sort().map(k=>canonical(k)+':'+canonical(v[k])).join(',')+'}':JSON.stringify(v);
const digest=v=>hash(canonical(v));
const exact=(d,keys)=>d&&Object.keys(d).sort().join('|')===keys.sort().join('|');
const id=v=>typeof v==='string'&&/^[a-z0-9][a-z0-9_-]{0,79}$/.test(v);
const text=(v,min,max)=>typeof v==='string'&&v===v.trim()&&[...v].length>=min&&[...v].length<=max;
const paths={species:'catalogSpecies',breed:'catalogBreeds',region:'catalogRegions'};
export function validateCatalog(entry){
 const common=['schemaVersion','id','kind','labels','normalizedLabel','sortKey','state','version','updatedAt'];
 const keys=entry.kind==='breed'?[...common,'speciesId','variant']:entry.kind==='region'?[...common,'level','parentRegionId']:common;
 if(!paths[entry.kind]||!exact(entry,keys)||entry.schemaVersion!==1||!id(entry.id)||!exact(entry.labels,['vi','en'])||!text(entry.labels.vi,1,80)||!text(entry.labels.en,1,80)||!text(entry.normalizedLabel,1,80)||!text(entry.sortKey,1,100)||!['active','retired'].includes(entry.state)||!Number.isInteger(entry.version)||entry.version<1||Buffer.byteLength(JSON.stringify(entry))>2048)fail('catalog_schema');
 if(entry.kind==='breed'&&(!id(entry.speciesId)||!['known','unknown','mixed'].includes(entry.variant)))fail('breed_parent');
 if(entry.kind==='region'&&(entry.level!=='coarse'||entry.parentRegionId!==null&&!id(entry.parentRegionId)||entry.parentRegionId===entry.id))fail('region_scope');return entry;
}
export function selection(species,breed,region,retained=null){
 if(!species||species.kind!=='species'||!breed||breed.kind!=='breed'||breed.speciesId!==species.id||!region||region.kind!=='region')fail('catalog_selection');
 for(const e of [species,breed,region]){validateCatalog(e);if(e.state!=='active'&&!(retained?.[e.kind]?.id===e.id&&Number.isInteger(retained[e.kind].version)&&retained[e.kind].version>=1&&retained[e.kind].version<=e.version))fail('retired_selection');}
 return {speciesId:species.id,breedId:breed.id,regionId:region.id};
}
export class CatalogService {
 constructor(store,{clock=Date.now,authorize=async()=>fail('catalog_operator_not_authorized')}={}){this.store=store;this.clock=clock;this.authorize=authorize;}
 async execute(actor,{kind,entry,expectedVersion,operationId,reason}){
  if(!actor?.uid||!paths[kind]||entry.kind!==kind||!uuid(operationId)||!Number.isInteger(expectedVersion)||expectedVersion<0||!text(reason,1,500))fail('catalog_intent');
  const inputDigest=digest({kind,entry,expectedVersion,operationId,reason}),path=`${paths[kind]}/${entry.id}`,rp=`catalogOperations/${operationId}`;
  return this.store.transaction(async tx=>{
   await scopedRestriction(tx,actor.uid,'reviewer_access');const permission=await this.authorize(tx,actor,'catalog_admin');if(permission?.uid!==actor.uid||permission.capability!=='catalog_admin')fail('catalog_operator_not_authorized');
   const receipt=await tx.get(rp);if(receipt){if(receipt.actorUid!==actor.uid||receipt.inputDigest!==inputDigest)fail('intent_conflict');return receipt;}
   const old=await tx.get(path);if((old?.version??0)!==expectedVersion)fail('version_conflict');
   if(old&&(old.id!==entry.id||old.kind!==kind||kind==='breed'&&(old.speciesId!==entry.speciesId||old.variant!==entry.variant)||kind==='region'&&(old.level!==entry.level||old.parentRegionId!==entry.parentRegionId)))fail('immutable_catalog_identity');
   const next={...entry,schemaVersion:1,version:expectedVersion+1,updatedAt:new Date(this.clock())};validateCatalog(next);
   if(!old&&next.state!=='active')fail('catalog_create_active');
   const revision={...next,actorUid:actor.uid,reason,serverCreatedAt:new Date(this.clock()),operationId};if(Buffer.byteLength(JSON.stringify(revision))>2048)fail('catalog_payload');
   const namePath=`catalogLabelKeys/${digest([kind,kind==='breed'?entry.speciesId:kind==='region'?entry.parentRegionId:null,entry.normalizedLabel])}`;
   const name=await tx.get(namePath),nameOwner=name&&name.id!==entry.id?await tx.get(`${paths[kind]}/${name.id}`):null;
   if(nameOwner?.state==='active'&&nameOwner.normalizedLabel===entry.normalizedLabel)fail('catalog_duplicate');
   const variantPath=kind==='breed'&&entry.variant!=='known'?`catalogVariantKeys/${digest([entry.speciesId,entry.variant])}`:null;
   const variant=variantPath?await tx.get(variantPath):null,variantOwner=variant&&variant.id!==entry.id?await tx.get(`${paths[kind]}/${variant.id}`):null;if(variantOwner?.state==='active')fail('catalog_duplicate_variant');
   if(kind==='breed'){const parent=await tx.get(`catalogSpecies/${entry.speciesId}`);if(!parent||next.state==='active'&&parent.state!=='active')fail('breed_parent');}
   if(kind==='region'&&entry.parentRegionId!==null){const parent=await tx.get(`catalogRegions/${entry.parentRegionId}`);if(!parent||parent.level!=='coarse'||parent.parentRegionId!==null)fail('region_parent');}
   const record={schemaVersion:1,actorUid:actor.uid,operationId,inputDigest,kind,id:entry.id,expectedVersion,resultVersion:next.version,serverCreatedAt:new Date(this.clock())};
   tx.set(namePath,{schemaVersion:1,id:entry.id,kind});if(variantPath)tx.set(variantPath,{schemaVersion:1,id:entry.id,kind});tx.set(path,next);tx.set(`${path}/revisions/v${next.version}`,revision);tx.set(rp,record);return record;
  });
 }
}
export function availability(pet,authority,gate,slot,listing){
 if(!pet||!authority||!gate||!slot||!listing||pet.schemaVersion!==1||authority.schemaVersion!==1||gate.schemaVersion!==1||slot.schemaVersion!==1||pet.lifecycle!=='active'||authority.petId!==pet.petId||gate.petId!==pet.petId||slot.petId!==pet.petId||listing.petId!==pet.petId||authority.offerUid!==listing.ownerUid||authority.identityVersion!==pet.identityVersion||authority.custodyVersion!==pet.custodyVersion||gate.custodyVersion!==pet.custodyVersion||authority.status!=='enabled'||authority.hold!=='clear'||slot.activeListingId!==listing.listingId||!['draft','open'].includes(listing.lifecycle))return 'unavailable';
 if(gate.state==='none'&&gate.activeReservationId===null&&gate.activePlacementId===null)return 'available';
 if(gate.state==='reserved'&&uuid(gate.activeReservationId)&&gate.activePlacementId===null)return 'reserved';
 if(gate.state==='placed'&&uuid(gate.activePlacementId))return 'placed';return 'unavailable';
}
export class ReviewService {
 constructor(store,{clock=Date.now,authorize=async()=>fail('review_adapter_not_authorized')}={}){this.store=store;this.clock=clock;this.authorize=authorize;}
 async candidate(tx,listingId){
  const l=await tx.get(`listings/${listingId}`);if(l?.schemaVersion!==1||l.listingId!==listingId||!uuid(l.petId)||l.activeRevisionId!==`v${l.contentVersion}`)fail('unsupported_listing');
  await scopedRestriction(tx,l.ownerUid,'new_offering');
  const r=await tx.get(`listings/${listingId}/revisions/${l.activeRevisionId}`),p=await tx.get(`pets/${l.petId}`),a=await tx.get(`petOfferAuthorities/${l.petId}`),g=await tx.get(`petActivityGates/${l.petId}`),s=await tx.get(`petListingSlots/${l.petId}`),claim=await tx.get(`pets/${l.petId}/claims/${l.ownerUid}`),subject=await tx.get(`securitySubjects/${l.ownerUid}`);
  if(r?.schemaVersion!==1||r.listingId!==listingId||r.petId!==l.petId||r.ownerUid!==l.ownerUid||r.contentVersion!==l.contentVersion||r.petIdentityVersion!==p?.identityVersion||r.custodyVersion!==p?.custodyVersion||r.careVersion!==null||r.mediaVersion!==null||subject?.status!=='active'||p?.editorUid!==l.ownerUid||claim?.reviewState!=='accepted_for_demo'||claim.claimVersion!==a?.claimVersion||claim.identityVersion!==p?.identityVersion||!uuid(a?.decisionId)||availability(p,a,g,s,l)!=='available')fail('candidate_ineligible');
  const fingerprint={listingId,petId:l.petId,contentVersion:l.contentVersion,controlVersion:l.controlVersion,identityVersion:p.identityVersion,custodyVersion:p.custodyVersion,authorityVersion:a.authorityVersion,slotVersion:s.slotVersion,gateVersion:g.gateVersion,revisionDigest:digest(r.editableFields),careVersion:null,mediaVersion:null};
  return {listing:l,revision:r,fingerprint,digest:digest(fingerprint)};
 }
 async execute(actor,input){
  if(!actor?.uid||!uuid(input.listingId)||!uuid(input.operationId)||!['submit','cancel','assign','decide','revoke'].includes(input.command)||!Number.isInteger(input.contentVersion)||input.contentVersion<1||!Number.isInteger(input.requestVersion)||input.requestVersion<0)fail('review_intent');
  const keys=['command','listingId','operationId','contentVersion','requestVersion',...(input.command==='decide'?['decision','reason']:input.command==='revoke'?['reason','expectedPublicationVersion']:[])];if(!exact(input,keys))fail('review_intent');
  const inputDigest=digest(input),receiptPath=`listingReviewOperations/${input.operationId}`,requestPath=`listingReviewRequests/${input.listingId}/versions/v${input.contentVersion}`;
  return this.store.transaction(async tx=>{
   if(['assign','decide','revoke'].includes(input.command)){await scopedRestriction(tx,actor.uid,'reviewer_access');const permission=await this.authorize(tx,actor,'listing_review');if(permission?.uid!==actor.uid||permission.capability!=='listing_review')fail('review_adapter_not_authorized');}
   const prior=await tx.get(receiptPath);if(prior){if(prior.actorUid!==actor.uid||prior.inputDigest!==inputDigest)fail('intent_conflict');return prior;}
   const existing=await tx.get(requestPath);if((existing?.requestVersion??0)!==input.requestVersion)fail('version_conflict');
   if(input.command==='revoke'){
    const control=await tx.get(`listingPublication/${input.listingId}`);if(existing?.state!=='decided'||existing.ownerUid===actor.uid||control?.approvedContentVersion!==input.contentVersion||control.publicationVersion!==input.expectedPublicationVersion||!text(input.reason,1,1000))fail('revocation_denied');
    const now=new Date(this.clock()),receipt={schemaVersion:1,actorUid:actor.uid,operationId:input.operationId,inputDigest,listingId:input.listingId,command:'revoke',resultVersion:existing.requestVersion,serverCreatedAt:now};
    tx.set(`listingReviews/${input.listingId}/versions/v${input.contentVersion}/decisions/${input.operationId}`,{schemaVersion:1,reviewerUid:actor.uid,capability:'listing_review',expectedPublicationVersion:input.expectedPublicationVersion,decision:'revoke',reason:input.reason,sourceFingerprint:existing.sourceFingerprint,serverCreatedAt:now,operationId:input.operationId});
    tx.set(`listingPublication/${input.listingId}`,{...control,approvedContentVersion:null,publicationVersion:control.publicationVersion+1,state:'blocked',blockedReasonCode:'review_revoked'});for(const path of ['listingDiscovery','publicListings','mediaAccess'])tx.delete(`${path}/${input.listingId}`);tx.set(receiptPath,receipt);return receipt;
   }
   if(input.command==='cancel'){
    if(existing?.ownerUid!==actor.uid||!['queued','assigned'].includes(existing.state))fail('cancel_denied');
    const result={...existing,state:'cancelled',requestVersion:existing.requestVersion+1};const receipt={schemaVersion:1,actorUid:actor.uid,operationId:input.operationId,inputDigest,listingId:input.listingId,command:'cancel',resultVersion:result.requestVersion,serverCreatedAt:new Date(this.clock())};tx.set(requestPath,result);tx.set(receiptPath,receipt);return receipt;
   }
   const candidate=await this.candidate(tx,input.listingId);if(candidate.fingerprint.contentVersion!==input.contentVersion)fail('stale_candidate');
   if(input.command==='submit'&&(actor.emailVerified!==true||candidate.listing.ownerUid!==actor.uid||existing))fail('submit_denied');
   if(input.command!=='submit'&&(existing?.ownerUid===actor.uid||existing?.revisionDigest!==candidate.fingerprint.revisionDigest||existing?.sourceFingerprint!==candidate.digest))fail('stale_or_self_review');
   const now=new Date(this.clock());let next;
   if(input.command==='submit')next={schemaVersion:1,listingId:input.listingId,ownerUid:actor.uid,petId:candidate.listing.petId,contentVersion:input.contentVersion,revisionDigest:candidate.fingerprint.revisionDigest,identityVersion:candidate.fingerprint.identityVersion,custodyVersion:candidate.fingerprint.custodyVersion,careVersion:null,mediaVersion:null,sourceFingerprint:candidate.digest,submittedAt:now,state:'queued',requestVersion:1,assignedReviewerUid:null};
   else if(input.command==='assign'){if(existing.state!=='queued')fail('request_not_queued');next={...existing,state:'assigned',requestVersion:existing.requestVersion+1,assignedReviewerUid:actor.uid};}
   else{
    if(existing.state!=='assigned'||existing.assignedReviewerUid!==actor.uid||!['approve','reject'].includes(input.decision)||!text(input.reason,1,1000))fail('decision_denied');
    const control=await tx.get(`listingPublication/${input.listingId}`);next={...existing,state:'decided',requestVersion:existing.requestVersion+1};
    tx.set(`listingReviews/${input.listingId}/versions/v${input.contentVersion}/decisions/${input.operationId}`,{schemaVersion:1,reviewerUid:actor.uid,capability:'listing_review',expectedRequestVersion:existing.requestVersion,decision:input.decision,reason:input.reason,sourceFingerprint:candidate.digest,serverCreatedAt:now,operationId:input.operationId});
    // Approval is PRIVATE. No compatible source writer/publication authorization has been activated.
    tx.set(`listingPublication/${input.listingId}`,{schemaVersion:1,listingId:input.listingId,petId:existing.petId,ownerUid:existing.ownerUid,approvedContentVersion:input.decision==='approve'?input.contentVersion:null,decisionId:input.operationId,publicationVersion:(control?.publicationVersion??0)+1,state:'hidden',blockedReasonCode:'publication_adapter_required'});
    for(const path of ['listingDiscovery','publicListings','mediaAccess'])tx.delete(`${path}/${input.listingId}`);
   }
   const receipt={schemaVersion:1,actorUid:actor.uid,operationId:input.operationId,inputDigest,listingId:input.listingId,command:input.command,resultVersion:next.requestVersion,serverCreatedAt:now};tx.set(requestPath,next);tx.set(receiptPath,receipt);return receipt;
  });
 }
}
