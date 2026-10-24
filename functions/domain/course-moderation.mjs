import {Commands,Work,capability,exact,uuid,text,integer,fail,ms} from './safety-common.mjs';
import {overlay} from './participation.mjs';
import {courseListingCurrent,writeCourseProjection} from './adoption-source3.mjs';
import {source,listingReviewCurrent,sha} from './adoption-source2.mjs';

const must=(v,c='moderation_denied')=>{if(!v)fail(c);};
const kinds={post:{root:'communityPosts',feeds:['communityFeed','communityPublicFeed'],version:'controlVersion',owner:'authorUid'},comment:{version:'commentVersion',owner:'authorUid'},listing:{root:'listings',feeds:['listingDiscovery','publicListings'],version:'controlVersion',owner:'ownerUid'},pet:{root:'petOfferAuthorities',feeds:[],version:'authorityVersion',owner:'claimUid'},lost_found:{root:'lostFoundNotices',feeds:['lostFoundFeed'],version:'version',owner:'authorUid'},foster_ad:{root:'careAdvertisements',feeds:['careAdvertFeed'],version:'version',owner:'authorUid'}};
/** Bounded public-content adapters only. Does not expose private chats or mutate custody. */
export class CourseModeration extends Commands {
 constructor(store,options={}){super(store,'courseModerationReceipts',{hold:['kind','targetId','parentId','sourceVersion','reason'],restore:['kind','targetId','parentId','sourceVersion','holdActionId','reason']},options);}
 async target(w,c){const k=kinds[c.kind];must(k&&uuid(c.targetId)&&(c.kind==='comment'?uuid(c.parentId):c.parentId===null),'moderation_target');const path=c.kind==='comment'?`communityPosts/${c.parentId}/comments/${c.targetId}`:`${k.root}/${c.targetId}`,root=await w.get(path);must(root,'target_unavailable');return {k,path,root,feeds:c.kind==='comment'?[`communityCommentFeed/${c.parentId}/comments/${c.targetId}`]:k.feeds.map(f=>f+'/'+c.targetId)};}
 async command(w,a,c,now){
  await capability(w,a,'course_content_moderation',{now});const t=await this.target(w,c),{root,k,path,feeds}=t;
  await capability(w,a,'course_content_moderation',{conflicts:[root[k.owner]],now});must(text(c.reason,1000,1)&&integer(c.sourceVersion,1)&&root[k.version]===c.sourceVersion,'source_changed');
  const id=c.kind+':'+c.targetId,controlPath='courseModerationControls/'+id,prior=await w.get(controlPath),version=prior?.version??0;must(version===c.expectedVersion,'conflict');
  const at=new Date(now),next={...root,[k.version]:root[k.version]+1,updatedAt:at},snapshots=[];
  if(c.command==='hold'){
   must(!prior||prior.state==='clear','already_held');
   if(c.kind==='post')must([2,3,4,5,6].includes(root.schemaVersion)&&root.moderatorHold==='clear'&&root.lifecycle==='active'&&root.visibility==='visible','post_schema');
   if(c.kind==='comment')must(root.state==='visible','comment_unavailable');
   if(c.kind==='listing')must(root.schemaVersion===2&&root.lifecycle==='open','offer_unavailable');
   if(c.kind==='pet')must([1,3].includes(root.schemaVersion)&&root.hold==='clear','declaration_changed');
   if(c.kind==='lost_found')must(root.hold==='clear'&&root.state==='open','notice_unavailable');
   if(c.kind==='foster_ad')must(root.state==='published','advertisement_unavailable');
   for(const p of feeds)snapshots.push({path:p,data:await w.get(p)});
   if(c.kind==='post')next.moderatorHold='held';else if(c.kind==='comment')next.state='removed';else if(['pet','lost_found'].includes(c.kind))next.hold='held';else if(c.kind==='foster_ad')next.state='held';
   for(const p of feeds)w.delete(p);
   w.set('courseModerationActions/'+c.operationId,{schemaVersion:1,operationId:c.operationId,actorUid:a.uid,kind:c.kind,targetId:c.targetId,parentId:c.parentId,action:'hold',sourceVersion:c.sourceVersion,heldVersion:next[k.version],reason:c.reason,snapshots,createdAt:at});
  }else{
   must(uuid(c.holdActionId)&&prior?.state==='held'&&prior.holdActionId===c.holdActionId,'hold_changed');const action=await w.get('courseModerationActions/'+c.holdActionId);
   must(action?.kind===c.kind&&action.targetId===c.targetId&&action.parentId===c.parentId&&action.heldVersion===root[k.version],'source_changed');
   if(c.kind==='post'){
    const [revision,policy,member,subject]=await Promise.all([w.get(`${path}/revisions/${root.currentRevisionId}`),w.get('system/communityPolicy'),w.get('communityMemberships/'+root.authorUid),w.get('securitySubjects/'+root.authorUid)]);
    must(root.lifecycle==='active'&&root.visibility==='visible'&&root.moderatorHold==='held'&&revision?.contentVersion===root.contentVersion&&policy?.enabled&&member?.status==='active'&&(!subject||subject.status==='active'),'restore_ineligible');if(root.audience==='everyone')must((await w.get('system/communityPublicPolicy'))?.enabled===true,'restore_ineligible');next.moderatorHold='clear';
   }else if(c.kind==='comment'){must(root.state==='removed','restore_ineligible');const post=await w.get('communityPosts/'+c.parentId);must(post?.moderatorHold==='clear'&&post.lifecycle==='active'&&post.visibility==='visible','restore_ineligible');next.state='visible';}
   else if(c.kind==='pet'){must(root.hold==='held','restore_ineligible');next.hold='clear';}
   else if(c.kind==='lost_found'){must(root.hold==='held'&&root.state==='open'&&ms(root.expiresAt)>now,'restore_ineligible');next.hold='clear';}
   else if(c.kind==='foster_ad'){must(root.state==='held'&&ms(root.startAt)>now,'restore_ineligible');next.state='published';}
   w.set(controlPath,{schemaVersion:1,kind:c.kind,targetId:c.targetId,state:'clear',version:version+1,holdActionId:null,updatedAt:at});
   if(c.kind==='listing'){
    const o=overlay(w),pub=await o.get('listingPublication/'+c.targetId),b=pub?.schemaVersion===3?await courseListingCurrent(o,root,pub):await source(o,c.targetId,{offering:false});await listingReviewCurrent(o,root,pub);must(root.lifecycle==='open'&&root.visibility==='shown'&&pub.state==='eligible'&&pub.approvedContentVersion===root.contentVersion&&sha({...b.fingerprint,controlVersion:0})===sha({...pub.approvedSource,controlVersion:0}),'restore_ineligible');
    writeCourseProjection(o,{...b,l:next,hash:pub.sourceFingerprint},pub.publicationVersion,pub.firstPublishedAt,at);
   }else for(const s of action.snapshots){must(feeds.includes(s.path),'snapshot_invalid');if(s.data){const d={...s.data};if(c.kind==='post'&&Object.hasOwn(d,'controlVersion'))d.controlVersion=next.controlVersion;if(c.kind==='comment')d.commentVersion=next.commentVersion;w.set(s.path,d);}}
   w.set('courseModerationActions/'+c.operationId,{schemaVersion:1,operationId:c.operationId,actorUid:a.uid,kind:c.kind,targetId:c.targetId,parentId:c.parentId,action:'restore',holdActionId:c.holdActionId,sourceVersion:c.sourceVersion,reason:c.reason,createdAt:at});
  }
  if(c.command==='hold')w.set(controlPath,{schemaVersion:1,kind:c.kind,targetId:c.targetId,state:'held',version:version+1,holdActionId:c.operationId,updatedAt:at});
  w.set(path,next);w.set(`users/${root[k.owner]}/moderationNotices/${c.operationId}`,{schemaVersion:1,uid:root[k.owner],actionId:c.operationId,targetId:c.targetId,reason:c.reason,action:c.command,kind:c.kind,createdAt:at});
  return {actionId:c.operationId,version:version+1,sourceVersion:next[k.version],state:c.command==='hold'?'held':'clear'};
 }
 async read(actor,c){must(exact(c,['kind','targetId','parentId']),'invalid_intent');return this.store.transaction(async tx=>{const w=new Work(tx);await capability(w,actor,'course_content_moderation');const t=await this.target(w,c);return {kind:c.kind,targetId:c.targetId,parentId:c.parentId,sourceVersion:t.root[t.k.version],ownerUid:t.root[t.k.owner],control:await w.get('courseModerationControls/'+c.kind+':'+c.targetId)};});}
}
