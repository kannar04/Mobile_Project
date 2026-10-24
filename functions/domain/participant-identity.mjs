import {participationPolicy} from './participation.mjs';
import {fail} from './safety-common.mjs';
/** Actual Auth records are required when the old phone prerequisite is removed. */
export async function participantIdentity(w,uid,lookup){
 if(!await participationPolicy(w))return {uid,emailVerified:true};
 if(typeof lookup!=='function')fail('identity_lookup_unavailable');
 const user=await lookup(uid);if(!user||user.uid!==uid||user.disabled)fail('account_unavailable');
 return {uid,emailVerified:user.emailVerified===true,disabled:user.disabled===true};
}
