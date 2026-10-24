import {shell,protectedMember,scopedRestriction,text,integer,fail,exact,ms} from './safety-common.mjs';

/** The ordinary course policy is separate from reviewer/Admin capabilities. */
export async function participationPolicy(w){
 const p=await w.get('participationPolicy/current');
 if(!p?.enabled)return null;
 if(p.enabled!==true||p.schemaVersion!==1||p.policyVersion!==1||p.mode!=='course_simplified'||p.phoneRequired!==false||p.publicProfileRequired!==false||p.requireVerifiedEmailForAuthoring!==true||p.sourceSchema!==3||!integer(p.termsVersion,1))fail('participation_policy_invalid');
 return p;
}
export async function ordinaryMember(w,a,{verified=false,scope=null,recovery=false,displayName=false}={}){
 await shell(w,a?.uid);if(a.disabled===true)fail('account_unavailable');
 const [m,s]=await Promise.all([w.get('communityMemberships/'+a.uid),w.get('securitySubjects/'+a.uid)]);
 if(!exact(m,['schemaVersion','uid','status','membershipVersion','createdAt','updatedAt'])||m.uid!==a.uid||m.schemaVersion!==1||!integer(m.membershipVersion,1)||!Number.isFinite(ms(m.createdAt))||!Number.isFinite(ms(m.updatedAt))||(!recovery&&m.status!=='active')||(recovery&&!['active','restricted'].includes(m.status)))fail('account_restricted');
 if(s&&(!exact(s,['status','roles','updatedAt'])||!Number.isFinite(ms(s.updatedAt))||!Array.isArray(s.roles)||s.roles.length>2||new Set(s.roles).size!==s.roles.length||s.roles.some(role=>!['member','admin'].includes(role))||(!recovery&&s.status!=='active')||(recovery&&!['active','restricted'].includes(s.status))))fail('account_restricted');
 if(verified&&a.emailVerified!==true&&a.email_verified!==true)fail('email_required');
 if(scope&&!recovery)await scopedRestriction(w,a.uid,scope);
 if(displayName){const profile=await w.get('users/'+a.uid+'/profileDraft/current');if(!text(profile?.displayName,80,1))fail('display_name_required');}
 return m;
}
export async function participantMember(w,a,options={}){
 if(await participationPolicy(w))return ordinaryMember(w,a,{verified:true,...options});
 return protectedMember(w,a);
}
export function overlay(w){return {get:p=>w.writes?.has(p)?w.writes.get(p):w.get(p),set:(p,d)=>w.set(p,d),delete:p=>w.delete(p)};}
