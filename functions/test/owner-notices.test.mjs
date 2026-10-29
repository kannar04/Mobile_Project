import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {initializeApp,deleteApp} from 'firebase-admin/app';
import {getFirestore} from 'firebase-admin/firestore';
import {AdoptionStore} from '../adoption-runtime.mjs';
import {ownerNotices} from '../owner-notices.mjs';
test('recipient history allows a restricted unverified ordinary member and isolates other recipients',{skip:!process.env.FIRESTORE_EMULATOR_HOST},async()=>{
 const app=initializeApp({projectId:'demo-notices-'+randomUUID().slice(0,8)},randomUUID()),db=getFirestore(app),store=new AdoptionStore(db),at=new Date(),id=randomUUID(),other=randomUUID(),appealId=randomUUID();
 const runtime={db,store,document:async p=>(await db.doc(p).get()).data()??null};
 try{
  for(const [path,data] of [
   ['users/member',{uid:'member',schemaVersion:1,createdAt:at}],
   ['communityMemberships/member',{schemaVersion:1,uid:'member',status:'restricted',membershipVersion:1,createdAt:at,updatedAt:at}],
   ['users/member/decisionNotices/'+id,{uid:'member',reason:'Explain this restriction',state:'active',scope:'new_adoption',version:1,createdAt:at,privateEvidence:'must not leak'}],
   ['users/other/decisionNotices/'+other,{uid:'other',reason:'Other recipient',createdAt:at}],
   ['decisionAppealLinks/member_'+id,{appealId}],
   ['decisionAppeals/'+appealId,{subjectUid:'member',decisionId:id,status:'received',version:1,statement:'private statement'}]
  ])await db.doc(path).set(data);
  const actor={uid:'member',emailVerified:false},r=await ownerNotices(runtime,actor,{kind:'account',after:null});
  assert.equal(r.rows.length,1);assert.equal(r.rows[0].noticeId,id);assert.equal(r.rows[0].appeal.appealId,appealId);assert(!JSON.stringify(r).includes('privateEvidence'));assert(!JSON.stringify(r).includes('private statement'));
  await assert.rejects(ownerNotices(runtime,actor,{kind:'account',after:other}),/invalid_cursor/);
  await assert.rejects(ownerNotices(runtime,actor,{kind:'account',after:null,uid:'other'}),/invalid_intent/);
  await assert.rejects(ownerNotices(runtime,actor,{kind:'privateChats',after:null}),/invalid_intent/);
  await db.doc('communityMemberships/member').update({status:'deleted'});
  await assert.rejects(ownerNotices(runtime,actor,{kind:'account',after:null}),/account_restricted/);
 }finally{await db.terminate();await deleteApp(app);}
});
