import {FieldPath} from 'firebase-admin/firestore';
import {Work,exact,uuid,fail} from './domain/safety-common.mjs';
import {ordinaryMember} from './domain/participation.mjs';
/** Private recipient history. The caller cannot choose a recipient or arbitrary collection. */
export async function ownerNotices(runtime,a,c){
 if(!exact(c,['kind','after'])||!['content','account'].includes(c.kind)||(c.after!==null&&!uuid(c.after)))fail('invalid_intent');
 await runtime.store.transaction(tx=>ordinaryMember(new Work(tx),a,{recovery:true}));
 const root=`users/${a.uid}/${c.kind==='content'?'moderationNotices':'decisionNotices'}`;let q=runtime.db.collection(root).orderBy('createdAt','desc').orderBy(FieldPath.documentId(),'desc').limit(20);
 if(c.after){const cursor=await runtime.db.doc(root+'/'+c.after).get();if(!cursor.exists)fail('invalid_cursor');q=q.startAfter(cursor);}
 const docs=await q.get(),rows=[];
 for(const doc of docs.docs){const d=doc.data();if(!uuid(doc.id)||d.uid!==a.uid||typeof d.reason!=='string')continue;let appeal=null;if(c.kind==='account'){const link=await runtime.document('decisionAppealLinks/'+a.uid+'_'+doc.id);if(link){if(!uuid(link.appealId))fail('appeal_inventory_required');const r=await runtime.document('decisionAppeals/'+link.appealId);if(r?.subjectUid!==a.uid||r.decisionId!==doc.id)fail('appeal_inventory_required');appeal={appealId:link.appealId,status:r.status,version:r.version};}}rows.push({noticeId:doc.id,kind:c.kind,reason:d.reason,action:d.action??null,scope:d.scope??null,state:d.state??null,version:d.version??1,createdAt:d.createdAt,reviewAt:d.reviewAt??null,appeal});}
 await runtime.store.transaction(tx=>ordinaryMember(new Work(tx),a,{recovery:true}));return {rows,next:docs.size===20?docs.docs.at(-1).id:null};
}
