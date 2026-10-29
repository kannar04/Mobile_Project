import {createHash} from 'node:crypto';
import {TERMS} from './course-terms.mjs';
import {exact,fail} from './safety-common.mjs';
import {participationPolicy,ordinaryMember} from './participation.mjs';
export async function recordParticipationConsent(w,a,c,now){
 const p=await participationPolicy(w);if(!p||!exact(c,['termsVersion','termsHash'])||c.termsVersion!==p.termsVersion||c.termsHash!==createHash('sha256').update(JSON.stringify(TERMS)).digest('hex'))fail('terms_changed');
 await ordinaryMember(w,a,{verified:true,displayName:true});
 const ref='adoptionTerms/'+a.uid,old=await w.get(ref);if(old?.termsVersion===p.termsVersion&&old.termsHash===c.termsHash)return;
 w.set(ref,{schemaVersion:2,termsVersion:c.termsVersion,termsHash:c.termsHash,acceptedAt:new Date(now)});
}
