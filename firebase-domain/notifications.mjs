import {randomBytes,randomUUID,timingSafeEqual} from 'node:crypto';
import {hash,fail,uuid,DAY} from './media.mjs';
const ms=v=>v instanceof Date?v.getTime():v?.toMillis?.()??NaN;
const equal=(a,b)=>typeof a==='string'&&typeof b==='string'&&a.length===b.length&&timingSafeEqual(Buffer.from(a),Buffer.from(b));
const shell=(s,uid)=>s?.uid===uid&&s.schemaVersion===1&&Object.keys(s).length===3&&Number.isFinite(ms(s.createdAt));
export class NotificationService {
 constructor(store,{clock=Date.now}={}){this.store=store;this.clock=clock;}
 async deriveComment({writerUid,postId,commentId,operationId}){
  if(!writerUid||!uuid(postId)||!uuid(commentId)||!uuid(operationId))fail('invalid_source');
  return this.derive(async tx=>{
   const post=await tx.get(`communityPosts/${postId}`),feed=await tx.get(`communityFeed/${postId}`),comment=await tx.get(`communityPosts/${postId}/comments/${commentId}`),revision=await tx.get(`communityPosts/${postId}/comments/${commentId}/revisions/v1`),receipt=await tx.get(`users/${writerUid}/communityOperations/${operationId}`);
   if(post?.schemaVersion!==2||post.lifecycle!=='active'||post.visibility!=='visible'||post.moderatorHold!=='clear'||feed?.schemaVersion!==2||feed.contentVersion!==post.contentVersion||feed.authorUid!==post.authorUid||feed.state!=='visible'||feed.audience!=='community'||comment?.schemaVersion!==2||comment.postId!==postId||comment.state!=='visible'||comment.authorUid!==writerUid||revision?.schemaVersion!==2||revision.postId!==postId||revision.state!=='visible'||revision.commentVersion!==1||revision.operationId!==operationId||revision.authorUid!==writerUid||revision.commentId!==commentId||receipt?.schemaVersion!==2||receipt.ownerUid!==writerUid||receipt.command!=='add_comment'||receipt.postId!==postId||receipt.targetId!==commentId||receipt.operationId!==operationId||receipt.status!=='committed'||receipt.result?.targetVersion!==1||receipt.base?.contentVersion!==revision.sourceContentVersion||post.authorUid===writerUid)fail('ineligible_source');
   return {recipientUid:post.authorUid,kind:'comment',routeType:'post',targetId:postId,sourceId:commentId,sourceVersion:1,operationId,committedAt:receipt.committedAt,templateKey:'comments_on_post',adapterVersion:'comment_v1'};
  });
 }
 async deriveOwnIntake({uid,reportId,operationId}){
  if(!uid||!uuid(reportId)||!uuid(operationId))fail('invalid_source');
  return this.derive(async tx=>{
   const receipt=await tx.get(`users/${uid}/reportOperations/${operationId}`),status=await tx.get(`users/${uid}/reportStatus/${reportId}`);
   if(receipt?.schemaVersion!==1||receipt.ownerUid!==uid||receipt.reportId!==reportId||receipt.operationId!==operationId||receipt.command!=='submit'||receipt.resultVersion!==1||status?.schemaVersion!==1||status.reportId!==reportId||status.status!=='received')fail('ineligible_source');
   return {recipientUid:uid,kind:'own_intake',routeType:'own_report',targetId:reportId,sourceId:reportId,sourceVersion:1,operationId,committedAt:receipt.committedAt,templateKey:'report_received',adapterVersion:'own_intake_v1'};
  });
 }
 async derive(resolve){
  const now=this.clock();return this.store.transaction(async tx=>{
   const policy=await tx.get('notificationPolicy/current');if(!policy?.enabled||policy.schemaVersion!==1)fail('notifications_disabled');
   const event=await resolve(tx),uid=event.recipientUid;
   if(!policy.allowedAdapterVersions?.includes(event.adapterVersion))fail('adapter_disabled');
   const watermark=ms(policy.activationWatermark),committed=ms(event.committedAt);
   if(!Number.isFinite(watermark)||!Number.isFinite(committed)||committed<watermark||committed>now||committed<now-7*DAY)fail('source_age');
   const key=hash([event.kind,event.sourceId,event.sourceVersion,event.operationId,uid]),sourcePath=`notificationSources/${key}`,prior=await tx.get(sourcePath);
   if(prior)return prior;
   if(!shell(await tx.get(`users/${uid}`),uid))fail('recipient_missing');
   const day=new Date(now).toISOString().slice(0,10),quotaPath=`notificationQuota/${uid}/days/${day}`,quota=await tx.get(quotaPath)??{inboxNewCount:0,pushAttemptCount:0},prefs=await tx.get(`users/${uid}/notificationPreferences/main`);
   const bucket=Math.floor(committed/600_000),groupKey=event.kind==='comment'?hash(['comment',event.targetId,uid,bucket]):key;
   let itemId=groupKey,itemPath=`users/${uid}/notificationInbox/${itemId}`,old=await tx.get(itemPath);
   const overflow=!old&&quota.inboxNewCount>=50;
   if(overflow){itemId=hash(['summary',uid,day]);itemPath=`users/${uid}/notificationInbox/${itemId}`;old=await tx.get(itemPath);}
   const row=old?{...old,updatedAt:new Date(now),itemVersion:old.itemVersion+1}:{schemaVersion:1,recipientUid:uid,kind:overflow?'summary':event.kind,routeType:overflow?'inbox':event.routeType,targetId:overflow?'main':event.targetId,sourceVersion:event.sourceVersion,templateKey:overflow?'daily_activity':event.templateKey,createdAt:new Date(now),updatedAt:new Date(now),expiresAt:new Date(now+30*DAY),itemVersion:1,state:'active',readAt:null,dismissedAt:null};
   const pushEligible=event.kind==='comment'&&!overflow&&!old&&policy.pushEnabled===true&&prefs?.pushEnabled===true&&prefs.commentsPush===true;
   // Bindings are deliberately not inferred from untrusted token records. Dispatcher claims current ones.
   const source={sourceType:event.kind,sourceId:event.sourceId,sourceVersion:event.sourceVersion,operationId:event.operationId,sourceCommittedAt:event.committedAt,recipientUid:uid,adapterVersion:event.adapterVersion,outcome:overflow?'summary':'inbox',itemId,createdAt:new Date(now),expiresAt:new Date(now+31*DAY)};
   tx.set(sourcePath,source);tx.set(itemPath,row);tx.set(quotaPath,{...quota,inboxNewCount:quota.inboxNewCount+Number(!old&&!overflow)});
   if(pushEligible)tx.set(`pushJobs/${key}`,{itemId,uid,sourceEventKey:key,status:'queued',attemptCount:0,notBefore:new Date(now),expiresAt:new Date(now+DAY),leaseVersion:0,leaseUntil:null,installationId:null,bindingEpoch:null,providerCode:null,messageId:null});
   return source;
  });
 }
 // Sender port performs data-only delivery. No HTTP-v1 credentials are accepted by this module.
 async send(jobId,installationId,sender){
  const now=this.clock(),lease=await this.store.transaction(async tx=>{
   const job=await tx.get(`pushJobs/${jobId}`);if(!job)fail('unknown_job');
   if(['provider_accepted','permanent_failure','suppressed','expired'].includes(job.status))return null;
   if(ms(job.expiresAt)<=now||job.attemptCount>=5){tx.set(`pushJobs/${jobId}`,{...job,status:'expired'});return null;}
   if(ms(job.notBefore)>now||(job.status==='leased'&&ms(job.leaseUntil)>now))fail('lease_busy');
   const binding=await tx.get(`pushInstallations/${installationId}`),policy=await tx.get('notificationPolicy/current'),prefs=await tx.get(`users/${job.uid}/notificationPreferences/main`),source=await tx.get(`notificationSources/${job.sourceEventKey}`),item=await tx.get(`users/${job.uid}/notificationInbox/${job.itemId}`),tokenOwner=binding?await tx.get(`pushTokenOwners/${binding.tokenHash}`):null;
   const day=new Date(now).toISOString().slice(0,10),qp=`notificationQuota/${job.uid}/days/${day}`,quota=await tx.get(qp)??{inboxNewCount:0,pushAttemptCount:0};
   const current=binding?.state==='active'&&binding.uid===job.uid&&ms(binding.expiresAt)>now&&tokenOwner?.installationId===installationId&&tokenOwner.uid===job.uid&&tokenOwner.bindingEpoch===binding.bindingEpoch;
   const post=item?.routeType==='post'?await tx.get(`communityPosts/${item.targetId}`):null,feed=item?.routeType==='post'?await tx.get(`communityFeed/${item.targetId}`):null,comment=item?.routeType==='post'&&source?.sourceId?await tx.get(`communityPosts/${item.targetId}/comments/${source.sourceId}`):null;
   const sourceEligible=source?.sourceType==='comment'&&source.recipientUid===job.uid&&ms(source.sourceCommittedAt)>=now-7*DAY&&ms(source.sourceCommittedAt)<=now&&ms(source.sourceCommittedAt)>=ms(policy?.activationWatermark)&&policy?.allowedAdapterVersions?.includes('comment_v1')&&post?.schemaVersion===2&&post.authorUid===job.uid&&post.lifecycle==='active'&&post.visibility==='visible'&&post.moderatorHold==='clear'&&feed?.schemaVersion===2&&feed.contentVersion===post.contentVersion&&feed.state==='visible'&&feed.audience==='community'&&comment?.schemaVersion===2&&comment.state==='visible';
   if(!policy?.enabled||!policy.pushEnabled||!prefs?.pushEnabled||!prefs.commentsPush||!current||!sourceEligible||item?.state!=='active'||ms(item.expiresAt)<=now||quota.pushAttemptCount>=20){tx.set(`pushJobs/${jobId}`,{...job,status:'suppressed'});return null;}
   // Serialize by installation; lease lives on the binding as well as the job.
   if(ms(binding.sendLeaseUntil)>now)fail('binding_busy');
   const next={...job,status:'leased',installationId,bindingEpoch:binding.bindingEpoch,attemptCount:job.attemptCount+1,leaseVersion:job.leaseVersion+1,leaseUntil:new Date(now+60_000)};
   tx.set(`pushJobs/${jobId}`,next);tx.set(`pushInstallations/${installationId}`,{...binding,sendLeaseUntil:next.leaseUntil,sendLeaseJob:jobId});tx.set(qp,{...quota,pushAttemptCount:quota.pushAttemptCount+1});
   return {job:next,binding,data:{schemaVersion:'1',uid:job.uid,installationId,bindingEpoch:String(binding.bindingEpoch),itemId:job.itemId,expiresAt:String(ms(job.expiresAt))}};
  });if(!lease)return {outcome:'not_sent'};
  let response;try{response=await sender.sendData(lease.binding.token,lease.data);}catch{response={code:'retryable_failure'};}
  return this.store.transaction(async tx=>{
   const job=await tx.get(`pushJobs/${jobId}`),binding=await tx.get(`pushInstallations/${installationId}`);
   if(job.leaseVersion!==lease.job.leaseVersion||job.status!=='leased')fail('stale_send_result');
   const accepted=response.code==='provider_accepted',invalid=response.code==='unregistered',permanent=invalid||response.code==='permanent_failure';
   const status=accepted?'provider_accepted':permanent?'permanent_failure':'retryable_failure';
   tx.set(`pushJobs/${jobId}`,{...job,status,leaseUntil:null,notBefore:new Date(this.clock()+Math.min(3_600_000,30_000*2**job.attemptCount)),providerCode:accepted?'accepted':invalid?'unregistered':permanent?'permanent':'retryable',messageId:null});
   if(binding?.uid===lease.binding.uid&&binding.bindingEpoch===lease.binding.bindingEpoch&&binding.tokenHash===lease.binding.tokenHash){const ownsLease=binding.sendLeaseJob===jobId&&ms(binding.sendLeaseUntil)===ms(lease.job.leaseUntil);tx.set(`pushInstallations/${installationId}`,{...binding,state:invalid?'revoked':binding.state,sendLeaseUntil:ownsLease?null:binding.sendLeaseUntil,sendLeaseJob:ownsLease?null:binding.sendLeaseJob});}
   return {outcome:status};
  });
 }
}

