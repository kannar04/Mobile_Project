import {FieldPath} from 'firebase-admin/firestore';
import {Work,exact,uuid,fail} from './domain/safety-common.mjs';
import {browseRelease,browseMember} from './domain/adoption-release-policy.mjs';
import {currentBrowseSource} from './domain/adoption-release.mjs';
import {sha} from './domain/adoption-source2.mjs';
const deny=(ok)=>{if(!ok)fail('invalid_intent');};
export async function browseRead(runtime,actor,op,c){
 const release=await runtime.store.transaction(async tx=>{const w=new Work(tx);await browseMember(w,actor.uid);return browseRelease(w);});
 if(op==='discoveryWindow'){
  deny(exact(c,['ids'])&&Array.isArray(c.ids)&&c.ids.length<=20&&new Set(c.ids).size===c.ids.length&&c.ids.every(uuid));const rows=[];
  for(const id of c.ids){try{rows.push((await runtime.store.transaction(async tx=>{const w=new Work(tx);await browseMember(w,actor.uid);return currentBrowseSource(w,id);})).discovery);}catch(e){if(!['offer_unavailable','source_changed','care_changed','review_required','claim_review_unavailable','self_review','reviewer_required','protected_account_ineligible','scoped_restriction','active_obligation','held','declaration_changed','publication_changed','account_restricted'].includes(e.message))throw e;}}
  return {rows,cursor:null};
 }
 if(op==='discoveryDetail'||op==='publicDetail'){
  deny(exact(c,['listingId'])&&uuid(c.listingId));
  return runtime.store.transaction(async tx=>{const w=new Work(tx);await browseMember(w,actor.uid);const r=await currentBrowseSource(w,c.listingId);return op==='publicDetail'?{detail:r.detail,photoAssetIds:(await w.get(`petMedia/${r.detail.petId}`))?.assetIds??[],isOwner:r.detail.ownerUid===actor.uid,reviewMode:r.reviewMode,applicationsEnabled:r.applicationsEnabled}:{row:r.discovery};});
 }
 deny(exact(c,['prefix','availability','filters','own','cursor'])&&typeof c.prefix==='string'&&/^[a-z0-9 ]{0,32}$/.test(c.prefix)&&['available','reserved','any'].includes(c.availability)&&typeof c.own==='boolean'&&c.filters&&typeof c.filters==='object'&&!Array.isArray(c.filters));
 const allowed=['speciesId','breedId','regionId','ageBand','sex','offerType'];for(const[k,v]of Object.entries(c.filters)){deny(allowed.includes(k)&&typeof v==='string'&&/^[a-z][a-z0-9_\-]{0,63}$/.test(v));if(k==='sex')deny(['unknown','male','female'].includes(v));if(k==='ageBand')deny(['unknown','baby','young','adult','senior'].includes(v));if(k==='offerType')deny(v==='adoption');}deny(!c.filters.breedId||c.filters.speciesId);
 if(c.filters.breedId){const [species,breed]=await Promise.all([runtime.document('catalogSpecies/'+c.filters.speciesId),runtime.document('catalogBreeds/'+c.filters.breedId)]);deny(species?.state==='active'&&breed?.state==='active'&&breed.speciesId===c.filters.speciesId);}
 const order=c.prefix?'nameKey':'firstPublishedAt',direction=c.prefix?'asc':'desc',signature=sha({uid:actor.uid,prefix:c.prefix,availability:c.availability,filters:c.filters,own:c.own});
 let q=runtime.db.collection('listingDiscovery').where('audience','==','signed_in').where('state','==','visible');
 q=c.availability==='any'?q.where('availability','in',['available','reserved']):q.where('availability','==',c.availability);
 if(c.own)q=q.where('ownerUid','==',actor.uid);for(const[k,v]of Object.entries(c.filters))q=q.where(k,'==',v);
 if(c.prefix){const end=c.prefix.slice(0,-1)+String.fromCharCode(c.prefix.charCodeAt(c.prefix.length-1)+1);q=q.where(order,'>=',c.prefix).where(order,'<',end);}
 q=q.orderBy(order,direction).orderBy(FieldPath.documentId(),direction).limit(20);
 if(c.cursor!==null){deny(exact(c.cursor,['id','signature'])&&uuid(c.cursor.id)&&c.cursor.signature===signature);const row=await runtime.db.doc('listingDiscovery/'+c.cursor.id).get();deny(row.exists&&row.get('audience')==='signed_in'&&row.get('state')==='visible'&&(!c.own||row.get('ownerUid')===actor.uid)&&['available','reserved'].includes(row.get('availability'))&&(c.availability==='any'||row.get('availability')===c.availability)&&Object.entries(c.filters).every(([k,v])=>row.get(k)===v)&&(!c.prefix||row.get('nameKey')?.startsWith(c.prefix)));q=q.startAfter(row);}
 const candidates=await q.get(),rows=[];
 for(const row of candidates.docs){try{const current=await runtime.store.transaction(async tx=>{const w=new Work(tx);await browseMember(w,actor.uid);return currentBrowseSource(w,row.id);});if(current.discovery.contentVersion===row.get('contentVersion')&&current.discovery.publicationVersion===row.get('publicationVersion')&&(!c.own||current.discovery.ownerUid===actor.uid))rows.push(current.discovery);}catch(e){if(!['offer_unavailable','offer_unavailable','source_changed','care_changed','review_required','claim_review_unavailable','self_review','reviewer_required','protected_account_ineligible','scoped_restriction','active_obligation','held','declaration_changed','publication_changed','account_restricted'].includes(e.message))throw e;}}
 return {rows,cursor:candidates.size===20?{id:candidates.docs.at(-1).id,signature}:null,scanned:candidates.size,applicationsEnabled:release.applicationsEnabled};
}
