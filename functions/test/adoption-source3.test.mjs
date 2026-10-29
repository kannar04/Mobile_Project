import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {setup,run,host,fields,now} from '../../firebase-tests/adoption-source2.test.mjs';
import {identity,claim} from '../../firebase-tests/doc-feedback.test.mjs';
import {AdoptionSourceService} from '../domain/adoption-source2.mjs';
import {currentBrowseSource} from '../domain/adoption-release.mjs';
import {Work} from '../domain/safety-common.mjs';
import {digest} from '../contract.mjs';
import {TERMS} from '../domain/course-terms.mjs';
const policy={schemaVersion:1,policyVersion:1,mode:'course_simplified',enabled:true,requireVerifiedEmailForAuthoring:true,phoneRequired:false,publicProfileRequired:false,termsVersion:1,sourceSchema:3};
const requirements={description:'Daily care',temperament:'Friendly',careRequirements:'Indoor home',adoptionRequirements:'Discuss the routine'};
export async function courseFixture(){
 const f=setup();f.service=new AdoptionSourceService(f.store,{clock:()=>now});
 f.store.set('participationPolicy/current',policy);
 f.store.set('adoptionBrowseRelease/current',{...f.store.get('adoptionBrowseRelease/current'),mode:'course_participation',allowedSourceSchema:3,applicationsEnabled:true});
 f.store.set('adoptionWorkflowPolicy/current',{...f.store.get('adoptionWorkflowPolicy/current'),writerSetVersion:3});
 f.store.docs.delete('securitySubjects/owner');
 f.store.set('communityMemberships/owner',{schemaVersion:1,uid:'owner',status:'active',membershipVersion:1,createdAt:new Date(now),updatedAt:new Date(now)});
 f.store.set('users/owner/profileDraft/current',{displayName:'Private owner'});
 const assetId=randomUUID();f.store.set('mediaAssets/'+assetId,{schemaVersion:2,assetId,ownerUid:'owner',purpose:'pet_photo',state:'ready',targetId:null,version:1,expiresAt:new Date(now+86400000)});
 await run(f,host,'registerPet',{petId:f.pet,identity,claim,mediaAssetIds:[assetId]});
 f.input={listingId:f.listing,petId:f.pet,expectedContentVersion:0,expectedControlVersion:0,fields,requirements,consent:true,participationConsent:{termsVersion:1,termsHash:digest(TERMS)}};
 return f;
}
async function browse(f){return f.store.transaction(tx=>currentBrowseSource(new Work(tx),f.listing));}
const fixture=courseFixture;
test('ordinary member publishes once with truthful declaration and no reviewer/public profile/phone',async()=>{
 const f=await fixture(),id=randomUUID();const r=await run(f,host,'publishCourse',f.input,id);
 assert.equal(r.status,'visible');assert.deepEqual(await run(f,host,'publishCourse',f.input,id),r);
 assert.equal((await browse(f)).reviewMode,'course_self_declared');
 assert.equal(f.store.get('petOfferAuthorities/'+f.pet).reviewMode,'self_declared_for_demo');
 assert.equal(f.store.get('adoptionListingRequirements/'+f.listing).version,1);
 assert.equal(f.store.get('securitySubjects/owner'),undefined);
 assert.equal([...f.store.docs.keys()].some(p=>p.startsWith('listingReviews/')),false);
 assert.equal(f.store.get('publicProfiles/owner'),undefined);
});
test('disabled rollout and wrong consent roll back every publication write',async()=>{
 const f=await fixture();f.store.set('adoptionWorkflowPolicy/current',{...f.store.get('adoptionWorkflowPolicy/current'),reviewCapacityVerified:false});
 await assert.rejects(run(f,host,'publishCourse',f.input),/publication_not_ready/);
 assert.equal(f.store.get('listings/'+f.listing),undefined);assert.equal(f.store.get('adoptionTerms/owner'),undefined);
 f.store.set('adoptionWorkflowPolicy/current',{...f.store.get('adoptionWorkflowPolicy/current'),reviewCapacityVerified:true});
 await assert.rejects(run(f,host,'publishCourse',{...f.input,participationConsent:{termsVersion:1,termsHash:'wrong'}}),/terms_changed/);
 assert.equal(f.store.get('adoptionTerms/owner'),undefined);
});
test('hide/restore uses current declaration and moderation hold defeats owner restore',async()=>{
 const f=await fixture();await run(f,host,'publishCourse',f.input);
 await run(f,host,'hide',{listingId:f.listing,expectedControlVersion:1});await assert.rejects(browse(f));
 await run(f,host,'show',{listingId:f.listing,expectedControlVersion:2});assert((await browse(f)).detail);
 await run(f,host,'hide',{listingId:f.listing,expectedControlVersion:3});
 f.store.set('courseModerationControls/listing:'+f.listing,{state:'held'});
 await assert.rejects(run(f,host,'show',{listingId:f.listing,expectedControlVersion:4}),/held/);
});
test('projection tampering and explicit account restrictions invalidate source',async()=>{
 const f=await fixture();await run(f,host,'publishCourse',f.input);const row=f.store.get('listingDiscovery/'+f.listing);
 f.store.set('listingDiscovery/'+f.listing,{...row,title:'Wrong'});await assert.rejects(browse(f),/source_changed/);
 f.store.set('listingDiscovery/'+f.listing,row);f.store.set('communityMemberships/owner',{...f.store.get('communityMemberships/owner'),status:'restricted'});
 await assert.rejects(browse(f),/account_restricted/);
});
test('current caregiver makes a fresh own declaration without inheriting the former host claim or photos',async()=>{const f=await fixture(),uid='receiver',oldClaim=structuredClone(f.store.get(`pets/${f.pet}/claims/owner`)),pet=f.store.get('pets/'+f.pet),a=f.store.get('petOfferAuthorities/'+f.pet),g=f.store.get('petActivityGates/'+f.pet),assetId=randomUUID();f.store.set('users/'+uid,{uid,schemaVersion:1,createdAt:new Date(now)});f.store.set('users/'+uid+'/profileDraft/current',{displayName:'Current caregiver'});f.store.set('communityMemberships/'+uid,{uid,schemaVersion:1,status:'active',membershipVersion:1,createdAt:new Date(now),updatedAt:new Date(now)});f.store.set('pets/'+f.pet,{...pet,editorUid:uid,custodyVersion:1});f.store.set('petActivityGates/'+f.pet,{...g,custodyVersion:1});f.store.set('petOfferAuthorities/'+f.pet,{...a,offerUid:null,status:'blocked',custodyVersion:1});f.store.set('mediaAssets/'+assetId,{schemaVersion:2,assetId,ownerUid:uid,purpose:'pet_photo',state:'ready',targetId:null,version:1,expiresAt:new Date(now+86400000)});const result=await f.service.execute({uid,emailVerified:true},'revisePet',{operationId:randomUUID(),petId:f.pet,expectedIdentityVersion:1,expectedClaimVersion:0,identity,claim,mediaAssetIds:[assetId]});assert.equal(result.status,'self_declared_for_demo');assert.equal(result.claimVersion,f.store.get(`pets/${f.pet}/claims/${uid}`).claimVersion);assert.equal(f.store.get('petOfferAuthorities/'+f.pet).offerUid,uid);assert.deepEqual(f.store.get(`pets/${f.pet}/claims/owner`),oldClaim);assert.deepEqual(f.store.get('petMedia/'+f.pet).assetIds,[assetId]);assert.equal(f.store.get('securitySubjects/'+uid),undefined);});
