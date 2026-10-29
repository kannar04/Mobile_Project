// Synthetic delivery fixtures using the two existing identities; never changes their profiles or roles.
const fs=require('fs'),path=require('path'),{randomUUID,randomBytes}=require('crypto'),{mediaAdmin}=require('./media-admin.cjs');
(async()=>{const {db,auth,service}=await mediaAdmin(),root=path.resolve(__dirname,'../build/two-device'),file=path.join(root,'notification-fixture.json');
 const uids=[1,2].map(n=>JSON.parse(fs.readFileSync(path.join(root,'device-'+n+'.json'))).uid);if(uids[0]===uids[1])throw Error('distinct_accounts_required');
 if(process.argv[2]==='audit'){
  const fixture=JSON.parse(fs.readFileSync(file)),cases=[];
  for(const f of fixture.cases){const uid=uids[f.recipientRole==='host'?0:1],rows=await db.collection('users/'+uid+'/notificationInbox').where('targetId','in',[f.post,f.conversationId]).limit(20).get(),items=[];for(const d of rows.docs){const outbox=await db.doc('activityPushOutbox/'+d.id).get();items.push({kind:d.get('kind'),pushState:outbox.get('state')??null,attempts:outbox.get('attempts')??0});}cases.push({role:f.recipientRole,items});}fs.writeFileSync(path.join(root,'notification-delivery.json'),JSON.stringify({cases},null,2));console.log({notificationDeliveryReceiptSaved:true});
 }else if(process.argv[2]==='cleanup'){
  const fixture=JSON.parse(fs.readFileSync(file));let removed=0;
  for(const f of fixture.cases){
   for(const uid of uids){const items=await db.collection('users/'+uid+'/notificationInbox').where('targetId','in',[f.post,f.conversationId].filter(Boolean)).limit(20).get(),binding=(await db.doc('pushUserBindings/'+uid).get()).get('ids')??[];
    for(const d of items.docs){for(const id of binding)await db.doc('activityPushAttempts/'+d.id+'_'+id).delete();await db.doc('activityPushOutbox/'+d.id).delete();await d.ref.delete();removed++;}
    if(f.conversationId){const commands=await db.collection('directCommands/'+uid+'/receipts').where('conversationId','==',f.conversationId).limit(20).get();for(const d of commands.docs){await d.ref.delete();removed++;}}
   }
   for(const p of f.paths){if(!p.includes(f.post)&&!p.includes(f.conversationId))throw Error('cleanup_scope');await db.recursiveDelete(db.doc(p));removed++;}
  }
  fs.writeFileSync(path.join(root,'notification-cleanup.json'),JSON.stringify({exactFixtureDocumentsRemoved:removed,profilesAndRolesUnchanged:true,deviceBindingsRetained:true,quotaHistoryRetained:true},null,2));console.log({notificationFixturesCleaned:true});
 }else{
  if(fs.existsSync(file))throw Error('fixture_already_exists');const fixture={cases:[]};
  for(let i=0;i<2;i++){const recipient=uids[i],sender=uids[1-i];for(const uid of [recipient,sender]){const u=await auth.getUser(uid);if(u.disabled||!u.emailVerified)throw Error('active_verified_account_required');}
   const post=randomUUID(),comment=randomUUID(),now=new Date(),f={recipientRole:i===0?'host':'adopter',post,comment,paths:['communityPosts/'+post,'communityFeed/'+post]};fixture.cases.push(f);fs.writeFileSync(file,JSON.stringify(fixture,null,2));
   await db.doc(f.paths[0]).create({schemaVersion:2,postId:post,authorUid:recipient,lifecycle:'active',visibility:'visible',moderatorHold:'clear',contentVersion:1,body:'DEMO two-device delivery fixture'});await db.doc(f.paths[1]).create({schemaVersion:2,postId:post,authorUid:recipient,state:'visible',contentVersion:1});
   await db.doc('communityPosts/'+post+'/comments/'+comment).create({schemaVersion:2,postId:post,commentId:comment,authorUid:sender,state:'visible',commentVersion:1,body:'DEMO two-device notification check',createdAt:now,updatedAt:now});await db.doc('communityPosts/'+post+'/likes/'+sender).create({schemaVersion:2,postId:post,uid:sender,active:true,likeVersion:1,updatedAt:now});
   f.conversationId=randomBytes(32).toString('hex');f.paths.push('directConversations/'+f.conversationId,...uids.map(uid=>'users/'+uid+'/directConversationIndex/'+f.conversationId));fs.writeFileSync(file,JSON.stringify(fixture,null,2));await db.doc('directConversations/'+f.conversationId).create({schemaVersion:1,conversationId:f.conversationId,participants:uids.slice().sort(),blocks:{},nextSequence:1,createdAt:now,updatedAt:now});for(const uid of uids)await db.doc('users/'+uid+'/directConversationIndex/'+f.conversationId).create({schemaVersion:1,conversationId:f.conversationId,peerUid:uids.find(x=>x!==uid),updatedAt:now});const cmd={conversationId:f.conversationId,operationId:randomUUID(),body:'DEMO two-device delivery check'};await service.directSend({uid:sender},cmd);await service.directSend({uid:sender},cmd);
  }console.log({twoRecipients:true,syntheticCommentLikeFixtures:true,managedMessageSendWithExactRetry:true,profilesAndRolesUnchanged:true});
 }await db.terminate();process.exit(0);
})().catch(e=>{console.error({failed:true,code:e.code??'notification_fixture_failed'});process.exit(1);});
