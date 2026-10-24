import {getAuth} from 'firebase-admin/auth';
import {randomUUID} from 'node:crypto';
import {AccountDecisionService} from './domain/account-decisions.mjs';
import {Work,capability,exact,integer,text,uuid,fail,digest} from './domain/safety-common.mjs';

const ADMIN_CAPABILITIES=['account_admin','account_lookup','account_restrict','course_content_moderation','report_review'];
const validId=value=>typeof value==='string'&&value.length>0&&value.length<=128&&!value.includes('/');
const activeAdmin=row=>row?.schemaVersion===1&&row.active===true&&Array.isArray(row.capabilities)&&(row.capabilities.includes('account_admin')||(row.capabilities.includes('account_lookup')&&row.capabilities.includes('account_restrict')));

/** Bounded account lookup, reversible restrictions and trusted Admin delegation. */
export class CourseAccounts {
 constructor(db,store,{auth=getAuth(),clock=Date.now}={}){this.db=db;this.store=store;this.auth=auth;this.clock=clock;this.decisions=new AccountDecisionService(store,{clock});}
 async authorize(actor,{reauth=false}={}){return this.store.transaction(async tx=>{const w=new Work(tx);try{return await capability(w,actor,'account_admin',{reauth,now:this.clock()});}catch{await capability(w,actor,'account_lookup',{reauth,now:this.clock()});return capability(w,actor,'account_restrict',{reauth,now:this.clock()});}});}
 async userByQuery(query){
  if(query.includes('@')){try{return [await this.auth.getUserByEmail(query.toLowerCase())];}catch{return [];}}
  try{return [await this.auth.getUser(query)];}catch{}
  const docs=await this.db.collection('publicProfiles').orderBy('displayName').startAt(query).endAt(query+'\uf8ff').limit(20).get();if(!docs.size)return [];
  return (await this.auth.getUsers(docs.docs.map(d=>({uid:d.id})))).users;
 }
 async directory(user,actorUid){return this.store.transaction(async tx=>{const w=new Work(tx),path='adminAccountDirectory/'+user.uid,old=await w.get(path),alias=String(user.displayName??'').slice(0,80),status=user.disabled?'disabled':'active',aliasNormalized=alias.toLocaleLowerCase('vi');if(old&&(!integer(old.directoryVersion,1)||old.uid!==user.uid))fail('directory_repair_required');const row=old&&old.alias===alias&&old.status===status&&old.aliasNormalized===aliasNormalized?old:{...old,uid:user.uid,alias,aliasNormalized,status,directoryVersion:(old?.directoryVersion??0)+1};if(row!==old)w.set(path,row);w.set('adminAccountAccess/'+randomUUID(),{schemaVersion:1,actorUid,targetUid:user.uid,purpose:'scoped_account_moderation',recordedAt:new Date(this.clock())});w.flush();return row;});}
 async summary(user,actorUid){const row=await this.directory(user,actorUid),cap=(await this.db.doc('operatorCapabilities/'+user.uid).get()).data();return {uid:row.uid,alias:row.alias,email:String(user.email??''),status:row.status,directoryVersion:row.directoryVersion,admin:activeAdmin(cap),capabilityVersion:cap?.version??0};}
 async read(actor,c){
  await this.authorize(actor);
  if(exact(c,['query'])){if(typeof c.query!=='string'||c.query!==c.query.trim()||[...c.query].length<2||[...c.query].length>128)fail('exact_lookup_required');const users=await this.userByQuery(c.query),rows=[];for(const user of users.slice(0,20))rows.push(await this.summary(user,actor.uid));return {rows};}
  if(!exact(c,['uid'])||!validId(c.uid))fail('exact_lookup_required');let user;try{user=await this.auth.getUser(c.uid);}catch{fail('account_unavailable');}const account=await this.summary(user,actor.uid);
  return this.store.transaction(async tx=>{const w=new Work(tx),state=await w.get('accountRestrictionState/'+user.uid),decisions=[];if(state&&(!integer(state.version)||!Array.isArray(state.activeActions)||state.activeActions.length>64))fail('restriction_inventory_required');for(const id of state?.activeActions??[]){const d=await w.get('platformDecisions/'+id),control=await w.get('platformDecisionControls/'+id);if(!d||d.subjectUid!==user.uid||!control)fail('restriction_inventory_required');if(control.state==='active')decisions.push({decisionId:id,scope:d.scope,reason:d.reason,version:control.version});}return {account,restrictionVersion:state?.version??0,decisions};});
 }
 async setRole(actor,c){
  if(!exact(c,['command','operationId','expectedVersion','targetUid','reason'])||!['promote_admin','demote_admin'].includes(c.command)||!uuid(c.operationId)||!validId(c.targetUid)||!integer(c.expectedVersion)||!text(c.reason,500,1)||c.targetUid===actor.uid)fail('invalid_intent');await this.authorize(actor,{reauth:true});
  let target;try{target=await this.auth.getUser(c.targetUid);}catch{fail('account_unavailable');}if(target.disabled)fail('account_unavailable');const inputDigest=digest(c),now=this.clock();
  return this.store.transaction(async tx=>{const w=new Work(tx),receiptPath=`adminRoleReceipts/${actor.uid}/operations/${c.operationId}`,prior=await w.get(receiptPath);if(prior){if(prior.inputDigest!==inputDigest)fail('intent_conflict');return prior.result;}
   try{await capability(w,actor,'account_admin',{reauth:true,now});}catch{await capability(w,actor,'account_lookup',{reauth:true,now});await capability(w,actor,'account_restrict',{reauth:true,now});}
   const member=await w.get('communityMemberships/'+c.targetUid),subject=await w.get('securitySubjects/'+c.targetUid),current=await w.get('operatorCapabilities/'+c.targetUid);if(member?.status!=='active'||subject&&subject.status!=='active')fail('account_unavailable');if((current?.version??0)!==c.expectedVersion)fail('version_conflict');const promote=c.command==='promote_admin',wasAdmin=activeAdmin(current);if(promote===wasAdmin)fail('role_unchanged');
   if(!promote){const all=await w.tx.scan('operatorCapabilities',100),count=all.filter(activeAdmin).length;if(count<=1)fail('last_admin_required');}
   const before=Array.isArray(current?.capabilities)?current.capabilities:[],caps=promote?[...new Set([...before,...ADMIN_CAPABILITIES])]:before.filter(x=>!ADMIN_CAPABILITIES.includes(x)),version=(current?.version??0)+1,roles=[...new Set([...(Array.isArray(subject?.roles)?subject.roles:['member'])].filter(x=>x!=='admin').concat(promote?['admin']:[]))],at=new Date(now);
   w.set('operatorCapabilities/'+c.targetUid,{schemaVersion:1,uid:c.targetUid,active:caps.length>0,version,capabilities:caps,updatedAt:at});w.set('securitySubjects/'+c.targetUid,{status:'active',roles,updatedAt:at});const audit={schemaVersion:1,operationId:c.operationId,actorUid:actor.uid,targetUid:c.targetUid,action:promote?'promoted':'demoted',reason:c.reason,previousVersion:c.expectedVersion,resultVersion:version,recordedAt:at};w.set('adminAudit/'+c.operationId,audit);w.set(`users/${c.targetUid}/adminNotices/${c.operationId}`,{...audit,uid:c.targetUid,state:'unread'});const result={uid:c.targetUid,admin:promote,capabilityVersion:version};w.set(receiptPath,{schemaVersion:1,inputDigest,result,committedAt:at});w.flush();return result;
  });
 }
 async execute(actor,c){return ['promote_admin','demote_admin'].includes(c?.command)?this.setRole(actor,c):this.decisions.execute(actor,c);}
}
