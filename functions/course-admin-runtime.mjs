import {FieldPath} from 'firebase-admin/firestore';
import {CourseModeration} from './domain/course-moderation.mjs';
import {Work,capability,exact,uuid,fail} from './domain/safety-common.mjs';
const require=(v)=>{if(!v)fail('invalid_intent');};
const collections={post:'communityPosts',listing:'listings',pet:'petOfferAuthorities',lost_found:'lostFoundNotices',foster_ad:'careAdvertisements'};
const versions={post:'controlVersion',comment:'commentVersion',listing:'controlVersion',pet:'authorityVersion',lost_found:'version',foster_ad:'version'};
/** Public-content inventory only; bounded pages and no caller-supplied collection paths. */
export class CourseAdminRuntime {
 constructor(db,store){this.db=db;this.store=store;this.service=new CourseModeration(store);}
 async read(actor,c){
  if(c?.mode==='target'){require(exact(c,['mode','kind','targetId','parentId']));const {mode,...target}=c;return this.service.read(actor,target);}
  if(c?.mode==='queue'&&c.kind==='report'){
   require(exact(c,['mode','kind','parentId','after'])&&c.parentId===null&&(c.after===null||uuid(c.after)));await this.store.transaction(tx=>capability(new Work(tx),actor,'report_review'));
   let q=this.db.collection('reports').orderBy(FieldPath.documentId()).limit(20);if(c.after)q=q.startAfter(c.after);const docs=await q.get();const rows=docs.docs.filter(d=>['received','needs_specialist'].includes(d.get('status'))).map(d=>({kind:'report',targetId:d.id,parentId:null,title:d.get('category'),state:d.get('status')}));await this.store.transaction(tx=>capability(new Work(tx),actor,'report_review'));return {rows,next:docs.size===20?docs.docs.at(-1).id:null};
  }
  require(exact(c,['mode','kind','parentId','after'])&&c.mode==='queue'&&(collections[c.kind]||c.kind==='comment')&&(c.kind==='comment'?uuid(c.parentId):c.parentId===null)&&(c.after===null||uuid(c.after)));
  await this.store.transaction(tx=>capability(new Work(tx),actor,'course_content_moderation'));
  let q=this.db.collection(c.kind==='comment'?`communityPosts/${c.parentId}/comments`:collections[c.kind]).orderBy(FieldPath.documentId()).limit(20);if(c.after)q=q.startAfter(c.after);
  const docs=await q.get(),rows=[];
  for(const doc of docs.docs){const d=doc.data();if(!uuid(doc.id)||!Number.isSafeInteger(d[versions[c.kind]]))continue;if(c.kind==='post'&&(d.lifecycle!=='active'||d.visibility!=='visible'))continue;if(c.kind==='listing'&&(d.lifecycle!=='open'||d.visibility!=='shown'))continue;let title=d.title??d.summary??d.body??null;if(c.kind==='post'){const revision=(await this.db.doc(`communityPosts/${doc.id}/revisions/${d.currentRevisionId}`).get()).data();title=revision?.contentVersion===d.contentVersion?revision.body:null;}const control=(await this.db.doc('courseModerationControls/'+c.kind+':'+doc.id).get()).data();rows.push({kind:c.kind,targetId:doc.id,parentId:c.parentId,sourceVersion:d[versions[c.kind]],ownerUid:d.authorUid??d.ownerUid??d.claimUid??null,state:control?.state??'clear',version:control?.version??0,holdActionId:control?.holdActionId??null,title});}
  await this.store.transaction(tx=>capability(new Work(tx),actor,'course_content_moderation'));
  return {rows,next:docs.size===20?docs.docs.at(-1).id:null};
 }
 async execute(actor,c){return this.service.execute(actor,c);}
}
