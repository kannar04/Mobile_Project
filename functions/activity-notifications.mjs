import {createHash} from 'node:crypto';
import {getMessaging} from 'firebase-admin/messaging';
const hash=x=>createHash('sha256').update(x).digest('hex');
const ms=x=>x?.toMillis?.()??(x instanceof Date?+x:0);
const DAY=86400000;
// Ordinary community membership does not require a protected adoption-role record.
const eligible=(member,subject,protectedEvent=false)=>member?.status==='active'&&(subject?subject.status==='active':!protectedEvent);
export async function mirrorAdoptionNotice(db,uid,noticeId){
 return db.runTransaction(async tx=>{const source=(await tx.get(db.doc(`users/${uid}/adoptionFlowNotices/${noticeId}`))).data(),subject=(await tx.get(db.doc('securitySubjects/'+uid))).data();if(!source||subject?.status!=='active')return {ignored:true};if(source.adoptionId){const adoption=(await tx.get(db.doc('adoptions/'+source.adoptionId))).data();if(!adoption?.participantUids?.includes(uid))return {ignored:true};}const id=hash('adoption:'+uid+':'+noticeId),ref=db.doc(`users/${uid}/notificationInbox/${id}`),prior=await tx.get(ref);if(prior.exists)return {duplicate:true};const at=new Date(ms(source.createdAt)||Date.now());tx.create(ref,{schemaVersion:1,recipientUid:uid,kind:'adoption',routeType:source.adoptionId?'adoption':'inbox',targetId:source.adoptionId??'main',sourceVersion:1,templateKey:'adoption_update',createdAt:at,updatedAt:at,expiresAt:new Date(+at+30*DAY),itemVersion:1,state:'active',readAt:null,dismissedAt:null});return {created:true};});
}
/** Only trusted committed source paths enter here; raw clients cannot create inbox/outbox rows. */
export async function activitySource(db,kind,ids,{clock=Date.now}={}){
 return db.runTransaction(async tx=>{
  const read=async p=>(await tx.get(db.doc(p))).data();
  const policy=await read('notificationPolicy/current');if(!policy?.enabled)return {disabled:true};
  let source,uid,writer,target,version,route,template;
  if(kind==='adoption_chat'){
   source=await read(`adoptionFlowConversations/${ids.conversation}/messages/${ids.message}`);const root=await read('adoptions/'+ids.conversation),conv=await read('adoptionFlowConversations/'+ids.conversation);if(!source||conv?.state!=='ACTIVE'||root?.participantUids?.length!==2||!root.participantUids.includes(source.senderUid))return {ignored:true};writer=source.senderUid;uid=root.participantUids.find(x=>x!==writer);target=ids.conversation;version=1;route='adoption';template='adoption_update';
  }else if(kind==='message'){
   source=await read(`directConversations/${ids.conversation}/messages/${ids.message}`);
   const root=await read('directConversations/'+ids.conversation);
   if(source?.schemaVersion!==1||root?.schemaVersion!==1||root?.participants?.length!==2||!root.participants.includes(source.senderUid))return {ignored:true};
   writer=source.senderUid;uid=root.participants.find(x=>x!==writer);if(root.blocks?.[writer]||root.blocks?.[uid])return {ignored:true};
   target=ids.conversation;version=source.sequence;route='direct';template='direct_message';
  }else{
   const post=await read('communityPosts/'+ids.post),feed=await read('communityFeed/'+ids.post);
   if(post?.schemaVersion!==2||feed?.schemaVersion!==2||feed.authorUid!==post.authorUid||post?.lifecycle!=='active'||post.visibility!=='visible'||post.moderatorHold!=='clear'||feed?.state!=='visible'||feed.contentVersion!==post.contentVersion)return {ignored:true};
   uid=post.authorUid;target=ids.post;route='post';
   if(kind==='comment'){
    source=await read(`communityPosts/${ids.post}/comments/${ids.source}`);
    if(source?.schemaVersion!==2||source.postId!==ids.post||source?.state!=='visible'||source.commentVersion!==1)return {ignored:true};
    writer=source.authorUid;version=source.commentVersion;template='comments_on_post';
   }else if(kind==='like'){
    source=await read(`communityPosts/${ids.post}/likes/${ids.source}`);
    if(source?.schemaVersion!==2||source.postId!==ids.post||source?.active!==true)return {ignored:true};writer=source.uid;version=source.likeVersion;template='likes_on_post';
   }else return {ignored:true};
  }
  if(!uid||!writer||uid===writer||!Number.isSafeInteger(version))return {ignored:true};
  const at=ms(source.createdAt)||ms(source.updatedAt),now=clock();if(at<ms(policy.activationWatermark)||at<now-7*DAY||at>now+60000)return {ignored:true};
  for(const who of [uid,writer]){const member=await read('communityMemberships/'+who),subject=await read('securitySubjects/'+who);if(!eligible(member,subject,kind==='adoption_chat'))return {ignored:true};}
  const blocked=await read(`communicationBlocks/${uid}/users/${writer}`),reverse=await read(`communicationBlocks/${writer}/users/${uid}`);if(blocked?.blocked||reverse?.blocked)return {ignored:true};
  // A liker can toggle repeatedly: notify once per post/actor/day, not once per toggle.
  const eventId=hash(JSON.stringify([kind,target,kind==='like'?writer:ids.source??ids.message,kind==='like'?new Date(at).toISOString().slice(0,10):version,uid]));
  const ref=db.doc(`users/${uid}/notificationInbox/${eventId}`),prior=await tx.get(ref);if(prior.exists)return {duplicate:true};
  const quotaRef=db.doc(`notificationQuota/${uid}/days/${new Date(now).toISOString().slice(0,10)}`),quota=(await tx.get(quotaRef)).data()??{inboxNewCount:0,pushAttemptCount:0};if(quota.inboxNewCount>=50)return {capped:true};
  tx.create(ref,{schemaVersion:1,recipientUid:uid,kind:kind==='adoption_chat'?'adoption':kind,routeType:route,targetId:target,sourceVersion:version,templateKey:template,createdAt:new Date(now),updatedAt:new Date(now),expiresAt:new Date(now+30*DAY),itemVersion:1,state:'active',readAt:null,dismissedAt:null});
  tx.set(quotaRef,{...quota,inboxNewCount:quota.inboxNewCount+1});
  tx.create(db.doc('activityPushOutbox/'+eventId),{uid,eventId,kind,ids,writer,state:'pending',createdAt:new Date(now),attempts:0});return {created:true,eventId};
 });
}
export async function dispatchActivity(db,{sender=getMessaging(),clock=Date.now}={}){
 const docs=await db.collection('activityPushOutbox').where('state','==','pending').limit(20).get();
 for(const doc of docs.docs){const job=doc.data(),ids=(await db.doc('pushUserBindings/'+job.uid).get()).get('ids')??[];let delivered=false;
  for(const id of ids.slice(0,5)){
   const lease=await db.runTransaction(async tx=>{
    const read=async p=>(await tx.get(db.doc(p))).data(),j=await read(doc.ref.path),binding=await read('pushInstallations/'+id),owner=binding?await read('pushTokenOwners/'+binding.tokenHash):null,policy=await read('notificationPolicy/current'),prefs=await read(`users/${job.uid}/notificationPreferences/main`),notice=await read(`users/${job.uid}/notificationInbox/${job.eventId}`),subject=await read('securitySubjects/'+job.uid),member=await read('communityMemberships/'+job.uid),attemptRef=db.doc(`activityPushAttempts/${doc.id}_${id}`),attempt=(await tx.get(attemptRef)).data(),qref=db.doc(`notificationQuota/${job.uid}/days/${new Date(clock()).toISOString().slice(0,10)}`),quota=(await tx.get(qref)).data()??{inboxNewCount:0,pushAttemptCount:0};
    if(j?.state!=='pending'||!policy?.enabled||!policy.pushEnabled||!prefs?.pushEnabled||(['comment','like'].includes(job.kind)&&prefs.commentsPush!==true)||!eligible(member,subject,job.kind==='adoption_chat')||notice?.readAt||notice?.state!=='active'||binding?.state!=='active'||binding.uid!==job.uid||ms(binding.expiresAt)<=clock()||owner?.uid!==job.uid||owner.installationId!==id||owner.bindingEpoch!==binding.bindingEpoch||attempt?.sent||attempt?.count>=3||ms(attempt?.leaseUntil)>clock()||ms(binding.sendLeaseUntil)>clock()||quota.pushAttemptCount>=20)return null;
    const peer=await read('securitySubjects/'+job.writer),peerMember=await read('communityMemberships/'+job.writer),a=await read(`communicationBlocks/${job.uid}/users/${job.writer}`),b=await read(`communicationBlocks/${job.writer}/users/${job.uid}`);if(!eligible(peerMember,peer,job.kind==='adoption_chat')||a?.blocked||b?.blocked)return null;
    if(job.kind==='adoption_chat'){const root=await read('adoptions/'+job.ids.conversation),conv=await read('adoptionFlowConversations/'+job.ids.conversation);if(!root?.participantUids?.includes(job.uid)||conv?.state!=='ACTIVE')return null;}else if(job.kind==='message'){const root=await read('directConversations/'+job.ids.conversation);if(!root?.participants?.includes(job.uid)||root.blocks?.[job.uid]||root.blocks?.[job.writer])return null;}else {const post=await read('communityPosts/'+job.ids.post),feed=await read('communityFeed/'+job.ids.post),source=await read(`communityPosts/${job.ids.post}/${job.kind==='like'?'likes':'comments'}/${job.ids.source}`);if(post?.authorUid!==job.uid||post.lifecycle!=='active'||post.visibility!=='visible'||post.moderatorHold!=='clear'||feed?.state!=='visible'||feed.contentVersion!==post.contentVersion||!source||(job.kind==='like'?source.active!==true:source.state!=='visible'))return null;}
    const next={count:(attempt?.count??0)+1,leaseUntil:new Date(clock()+60000),epoch:binding.bindingEpoch,sent:false};tx.set(attemptRef,next);tx.set(db.doc('pushInstallations/'+id),{...binding,sendLeaseUntil:next.leaseUntil,sendLeaseJob:doc.id});tx.set(qref,{...quota,pushAttemptCount:quota.pushAttemptCount+1});return {token:binding.token,epoch:binding.bindingEpoch,attemptRef,count:next.count};
   });if(!lease)continue;
   let sent=false;try{await sender.send({token:lease.token,data:{kind:'activity',schemaVersion:'2',uid:job.uid,installationId:id,bindingEpoch:String(lease.epoch),eventId:job.eventId},android:{priority:'high',ttl:3600000}});sent=true;delivered=true;}catch{/* No tokens or private content in logs. */}
   await db.runTransaction(async tx=>{const a=await tx.get(lease.attemptRef),ref=db.doc('pushInstallations/'+id),b=await tx.get(ref);if(a.get('count')!==lease.count)return;tx.set(lease.attemptRef,{...a.data(),sent,leaseUntil:null});if(b.get('bindingEpoch')===lease.epoch&&b.get('sendLeaseJob')===doc.id)tx.update(ref,{sendLeaseUntil:null,sendLeaseJob:null});});
  }
  await doc.ref.update({attempts:(job.attempts??0)+1,state:delivered?'sent':clock()-ms(job.createdAt)>DAY||(job.attempts??0)>=9?'expired':'pending'});
 }return {scanned:docs.size};
}
