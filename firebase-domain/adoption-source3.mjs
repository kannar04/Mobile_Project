import {exact,text,integer,uuid,fail} from './safety-common.mjs';
import {participationPolicy,ordinaryMember,overlay} from './participation.mjs';
import {recordParticipationConsent} from './participation-consent.mjs';
import {validateCare} from './care.mjs';
import {sha} from './adoption-source2.mjs';
import {browseRelease} from './adoption-release-policy.mjs';
import {createHash} from 'node:crypto';
import {TERMS} from './course-terms.mjs';
const must=(v,c='source_changed')=>{if(!v)fail(c);};
const same=(a,b)=>sha(a)===sha(b);
export async function unheld(w,type,id){const c=await w.get('courseModerationControls/'+type+':'+id);must(!c||c.state==='clear','held');}
export async function courseDeclarationCurrent(w,pet,claim,a){
 must(await participationPolicy(w),'publication_not_ready');await unheld(w,'pet',pet.petId);
 must(a?.schemaVersion===3&&uuid(a.decisionId),'declaration_changed');
 const event=await w.get('petDeclarationEvents/'+a.decisionId);
 must(a.schemaVersion===3&&a.reviewMode==='self_declared_for_demo'&&a.status==='enabled'&&a.hold==='clear'&&a.offerUid===pet.editorUid&&a.identityVersion===pet.identityVersion&&a.custodyVersion===pet.custodyVersion&&claim?.reviewState==='self_declared_for_demo'&&claim.claimVersion===a.claimVersion&&claim.identityVersion===pet.identityVersion&&event?.schemaVersion===3&&event.type==='course_right_to_offer_attestation'&&event.declarantUid===pet.editorUid&&event.petId===pet.petId&&event.claimVersion===claim.claimVersion&&event.identityVersion===pet.identityVersion&&event.custodyVersion===pet.custodyVersion&&same(event.declaration,{basis:claim.basis,reportedCare:claim.reportedCare,explanation:claim.explanation}),'declaration_changed');
 return 'course_self_declared';
}
export async function declareCourse(w,actor,petId,operationId,at){
 must(await participationPolicy(w),'publication_not_ready');await ordinaryMember(w,actor,{verified:true,displayName:true,scope:'new_offering'});await unheld(w,'pet',petId);
 const [pet,claim,a,g]=await Promise.all([w.get('pets/'+petId),w.get(`pets/${petId}/claims/${actor.uid}`),w.get('petOfferAuthorities/'+petId),w.get('petActivityGates/'+petId)]);
 must(pet?.editorUid===actor.uid&&pet.lifecycle==='active'&&claim?.claimantUid===actor.uid&&claim.identityVersion===pet.identityVersion,'not_owner');must(g?.state==='none'&&g.activeReservationId===null&&g.activePlacementId===null,'active_obligation');must(!a||a.hold==='clear','held');
 must(['claimed_owner','authorized_caregiver','other_permission'].includes(claim.basis)&&claim.reportedCare===true&&text(claim.explanation,1000,claim.basis==='claimed_owner'?0:1),'declaration_required');
 if(a?.schemaVersion===3&&a.identityVersion===pet.identityVersion&&a.claimVersion===claim.claimVersion&&a.status==='enabled'){await courseDeclarationCurrent(w,pet,claim,a);return a;}
 const version=claim.claimVersion+1,declaration={basis:claim.basis,reportedCare:claim.reportedCare,explanation:claim.explanation};
 w.set(`pets/${petId}/claims/${actor.uid}`,{...claim,claimVersion:version,reviewState:'self_declared_for_demo',submittedRevisionId:operationId,submittedAt:at,updatedAt:at,lastOperationId:operationId});
 w.set(`pets/${petId}/claims/${actor.uid}/revisions/${operationId}`,{schemaVersion:3,petId,claimantUid:actor.uid,claimVersion:version,identityVersion:pet.identityVersion,declaration,operationId,createdAt:at});
 w.set('petDeclarationEvents/'+operationId,{schemaVersion:3,type:'course_right_to_offer_attestation',declarantUid:actor.uid,petId,claimVersion:version,identityVersion:pet.identityVersion,custodyVersion:pet.custodyVersion,declaration,createdAt:at,operationId});
 const next={schemaVersion:3,petId,offerUid:actor.uid,claimUid:actor.uid,claimVersion:version,identityVersion:pet.identityVersion,custodyVersion:pet.custodyVersion,authorityVersion:(a?.authorityVersion??0)+1,status:'enabled',hold:'clear',reviewMode:'self_declared_for_demo',decisionId:operationId,updatedAt:at,lastOperationId:operationId};w.set('petOfferAuthorities/'+petId,next);return next;
}
export async function courseSource(w,l,a,{offering=true}={}){
 must(await participationPolicy(w),'publication_not_ready');await ordinaryMember(w,{uid:l.ownerUid},{scope:'new_offering'});await unheld(w,'listing',l.listingId);
 const [pet,g,s,c,r,claim]=await Promise.all([w.get('pets/'+l.petId),w.get('petActivityGates/'+l.petId),w.get('petListingSlots/'+l.petId),w.get('petCare/'+l.petId),w.get(`listings/${l.listingId}/revisions/${l.activeRevisionId}`),w.get(`pets/${l.petId}/claims/${l.ownerUid}`)]);
 must(l.schemaVersion===2&&['draft','open'].includes(l.lifecycle)&&pet?.schemaVersion===1&&pet.lifecycle==='active'&&pet.editorUid===l.ownerUid&&s?.activeListingId===l.listingId&&g?.custodyVersion===pet.custodyVersion&&g.activePlacementId===null);
 must(offering?g.state==='none'&&g.activeReservationId===null:['none','reserved'].includes(g.state),'active_obligation');
 const claimReviewMode=await courseDeclarationCurrent(w,pet,claim,a),care=c?await w.get(`petCare/${l.petId}/revisions/v${c.currentCareVersion}`):null;
 const [media,identity]=await Promise.all([w.get('petMedia/'+l.petId),w.get(`pets/${l.petId}/identityRevisions/${pet.currentRevisionId}`)]);
 must(integer(l.contentVersion,1)&&integer(l.controlVersion,1)&&g.schemaVersion===1&&s?.schemaVersion===1&&integer(s.slotVersion,1)&&media?.schemaVersion===1&&media.ownerUid===l.ownerUid&&integer(media.version,1)&&r?.mediaVersion===media.version&&Array.isArray(media.assetIds)&&media.assetIds.length>0&&media.assetIds.length<=100&&media.assetIds.every(uuid)&&identity?.version===pet.identityVersion&&same(identity.identity,r.identitySnapshot),'source_changed');
 must(exact(r.identitySnapshot,['displayName','species','breed','sex','ageMonths','ageObservedAt','marks','marksUnknown','illustrationKey']),'source_changed');validateCare(r.careSnapshot,'',Date.now());
 must(r?.schemaVersion===2&&r.listingId===l.listingId&&r.ownerUid===l.ownerUid&&r.petId===l.petId&&r.contentVersion===l.contentVersion&&r.petIdentityVersion===pet.identityVersion&&r.custodyVersion===pet.custodyVersion&&care?.actorUid===l.ownerUid&&care.identityVersion===pet.identityVersion&&care.custodyVersion===pet.custodyVersion&&r.careVersion===c.currentCareVersion&&r.careDigest===sha(care.payload)&&same(r.careSnapshot,care.payload),'care_changed');
 const fingerprint={listingId:l.listingId,contentVersion:l.contentVersion,controlVersion:l.controlVersion,identityVersion:pet.identityVersion,custodyVersion:pet.custodyVersion,authorityVersion:a.authorityVersion,claimVersion:claim.claimVersion,slotVersion:s.slotVersion,careVersion:r.careVersion,careDigest:r.careDigest,revisionDigest:sha(r)};
 return {l,pet,a,g,s,c,r,care,fingerprint,claimReviewMode,hash:sha(fingerprint)};
}
export async function courseListingCurrent(w,l,pub){
 const a=await w.get('petOfferAuthorities/'+l.petId),b=await courseSource(w,l,a,{offering:false}),receipt=await w.get(`listingOwnerPublications/${l.listingId}/versions/v${l.contentVersion}`);
 must(pub?.schemaVersion===3&&pub.reviewMode==='course_self_declared'&&pub.approvedContentVersion===l.contentVersion&&receipt?.schemaVersion===3&&receipt.ownerUid===l.ownerUid&&receipt.contentVersion===l.contentVersion&&receipt.declarationId===a.decisionId&&receipt.operationId===pub.decisionId&&receipt.sourceFingerprint===pub.sourceFingerprint&&same({...b.fingerprint,controlVersion:0},{...pub.approvedSource,controlVersion:0}),'publication_changed');return b;
}
export async function publishCourse(w,actor,c,at){
 const release=await browseRelease(w);must(release.mode==='course_participation','publication_not_ready');must(await participationPolicy(w),'publication_not_ready');await ordinaryMember(w,actor,{verified:true,displayName:true,scope:'new_offering'});must(c.consent===true,'consent_required');
 const o=overlay(w),f=c.fields;must(exact(f,['title','description','regionCode','offerType','mediaRefs'])&&text(f.title,80,5)&&text(f.description,2000,1)&&[null,'north','central','south'].includes(f.regionCode)&&f.offerType==='adoption'&&Array.isArray(f.mediaRefs)&&!f.mediaRefs.length,'offer_fields');
 must(exact(c.requirements,['description','temperament','careRequirements','adoptionRequirements'])&&Object.values(c.requirements).every(v=>text(v,2000,1)),'listing_requirements_missing');await unheld(o,'listing',c.listingId);
 const [old,slot,pet,media,care]=await Promise.all([o.get('listings/'+c.listingId),o.get('petListingSlots/'+c.petId),o.get('pets/'+c.petId),o.get('petMedia/'+c.petId),o.get('petCare/'+c.petId)]);
 must(pet?.editorUid===actor.uid&&pet.lifecycle==='active','not_owner');must(slot&&[null,c.listingId].includes(slot.activeListingId),'active_listing');must(!old||old.ownerUid===actor.uid&&old.petId===c.petId&&['draft','open'].includes(old.lifecycle),'not_owner');
 must((old?.contentVersion??0)===c.expectedContentVersion&&(old?.controlVersion??0)===c.expectedControlVersion,'listing_changed');
 must(Array.isArray(media?.assetIds)&&media.ownerUid===actor.uid&&media.assetIds.length>0&&media.assetIds.length<=100,'photo_required');
 for(const id of media.assetIds){const a=await o.get('mediaAssets/'+id);must(uuid(id)&&a?.ownerUid===actor.uid&&a.purpose==='pet_photo'&&a.state==='attached'&&a.targetId==='pet:'+c.petId,'invalid_asset');}
 if(c.participationConsent)await recordParticipationConsent(o,actor,c.participationConsent,+at);
 const terms=await o.get('adoptionTerms/'+actor.uid),pp=await participationPolicy(o);must(terms?.termsVersion===pp.termsVersion&&terms.termsHash===createHash('sha256').update(JSON.stringify(TERMS)).digest('hex'),'terms_required');
 await declareCourse(o,actor,c.petId,c.operationId,at);
 let cr=care?await o.get(`petCare/${c.petId}/revisions/v${care.currentCareVersion}`):null;
 if(!care){const payload={healthState:'unknown',color:'',behaviorNotes:'',careNeeds:'',sterilization:'unknown',vaccinations:[]};cr={schemaVersion:1,petId:c.petId,careVersion:1,actorUid:actor.uid,source:'owner_report',payload,identityVersion:pet.identityVersion,custodyVersion:pet.custodyVersion,digest:sha(payload),operationId:c.operationId,reportedAt:at};o.set('petCare/'+c.petId,{schemaVersion:1,petId:c.petId,currentCareVersion:1,controlVersion:1,identityVersion:pet.identityVersion,custodyVersion:pet.custodyVersion,updatedAt:at,lastOperationId:c.operationId});o.set(`petCare/${c.petId}/revisions/v1`,cr);}
 must(cr?.actorUid===actor.uid&&cr.identityVersion===pet.identityVersion&&cr.custodyVersion===pet.custodyVersion,'care_changed');validateCare(cr.payload,'',+at);
 const ir=await o.get(`pets/${c.petId}/identityRevisions/${pet.currentRevisionId}`);must(ir?.identity&&ir.version===pet.identityVersion,'identity_changed');
 const n=c.expectedContentVersion+1,control=c.expectedControlVersion+1,l={schemaVersion:2,listingId:c.listingId,petId:c.petId,ownerUid:actor.uid,contentVersion:n,activeRevisionId:'v'+n,controlVersion:control,lifecycle:'open',visibility:'shown',createdAt:old?.createdAt??at,updatedAt:at,lastOperationId:c.operationId,closureReason:null,closedAt:null,archivedAt:null};
 must(!await o.get(`listings/${c.listingId}/revisions/v${n}`),'lineage_conflict');o.set('listings/'+c.listingId,l);o.set(`listings/${c.listingId}/revisions/v${n}`,{schemaVersion:2,listingId:c.listingId,petId:c.petId,ownerUid:actor.uid,contentVersion:n,editableFields:f,petIdentityVersion:pet.identityVersion,custodyVersion:pet.custodyVersion,identitySnapshot:ir.identity,careVersion:cr.careVersion,careDigest:sha(cr.payload),careSnapshot:cr.payload,mediaVersion:media.version,createdAt:at,operationId:c.operationId});
 if(slot.activeListingId===null)o.set('petListingSlots/'+c.petId,{...slot,activeListingId:c.listingId,slotVersion:slot.slotVersion+1,updatedAt:at});
 const a=await o.get('petOfferAuthorities/'+c.petId),b=await courseSource(o,l,a),previous=await o.get('listingPublication/'+c.listingId),version=(previous?.publicationVersion??0)+1,first=previous?.firstPublishedAt??at;
 o.set(`listingOwnerPublications/${c.listingId}/versions/v${n}`,{schemaVersion:3,listingId:c.listingId,petId:c.petId,ownerUid:actor.uid,contentVersion:n,declarationId:a.decisionId,sourceFingerprint:b.hash,operationId:c.operationId,createdAt:at});
 o.set('listingPublication/'+c.listingId,{schemaVersion:3,listingId:c.listingId,petId:c.petId,ownerUid:actor.uid,approvedContentVersion:n,decisionId:c.operationId,publicationVersion:version,state:'eligible',sourceFingerprint:b.hash,approvedSource:b.fingerprint,blockedReasonCode:null,firstPublishedAt:first,reviewMode:'course_self_declared'});
 o.set('adoptionListingRequirements/'+c.listingId,{schemaVersion:2,listingId:c.listingId,sourceVersion:n,version:(await o.get('adoptionListingRequirements/'+c.listingId))?.version+1||1,fields:c.requirements,assetIds:media.assetIds,updatedAt:at});
 writeCourseProjection(o,b,version,first,at);return {listingId:c.listingId,listingContentVersion:n,listingPublicationVersion:version,contentVersion:n,status:'visible',sourceSchema:3};
}
export function writeCourseProjection(w,b,version,first,at){
 const l=b.l,i=b.r.identitySnapshot,f=b.r.editableFields,region={north:'Miền Bắc',central:'Miền Trung',south:'Miền Nam'};
 const detail={schemaVersion:2,listingId:l.listingId,petId:l.petId,ownerUid:l.ownerUid,contentVersion:l.contentVersion,publicationVersion:version,firstPublishedAt:first,updatedAt:at,audience:'signed_in',state:'visible',title:f.title,description:f.description,regionCode:f.regionCode,identitySnapshot:i,careVersion:b.r.careVersion,careDigest:b.r.careDigest,careSnapshot:b.r.careSnapshot,bundledIllustrationKey:i.illustrationKey,availability:b.g.state==='reserved'?'reserved':'available',sourceFingerprint:b.hash};
 w.set('publicListings/'+l.listingId,detail);w.set('listingDiscovery/'+l.listingId,{schemaVersion:2,sourceSchemaVersion:b.a.schemaVersion===3?3:2,careDigest:b.r.careDigest,listingId:l.listingId,petId:l.petId,ownerUid:l.ownerUid,contentVersion:l.contentVersion,publicationVersion:version,audience:'signed_in',state:'visible',firstPublishedAt:first,updatedAt:at,title:f.title,petName:i.displayName,nameKey:i.displayName.normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[đĐ]/g,'d').toLowerCase().replace(/[^a-z0-9 ]/g,'').slice(0,80),speciesId:['cat','dog'].includes(i.species)?i.species:null,breedId:null,regionId:f.regionCode,regionLabel:region[f.regionCode]??'Chưa chọn khu vực',sex:i.sex,ageBand:i.ageMonths===null?'unknown':i.ageMonths<12?'baby':i.ageMonths<24?'young':i.ageMonths<96?'adult':'senior',offerType:'adoption',availability:detail.availability,careCues:{source:'owner_report',healthState:b.r.careSnapshot.healthState},bundledIllustrationKey:i.illustrationKey,mediaDescriptors:[]});
}
export async function courseBrowse(w,b,pub){
 await courseListingCurrent(w,b.l,pub);const [discovery,detail]=await Promise.all([w.get('listingDiscovery/'+b.l.listingId),w.get('publicListings/'+b.l.listingId)]),available=b.g.state==='none'&&b.g.activeReservationId===null?'available':b.g.state==='reserved'&&b.g.activeReservationId?'reserved':null;
 must(b.l.lifecycle==='open'&&b.l.visibility==='shown'&&pub.state==='eligible'&&available&&discovery?.schemaVersion===2&&discovery.sourceSchemaVersion===3&&Object.keys(discovery).length===26&&detail?.schemaVersion===2&&Object.keys(detail).length===20,'offer_unavailable');
 // Rebuild the complete expected safe projection from its immutable current source.
 const writes=new Map(),out={set:(p,d)=>writes.set(p,d)};writeCourseProjection(out,{...b,hash:pub.sourceFingerprint},pub.publicationVersion,pub.firstPublishedAt,detail.updatedAt);
 must(same(discovery,writes.get('listingDiscovery/'+b.l.listingId))&&same(detail,writes.get('publicListings/'+b.l.listingId)),'source_changed');
 return {discovery,detail,reviewMode:'course_self_declared'};
}