export class BindingService {
 constructor(store,{clock=Date.now}={}){this.store=store;this.clock=clock;}
 async register(actor,input,challengeSender){
  if(!actor?.uid||actor.emailVerified!==true||!uuid(input.installationId)||!uuid(input.operationId)||typeof input.token!=='string'||input.token.length<1||input.token.length>4096||!/^[0-9a-f]{64}$/.test(input.secret)||typeof input.replaceConfirmed!=='boolean')fail('invalid_binding');
  const now=this.clock(),nonce=randomBytes(32).toString('hex'),challengeId=randomUUID(),digest=hash(input),uid=actor.uid;
  const challenge=await this.store.transaction(async tx=>{
   const receipt=await tx.get(`pushBindingRequests/${uid}/receipts/${input.operationId}`);
   if(receipt){if(receipt.inputHash!==digest)fail('intent_conflict');return {...receipt,retry:true};}
   const p=await tx.get('notificationPolicy/current'),s=await tx.get(`users/${uid}`),rate=await tx.get(`pushBindingRates/${uid}`);
   if(!p?.enabled||!p.pushEnabled||!shell(s,uid))fail('push_disabled');
   if(rate&&ms(rate.windowStart)>now-DAY&&rate.count>=20)fail('binding_quota');
   const old=await tx.get(`pushInstallations/${input.installationId}`);
   if(old&&old.state!=='revoked'&&old.installationSecretHash!==hash(input.secret))fail('installation_proof');
   const value={challengeId,uid,installationId:input.installationId,operationId:input.operationId,inputHash:digest,nonceHash:hash(nonce),token:input.token,tokenHash:hash(input.token),installationSecretHash:hash(input.secret),replaceConfirmed:input.replaceConfirmed,baseVersion:old?.version??0,expiresAt:new Date(now+600_000),failedAttemptCount:0,state:'pending'};
   tx.set(`pushBindingChallenges/${challengeId}`,value);tx.set(`pushBindingRequests/${uid}/receipts/${input.operationId}`,{inputHash:digest,challengeId,expiresAt:value.expiresAt});tx.set(`pushBindingRates/${uid}`,{windowStart:rate&&ms(rate.windowStart)>now-DAY?rate.windowStart:new Date(now),count:rate&&ms(rate.windowStart)>now-DAY?rate.count+1:1});return value;
  });
  if(challenge.retry)return {challengeId:challenge.challengeId,expiresAt:challenge.expiresAt};
  // Nonce is delivered only to claimed installation, NEVER returned to the register caller.
  await challengeSender.sendData(input.token,{schemaVersion:'1',kind:'binding_challenge',challengeId,nonce,expiresAt:String(ms(challenge.expiresAt))});
  return {challengeId,expiresAt:challenge.expiresAt};
 }
 async confirm(actor,{challengeId,nonce,secret}){
  if(!actor?.uid||actor.emailVerified!==true||!uuid(challengeId)||!/^[0-9a-f]{64}$/.test(nonce)||!/^[0-9a-f]{64}$/.test(secret))fail('invalid_proof');
  const result=await this.store.transaction(async tx=>{
   const c=await tx.get(`pushBindingChallenges/${challengeId}`),p=await tx.get('notificationPolicy/current');
   if(!c||c.uid!==actor.uid||!['pending','confirmed'].includes(c.state)||ms(c.expiresAt)<=this.clock()||c.failedAttemptCount>=5||!p?.enabled||!p.pushEnabled)fail('challenge_closed');
   if(!equal(c.nonceHash,hash(nonce))||!equal(c.installationSecretHash,hash(secret))){tx.set(`pushBindingChallenges/${challengeId}`,{...c,failedAttemptCount:c.failedAttemptCount+1});return {error:'invalid_proof'};}
   const old=await tx.get(`pushInstallations/${c.installationId}`),owner=await tx.get(`pushTokenOwners/${c.tokenHash}`),index=await tx.get(`pushUserBindings/${actor.uid}`)??{ids:[]};
   if(c.state==='confirmed'){if(old?.uid!==actor.uid||old.bindingEpoch!==c.confirmedEpoch||old.state!=='active')fail('stale_binding');return {installationId:c.installationId,bindingEpoch:old.bindingEpoch,expiresAt:old.expiresAt};}
   if((old?.version??0)!==c.baseVersion)fail('binding_conflict');
   const prior=owner&&owner.installationId!==c.installationId?await tx.get(`pushInstallations/${owner.installationId}`):null;
   const oldUserIndex=old&&old.uid!==actor.uid?await tx.get(`pushUserBindings/${old.uid}`):null;
   const priorUserIndex=prior&&prior.uid!==actor.uid?await tx.get(`pushUserBindings/${prior.uid}`):null;
   if(index.ids.length>3)fail('binding_index_corrupt');
   let ids=[];for(const id of index.ids){if(id===c.installationId||id===owner?.installationId)continue;const b=await tx.get(`pushInstallations/${id}`);if(b?.uid===actor.uid&&b.state==='active'&&ms(b.expiresAt)>this.clock())ids.push(id);}
   const victims=[];if(ids.length>=3){if(!c.replaceConfirmed)fail('replacement_confirmation');const victim=await tx.get(`pushInstallations/${ids[0]}`);if(victim)victims.push(victim);ids=ids.slice(1);}
   const bindingEpoch=Math.max(old?.bindingEpoch??0,owner?.bindingEpoch??0)+1,time=new Date(this.clock());
   const binding={installationId:c.installationId,uid:actor.uid,token:c.token,tokenHash:c.tokenHash,bindingEpoch,installationSecretHash:c.installationSecretHash,state:'active',version:(old?.version??0)+1,createdAt:old?.createdAt??time,refreshedAt:time,expiresAt:new Date(this.clock()+30*DAY),sendLeaseUntil:null,sendLeaseJob:null};
   if(prior)tx.set(`pushInstallations/${prior.installationId}`,{...prior,state:'revoked',version:prior.version+1});
   for(const v of victims)tx.set(`pushInstallations/${v.installationId}`,{...v,state:'revoked',version:v.version+1});
   if(oldUserIndex)tx.set(`pushUserBindings/${old.uid}`,{ids:oldUserIndex.ids.filter(id=>id!==c.installationId&&id!==prior?.installationId)});
   if(priorUserIndex&&prior.uid!==old?.uid)tx.set(`pushUserBindings/${prior.uid}`,{ids:priorUserIndex.ids.filter(id=>id!==prior.installationId)});
   tx.set(`pushInstallations/${c.installationId}`,binding);tx.set(`pushTokenOwners/${c.tokenHash}`,{installationId:c.installationId,uid:actor.uid,bindingEpoch});tx.set(`pushUserBindings/${actor.uid}`,{ids:[...ids,c.installationId]});
   tx.set(`pushBindingChallenges/${challengeId}`,{...c,state:'confirmed',confirmedEpoch:bindingEpoch,token:null});return {installationId:c.installationId,bindingEpoch,expiresAt:binding.expiresAt};
  });if(result.error)fail(result.error);return result;
 }
 async revoke(actor,{installationId,bindingEpoch,secret}){
  if(!actor?.uid||!uuid(installationId)||!Number.isInteger(bindingEpoch)||!/^[0-9a-f]{64}$/.test(secret))fail('invalid_proof');
  return this.store.transaction(async tx=>{
   const b=await tx.get(`pushInstallations/${installationId}`),index=await tx.get(`pushUserBindings/${actor.uid}`);
   if(!b||b.uid!==actor.uid||b.bindingEpoch!==bindingEpoch||!equal(b.installationSecretHash,hash(secret)))fail('stale_binding');
   tx.set(`pushInstallations/${installationId}`,{...b,state:'revoked',version:b.version+1});if(index)tx.set(`pushUserBindings/${actor.uid}`,{ids:index.ids.filter(id=>id!==installationId)});return {state:'revoked'};
  });
 }
 async renew(actor,{installationId,bindingEpoch,secret}){
  if(!actor?.uid||actor.emailVerified!==true||!uuid(installationId)||!Number.isInteger(bindingEpoch)||!/^[0-9a-f]{64}$/.test(secret))fail('invalid_proof');
  return this.store.transaction(async tx=>{
   const b=await tx.get(`pushInstallations/${installationId}`),p=await tx.get('notificationPolicy/current'),s=await tx.get(`users/${actor.uid}`),owner=b?await tx.get(`pushTokenOwners/${b.tokenHash}`):null;
   if(!p?.enabled||!p.pushEnabled||!shell(s,actor.uid))fail('push_disabled');
   if(!b||b.state!=='active'||ms(b.expiresAt)<=this.clock()||b.uid!==actor.uid||b.bindingEpoch!==bindingEpoch||!equal(b.installationSecretHash,hash(secret))||owner?.uid!==actor.uid||owner.bindingEpoch!==bindingEpoch||owner.installationId!==installationId)fail('stale_binding');
   const next={...b,version:b.version+1,refreshedAt:new Date(this.clock()),expiresAt:new Date(this.clock()+30*DAY)};tx.set(`pushInstallations/${installationId}`,next);return {installationId,bindingEpoch,expiresAt:next.expiresAt};
  });
 }
}
