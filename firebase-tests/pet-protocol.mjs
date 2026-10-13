import { doc,runTransaction,serverTimestamp,getDocFromServer } from 'firebase/firestore';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
export const identity=()=>({displayName:'Synthetic pet',species:'cat',breed:'',sex:'unknown',ageMonths:null,ageObservedAt:null,marks:'',marksUnknown:true,illustrationKey:'default'});
export const declaration=()=>({basis:'claimed_owner',reportedCare:true,explanation:''});
export function receipt(uid,pet,operation,type,input,base,result,role){return {schemaVersion:1,ownerUid:uid,operationId:operation,petId:pet,type,role,baseIdentityVersion:base.i,baseClaimVersion:base.c,baseAuthorityVersion:base.a,result:{identityVersion:result.i,claimVersion:result.c,authorityVersion:result.a},input,status:'committed',recordedAt:serverTimestamp()};}
export function registration(uid,pet=randomUUID(),op=randomUUID(),input={identity:identity(),claim:declaration()}){
  const now=serverTimestamp(),base=`pets/${pet}`;
  return {pet,op,input,writes:new Map([
    [base,{schemaVersion:1,petId:pet,registrarUid:uid,editorUid:uid,identityVersion:1,currentRevisionId:op,custodyVersion:0,lifecycle:'active',createdAt:now,updatedAt:now,lastOperationId:op}],
    [`${base}/identityRevisions/${op}`,{schemaVersion:1,petId:pet,version:1,actorUid:uid,identity:input.identity,operationId:op,createdAt:now}],
    [`${base}/claims/${uid}`,{schemaVersion:1,petId:pet,claimantUid:uid,claimVersion:1,identityVersion:1,...input.claim,reviewState:'draft',submittedRevisionId:null,submittedAt:null,createdAt:now,updatedAt:now,lastOperationId:op}],
    [`petOfferAuthorities/${pet}`,{schemaVersion:1,petId:pet,offerUid:null,claimUid:uid,claimVersion:1,identityVersion:1,authorityVersion:0,status:'blocked',hold:'clear',decisionId:null,custodyVersion:0,updatedAt:now,lastOperationId:op}],
    [`petListingSlots/${pet}`,{schemaVersion:1,petId:pet,activeListingId:null,slotVersion:0,updatedAt:now}],
    [`petActivityGates/${pet}`,{schemaVersion:1,petId:pet,state:'none',activeReservationId:null,activePlacementId:null,gateVersion:0,custodyVersion:0,updatedAt:now}],
    [`users/${uid}/petReceipts/${op}`,receipt(uid,pet,op,'register_pet',input,{i:0,c:0,a:0},{i:1,c:1,a:0},'registrar')]
  ])};
}
export async function register(db,uid,r=registration(uid)) {return runTransaction(db,async tx=>{
  const receiptRef=doc(db,`users/${uid}/petReceipts/${r.op}`),before=await tx.get(receiptRef);
  if(before.exists()){assert.equal(before.data().type,'register_pet');assert.equal(before.data().petId,r.pet);assert.deepEqual(before.data().input,r.input,'conflict');return r;}
  assert.equal((await tx.get(doc(db,`pets/${r.pet}`))).exists(),false,'conflict');
  for(const [path,data] of r.writes)tx.set(doc(db,path),data);return r;
},{maxAttempts:3}).catch(async error=>{const observed=await getDocFromServer(doc(db,`users/${uid}/petReceipts/${r.op}`));if(observed.exists()){assert.equal(observed.data().petId,r.pet);assert.deepEqual(observed.data().input,r.input,'conflict');return r;}throw error;});}
export async function revise(db,uid,pet,base,input=identity(),op=randomUUID()) {return runTransaction(db,async tx=>{
  const pRef=doc(db,`pets/${pet}`),p=(await tx.get(pRef)).data(),c=(await tx.get(doc(db,`pets/${pet}/claims/${uid}`))).data(),a=(await tx.get(doc(db,`petOfferAuthorities/${pet}`))).data();
  const old=await tx.get(doc(db,`users/${uid}/petReceipts/${op}`));if(old.exists()){assert.deepEqual(old.data().input,{identity:input},'conflict');assert.equal(old.data().baseIdentityVersion,base);return;}
  assert.equal(p.identityVersion,base,'conflict');
  tx.set(pRef,{...p,identityVersion:base+1,currentRevisionId:op,lastOperationId:op,updatedAt:serverTimestamp()});
  tx.set(doc(db,`pets/${pet}/identityRevisions/${op}`),{schemaVersion:1,petId:pet,version:base+1,actorUid:uid,identity:input,operationId:op,createdAt:serverTimestamp()});
  tx.set(doc(db,`users/${uid}/petReceipts/${op}`),receipt(uid,pet,op,'revise_identity',{identity:input},{i:base,c:c.claimVersion,a:a.authorityVersion},{i:base+1,c:c.claimVersion,a:a.authorityVersion},'editor'));
},{maxAttempts:3});}
export async function claimCommand(db,uid,pet,type,baseClaim=1,op=randomUUID(),newDeclaration=null){return runTransaction(db,async tx=>{
  const p=(await tx.get(doc(db,`pets/${pet}`))).data(),ref=doc(db,`pets/${pet}/claims/${uid}`),c=(await tx.get(ref)).data(),aRef=doc(db,`petOfferAuthorities/${pet}`),a=(await tx.get(aRef)).data();
  const declared=newDeclaration??{basis:c.basis,reportedCare:c.reportedCare,explanation:c.explanation},existing=await tx.get(doc(db,`users/${uid}/petReceipts/${op}`));
  if(existing.exists()){assert.deepEqual(existing.data().input,{claim:declared},'conflict');assert.equal(existing.data().baseClaimVersion,baseClaim);return;}
  assert.equal(c.claimVersion,baseClaim,'conflict');let resultAuthority=a.authorityVersion;
  const next={...c,...declared,claimVersion:baseClaim+1,identityVersion:p.identityVersion,reviewState:type==='withdraw_claim'?'withdrawn':type==='submit_claim'?'submitted':'draft',submittedRevisionId:type==='submit_claim'?op:null,submittedAt:type==='submit_claim'?serverTimestamp():null,lastOperationId:op,updatedAt:serverTimestamp()};
  if(type==='submit_claim')tx.set(doc(db,`pets/${pet}/claims/${uid}/revisions/${op}`),{schemaVersion:1,petId:pet,claimantUid:uid,claimVersion:next.claimVersion,identityVersion:p.identityVersion,declaration:declared,operationId:op,createdAt:serverTimestamp()});
  else if(a.claimUid===uid&&a.status==='enabled'){resultAuthority++;tx.set(aRef,{...a,status:'suspended',authorityVersion:resultAuthority,lastOperationId:op,updatedAt:serverTimestamp()});}
  tx.set(ref,next);tx.set(doc(db,`users/${uid}/petReceipts/${op}`),receipt(uid,pet,op,type,{claim:declared},{i:p.identityVersion,c:baseClaim,a:a.authorityVersion},{i:p.identityVersion,c:next.claimVersion,a:resultAuthority},'claimant'));
},{maxAttempts:3});}
export async function suspend(db,uid,pet,baseAuthority,op=randomUUID()){return runTransaction(db,async tx=>{const ref=doc(db,`petOfferAuthorities/${pet}`),a=(await tx.get(ref)).data();assert.equal(a.authorityVersion,baseAuthority,'conflict');tx.set(ref,{...a,status:'suspended',authorityVersion:baseAuthority+1,updatedAt:serverTimestamp(),lastOperationId:op});tx.set(doc(db,`users/${uid}/petReceipts/${op}`),receipt(uid,pet,op,'suspend_offering',{}, {i:a.identityVersion,c:a.claimVersion,a:baseAuthority},{i:a.identityVersion,c:a.claimVersion,a:baseAuthority+1},'offer_actor'));},{maxAttempts:3});}
export async function challenge(db,uid,pet,statement='Synthetic concern',op=randomUUID()){return runTransaction(db,async tx=>{const existing=await tx.get(doc(db,`users/${uid}/petReceipts/${op}`));if(existing.exists()){assert.deepEqual(existing.data().input,{statement},'conflict');return op;}tx.set(doc(db,`users/${uid}/petChallenges/${op}`),{schemaVersion:1,ownerUid:uid,operationId:op,targetPetId:pet,statement,challengeVersion:1,status:'received',response:'',submittedAt:serverTimestamp()});tx.set(doc(db,`users/${uid}/petReceipts/${op}`),receipt(uid,pet,op,'submit_challenge',{statement},{i:0,c:0,a:0},{i:0,c:0,a:0},'reporter'));return op;},{maxAttempts:3});}
