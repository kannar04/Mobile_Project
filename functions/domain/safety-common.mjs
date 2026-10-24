import {canonical} from './adoption.mjs';
import {hash,fail,uuid} from './media.mjs';
export {fail,uuid};
export const digest=v=>hash(canonical(v));
export const exact=(v,keys)=>v&&Object.keys(v).sort().join('|')===[...keys].sort().join('|');
export const text=(v,max,min=0)=>typeof v==='string'&&v===v.trim()&&[...v].length>=min&&[...v].length<=max;
export const ms=v=>v instanceof Date?v.getTime():v?.toMillis?.()??NaN;
export const integer=(v,min=0)=>Number.isSafeInteger(v)&&v>=min;
export class Work {
 constructor(tx){this.tx=tx;this.cache=new Map();this.writes=new Map();}
 async get(path){if(!this.cache.has(path))this.cache.set(path,await this.tx.get(path));return this.cache.get(path);}
 set(path,data){this.writes.set(path,data);}delete(path){this.writes.set(path,null);}
 flush(){for(const[p,d]of this.writes)d===null?this.tx.delete(p):this.tx.set(p,d);}
}
export async function shell(w,uid){if(typeof uid!=='string'||!uid||uid.length>128||uid.includes('/'))fail('unauthenticated');const s=await w.get(`users/${uid}`);if(!exact(s,['uid','schemaVersion','createdAt'])||s.uid!==uid||s.schemaVersion!==1||!Number.isFinite(ms(s.createdAt)))fail('account_unavailable');}
export async function protectedMember(w,actor){await shell(w,actor?.uid);const s=await w.get(`securitySubjects/${actor.uid}`);if(actor.emailVerified!==true||actor.disabled===true||s?.status!=='active'||!Array.isArray(s.roles)||!s.roles.includes('member'))fail('protected_account_ineligible');return s;}
export async function capability(w,actor,name,{conflicts=[],reauth=false,now=Date.now()}={}){
 await protectedMember(w,actor);await scopedRestriction(w,actor.uid,'reviewer_access');const c=await w.get(`operatorCapabilities/${actor.uid}`);if(c?.schemaVersion!==1||c.active!==true||!integer(c.version,1)||!Array.isArray(c.capabilities)||!c.capabilities.includes(name)||conflicts.includes(actor.uid))fail('capability_or_conflict');
 if(reauth){const policy=await w.get('operatorPolicy/current');if(!integer(policy?.reauthMaxAgeSeconds,1)||policy.reauthMaxAgeSeconds>3600||!integer(actor.authTimeMillis)||actor.authTimeMillis>now||now-actor.authTimeMillis>policy.reauthMaxAgeSeconds*1000)fail('reauthentication_required');}return c;
}
export function party(record,uid){if(!record||![record.hostUid,record.applicantUid,record.caregiverUid,record.receiverUid,record.requesterUid].includes(uid))fail('not_participant');}
export async function policy(w,field){const p=await w.get('safetyRuntimePolicy/current');if(p?.schemaVersion!==1||p[field]!==true)fail('adapter_not_enabled');return p;}
export async function scopedRestriction(w,uid,scope){const d=await w.get(`accountRestrictionState/${uid}`);if(d&&(!integer(d.version)||!Array.isArray(d.scopes)||d.scopes.includes(scope)||d.scopes.includes('platform_access')))fail('scoped_restriction');}
export class Commands {
 constructor(store,receiptCollection,specs,{clock=Date.now}={}){this.store=store;this.receipts=receiptCollection;this.specs=specs;this.clock=clock;}
 async execute(actor,input){
  if(actor?.disabled===true)fail('unauthenticated');
  const keys=this.specs[input?.command];if(!keys||!exact(input,['command','operationId','expectedVersion',...keys])||!uuid(input.operationId)||!integer(input.expectedVersion))fail('invalid_intent');
  for(const[key,value]of Object.entries(input))if(/(Id|Uid)$/.test(key)&&value!==null&&(typeof value!=='string'||!value||value.length>200||value.includes('/')))fail('invalid_identifier');
  const frozen=structuredClone(input),inputDigest=digest(frozen);
  return this.store.transaction(async tx=>{const w=new Work(tx),now=this.clock();if(!integer(now))fail('server_clock');await shell(w,actor?.uid);const path=`${this.receipts}/${actor.uid}/operations/${frozen.operationId}`,prior=await w.get(path);if(prior){if(prior.inputDigest!==inputDigest)fail('intent_conflict');return prior.result;}
   const result=await this.command(w,actor,frozen,now);w.set(path,{schemaVersion:1,uid:actor.uid,operationId:frozen.operationId,command:frozen.command,inputDigest,baseVersion:frozen.expectedVersion,result,committedAt:new Date(now)});w.flush();return result;});
 }
}
export async function quota(w,path,uid,now,cap,spacing){const q=await w.get(path)||{schemaVersion:1,uid,version:0,grants:[],lastAcceptedAt:null};if(q.schemaVersion!==1||q.uid!==uid||!integer(q.version)||!Array.isArray(q.grants)||q.grants.length>cap||q.grants.some(d=>!Number.isFinite(ms(d))))fail('quota_repair_required');const grants=q.grants.filter(d=>ms(d)>now-86400000);if(grants.length>=cap||q.lastAcceptedAt&&now-ms(q.lastAcceptedAt)<spacing)fail('intake_limit');w.set(path,{...q,version:q.version+1,grants:[...grants,new Date(now)],lastAcceptedAt:new Date(now)});}
export async function currentPlacement(w,id,uid,{caregiver=false}={}){if(!uuid(id))fail('placement_id');const p=await w.get(`placements/${id}`);if(p?.schemaVersion!==1||p.state!=='completed'||p.placementId!==id)fail('placement_unavailable');party(p,uid);const pet=await w.get(`pets/${p.petId}`);if(!pet||pet.lifecycle!=='active'||caregiver&&pet.editorUid!==uid)fail('current_caregiver_changed');return {placement:p,pet};}
