// One disposable unverified identity, using the public client SDK; never the phone's account.
import {readFileSync,writeFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
import {initializeApp,deleteApp} from 'firebase/app';
import {getAuth,createUserWithEmailAndPassword,deleteUser} from 'firebase/auth';
import {getFirestore,doc,getDocFromServer,setDoc,serverTimestamp,terminate} from 'firebase/firestore';
import {intent,social} from './daily-protocol.mjs';
const require=createRequire(import.meta.url),{postIds}=require('./seed-spark-posts.cjs'),{connect}=require('./course-owner-firestore.cjs');
const config=JSON.parse(readFileSync('../app/google-services.json','utf8'));assert.equal(config.project_info.project_id,'aniloveproject');
assert.equal(JSON.parse(readFileSync('../build/ux-rules-release.json','utf8')).released,true);
const client=config.client.find(c=>c.client_info.android_client_info.package_name==='com.gmail.manhle240406.project');
const app=initializeApp({projectId:'aniloveproject',apiKey:client.api_key[0].current_key,appId:client.client_info.mobilesdk_app_id},'ux-live-'+randomUUID());
const auth=getAuth(app),db=getFirestore(app),email=`ux-flow-${randomUUID()}@example.invalid`,result={project:'aniloveproject',checks:[],fixtureCleaned:false,phoneAccountModified:false};
let own,post,cid;const operations=[];let liked=false,commentVersion=0;
const run=async command=>{operations.push(command.operationId);return social(db,own.uid,command);};
try{
 own=(await createUserWithEmailAndPassword(auth,email,randomUUID()+randomUUID())).user;assert.equal(own.emailVerified,false);
 await setDoc(doc(db,`users/${own.uid}`),{uid:own.uid,schemaVersion:1,createdAt:serverTimestamp()});
 await setDoc(doc(db,`communityMemberships/${own.uid}`),{uid:own.uid,schemaVersion:1,status:'active',membershipVersion:1,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});result.checks.push('unverified_strict_ordinary_enrollment');
 await setDoc(doc(db,`users/${own.uid}/profileDraft/current`),{ownerUid:own.uid,schemaVersion:1,version:1,displayName:'Fictional UX Member',avatarKey:'default',regionCode:null,bio:'',submission:'draft',lastOperationId:randomUUID(),updatedAt:serverTimestamp()});
 assert.equal((await getDocFromServer(doc(db,`publicProfiles/${own.uid}`))).exists(),false);result.checks.push('unverified_private_name_without_publication');
 post=(await getDocFromServer(doc(db,`communityFeed/${postIds[0]}`))).data();assert.ok(post.body.startsWith('[DEMO'));cid=randomUUID();
 const like=intent('set_like',post.postId,post.contentVersion,0,{active:true},null,0);await run(like);liked=true;await social(db,own.uid,like);assert.equal((await getDocFromServer(doc(db,`communityPosts/${post.postId}/likes/${own.uid}`))).data().likeVersion,1);result.checks.push('unverified_like_exact_retry');
 await run(intent('add_comment',post.postId,post.contentVersion,0,{body:'[DEMO TEST] Temporary unverified interaction check.'},cid));commentVersion=1;
 await run(intent('edit_comment',post.postId,post.contentVersion,0,{body:'[DEMO TEST] Temporary interaction check completed.'},cid,1));commentVersion=2;result.checks.push('unverified_comment_add_edit');
 await assert.rejects(()=>social(db,own.uid,intent('publish',undefined,0,0,{body:'[DEMO TEST] Must never publish',bundledIllustrationKey:null,consent:true})),e=>e.code==='permission-denied');result.checks.push('unverified_publication_denied');
 await assert.rejects(()=>setDoc(doc(db,`securitySubjects/${own.uid}`),{status:'active',roles:['admin'],updatedAt:serverTimestamp()}),e=>e.code==='permission-denied');result.checks.push('protected_role_escalation_denied');
}catch(error){result.failureCode=error.code??'assertion_failure';process.exitCode=1;}
finally{
 if(own){
  try{
   assert.equal(own.email,email);assert.equal(own.emailVerified,false);
   if(post&&commentVersion){await run(intent('withdraw_comment',post.postId,post.contentVersion,0,{},cid,commentVersion));commentVersion++;}
   if(post&&liked)await run(intent('set_like',post.postId,post.contentVersion,0,{active:false},null,1));
   const api=await connect();const paths=[`users/${own.uid}`,`communityMemberships/${own.uid}`,`users/${own.uid}/profileDraft/current`,...operations.map(op=>`users/${own.uid}/communityOperations/${op}`)];
   if(post&&liked)paths.push(`communityPosts/${post.postId}/likes/${own.uid}`);
   if(post&&commentVersion){paths.push(`communityPosts/${post.postId}/comments/${cid}`);for(let n=1;n<=commentVersion;n++)paths.push(`communityPosts/${post.postId}/comments/${cid}/revisions/v${n}`);}
   await api.transaction(async read=>{
    const writes=[];for(const path of [...new Set(paths)]){const value=await read(path);if(!value)continue;
     assert.ok(value.uid===own.uid||value.ownerUid===own.uid||value.authorUid===own.uid,'fixture_owner_mismatch');
     if(path.endsWith(`/comments/${cid}`))assert.equal(value.state,'withdrawn');
     if(path.endsWith(`/likes/${own.uid}`))assert.equal(value.active,false);
     writes.push({path,data:null});
    }return {writes};
   },true);
   await deleteUser(own);result.fixtureCleaned=true;
  }catch(error){result.cleanupFailureCode=error.code??'fixture_cleanup_guard';process.exitCode=1;}
 }
 await terminate(db);await deleteApp(app);writeFileSync('../build/ux-live-result.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
}
