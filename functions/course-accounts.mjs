import {getAuth} from 'firebase-admin/auth';
import {randomUUID} from 'node:crypto';
import {AccountDecisionService} from './domain/account-decisions.mjs';
import {Work,capability,exact,integer,fail} from './domain/safety-common.mjs';
/** Exact scoped account lookup and reversible platform restrictions; no Auth role/disable writes. */
export class CourseAccounts {
 constructor(db,store,{auth=getAuth()}={}){this.db=db;this.store=store;this.auth=auth;this.decisions=new AccountDecisionService(store);}
 async read(actor,c){
  if(!exact(c,['uid'])||typeof c.uid!=='string'||!c.uid||c.uid.length>128||c.uid.includes('/'))fail('exact_lookup_required');
  await this.store.transaction(tx=>capability(new Work(tx),actor,'account_lookup'));
  let user;try{user=await this.auth.getUser(c.uid);}catch{fail('account_unavailable');}
  return this.store.transaction(async tx=>{const w=new Work(tx);await capability(w,actor,'account_lookup');const path='adminAccountDirectory/'+user.uid,old=await w.get(path),alias=String(user.displayName??'').slice(0,80),status=user.disabled?'disabled':'active';
   if(old&&(!integer(old.directoryVersion,1)||old.uid!==user.uid))fail('directory_repair_required');const row=old&&old.alias===alias&&old.status===status?old:{...old,uid:user.uid,alias,status,directoryVersion:(old?.directoryVersion??0)+1};if(row!==old)w.set(path,row);
   const state=await w.get('accountRestrictionState/'+user.uid),decisions=[];if(state&&(!integer(state.version)||!Array.isArray(state.activeActions)||state.activeActions.length>64))fail('restriction_inventory_required');
   for(const id of state?.activeActions??[]){const d=await w.get('platformDecisions/'+id),control=await w.get('platformDecisionControls/'+id);if(!d||d.subjectUid!==user.uid||!control)fail('restriction_inventory_required');if(control.state==='active')decisions.push({decisionId:id,scope:d.scope,reason:d.reason,version:control.version});}
   w.set('adminAccountAccess/'+randomUUID(),{schemaVersion:1,actorUid:actor.uid,targetUid:user.uid,purpose:'scoped_account_moderation',recordedAt:new Date()});w.flush();return {account:{uid:row.uid,alias:row.alias,status:row.status,directoryVersion:row.directoryVersion},restrictionVersion:state?.version??0,decisions};
  });
 }
 async execute(actor,c){return this.decisions.execute(actor,c);}
}
