// Synthetic fixtures ONLY. Never connects to a hosted Firebase project.
import assert from 'node:assert/strict';
import {createHash, randomUUID} from 'node:crypto';
import {initializeApp, deleteApp} from 'firebase/app';
import {getAuth, connectAuthEmulator, createUserWithEmailAndPassword, signInWithEmailAndPassword, reload} from 'firebase/auth';
import {initializeTestEnvironment, assertFails} from '@firebase/rules-unit-testing';
import {getFirestore, connectFirestoreEmulator, doc, getDoc, setDoc, serverTimestamp, Timestamp, collection, query, where, orderBy, documentId, limit, getDocs, startAfter} from 'firebase/firestore';
import {intent, social, publishProfile, reportIntent, report} from './daily-protocol.mjs';

assert.equal(process.env.FIRESTORE_EMULATOR_HOST,'127.0.0.1:8180');
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST,'127.0.0.1:9199');
const projectId='demo-anilove', password='AniLoveDemo2026!', seed='daily-use-v1';
const id=key=>{const h=createHash('sha256').update(`${seed}/${key}`).digest('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-4${h.slice(13,16)}-a${h.slice(17,20)}-${h.slice(20,32)}`;};
const env=await initializeTestEnvironment({projectId,firestore:{host:'127.0.0.1',port:8180}});
const apps=[], actors=[];
try {
 // Preflight happens before Auth/data changes. Preserve existing rollout/holds/unknown data.
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore(), policy=(await getDoc(doc(db,'system/communityPolicy'))).data();
  assert(!policy || policy.enabled===false,'Existing public rollout is enabled: refusing to alter it');
  const marker=(await getDoc(doc(db,`_demoSeeds/${seed}`))).data();
  assert(!marker || marker.version===1,'Unknown fixture version');
  for(const [path,value] of Object.entries({
   'system/communityPolicy':{schemaVersion:1,policyVersion:1,enabled:false,publicationMode:'post_moderated',updatedAt:Timestamp.now()},
   'system/reportPolicy':{schemaVersion:1,policyVersion:1,intakeEnabled:true,supportedTargetKinds:['post','comment'],updatedAt:Timestamp.now()},
   'system/config':{schemaVersion:1,environmentLabel:'local_emulator',featureMode:'course_demo',updatedAt:Timestamp.now()}
  })) if(!(await getDoc(doc(db,path))).exists()) await setDoc(doc(db,path),value);
 });
 for(const [i,name] of ['Mai Demo','An Demo','Linh Demo'].entries()){
  const email=['mai','an','linh'][i]+'@demo.anilove.test';
  const app=initializeApp({projectId,apiKey:'fake-api-key',appId:`demo-seed-${i}`},`seed-${i}`);apps.push(app);
  const auth=getAuth(app);connectAuthEmulator(auth,'http://127.0.0.1:9199',{disableWarnings:true});
  let account;try{account=await signInWithEmailAndPassword(auth,email,password);}catch(e){if(e.code!=='auth/invalid-credential'&&e.code!=='auth/user-not-found')throw e;account=await createUserWithEmailAndPassword(auth,email,password);}
  // Emulator-only trusted verification; no real email or provider is involved.
  const response=await fetch('http://127.0.0.1:9199/identitytoolkit.googleapis.com/v1/accounts:update?key=fake-api-key',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer owner'},body:JSON.stringify({localId:account.user.uid,emailVerified:true})});
  assert(response.ok,'Local account verification failed');await reload(account.user);await account.user.getIdToken(true);assert(account.user.emailVerified);
  const uid=account.user.uid,db=getFirestore(app);connectFirestoreEmulator(db,'127.0.0.1',8180);
  if(!(await getDoc(doc(db,`users/${uid}`))).exists())await setDoc(doc(db,`users/${uid}`),{uid,schemaVersion:1,createdAt:serverTimestamp()});
  if(!(await getDoc(doc(db,`communityMemberships/${uid}`))).exists())await setDoc(doc(db,`communityMemberships/${uid}`),{uid,schemaVersion:1,status:'active',membershipVersion:1,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
  await env.withSecurityRulesDisabled(async c=>{
   const trusted=c.firestore(),draft=`users/${uid}/profileDraft/current`;
   if(!(await getDoc(doc(trusted,draft))).exists()){
    // Initialize only a new fixture profile. Existing moderation holds are never reset.
    const hold=doc(trusted,`profileModeration/${uid}`);
    assert(!(await getDoc(hold)).exists(),'Existing profile moderation without draft; stop for inventory');
    await setDoc(doc(trusted,draft),{ownerUid:uid,schemaVersion:1,version:1,displayName:name,avatarKey:i===1?'dog':'cat',regionCode:['north','central','south'][i],bio:'Synthetic demo account — pet stories and everyday care.',submission:'draft',lastOperationId:id(`profile-${i}`),updatedAt:Timestamp.now()});
    await setDoc(hold,{schemaVersion:1,ownerUid:uid,hold:'clear',moderationVersion:1,reasonCode:null,decisionId:null,updatedAt:Timestamp.now()});
   }
  });
  if(i<2 && !(await getDoc(doc(db,`publicProfileControls/${uid}`))).exists())await publishProfile(db,uid,id(`profile-publish-${i}`));
  actors.push({uid,db,email});
 }
 const stories=[
  'A sunny window is the best seat in the house. Our cat spent the afternoon watching birds.',
  'Today’s short walk became a sniffing adventure. We brought water and took plenty of breaks.',
  'We made a quiet resting corner with a familiar blanket. Small routines make a big difference.',
  'Puzzle time: hiding a few treats kept our curious companion busy this morning.',
  'A new cardboard box arrived, and the cat has already claimed it. What simple toys do yours enjoy?',
  'Practising a calm greeting today. A little patience and a predictable routine helped us settle.'
 ];
 for(let i=0;i<42;i++){
  const postId=id(`post-${i}`),actor=actors[i%3];
  await env.withSecurityRulesDisabled(async c=>{
   const db=c.firestore(),existing=await getDoc(doc(db,`communityPosts/${postId}`));
   if(existing.exists()){assert.equal(existing.data().authorUid,actor.uid,'Fixture ID collision');return;}
   // Historical synthetic publication fixture, NOT an authorization to publish.
   // Rules are unchanged, and communityPolicy.enabled remains false throughout.
   await social(db,actor.uid,intent('publish',postId,0,0,{body:`Demo story ${i+1}\n\n${stories[i%stories.length]}`,bundledIllustrationKey:i%2?'dog':'cat',consent:true},null,0,id(`publish-${i}`)));
  });
 }
 const first=id('post-0');
 for(let i=0;i<3;i++){
  const actor=actors[i],op=id(`like-${i}`);
  if(!(await getDoc(doc(actor.db,`users/${actor.uid}/communityOperations/${op}`))).exists())await social(actor.db,actor.uid,intent('set_like',first,1,0,{active:true},null,0,op));
  const cid=id(`comment-${i}`),cop=id(`comment-op-${i}`);
  if(!(await getDoc(doc(actor.db,`users/${actor.uid}/communityOperations/${cop}`))).exists())await social(actor.db,actor.uid,intent('add_comment',first,1,0,{body:['Such a peaceful afternoon!','Our dog loves a sunny spot too.','Thanks for sharing this little moment.'][i]},cid,0,cop));
  const draftOp=id(`draft-op-${i}`);
  if(!(await getDoc(doc(actor.db,`users/${actor.uid}/communityOperations/${draftOp}`))).exists())await social(actor.db,actor.uid,intent('create_private',id(`draft-${i}`),0,0,{body:'Private demo draft: a story to finish later.',bundledIllustrationKey:null},null,0,draftOp));
 }
 const actor=actors[0],rid=id('report');
 if(!(await getDoc(doc(actor.db,`users/${actor.uid}/reportStatus/${rid}`))).exists()){
  const request=reportIntent('post',first,null,'spam','Synthetic report for testing the private received status.','submit',rid);request.operationId=id('report-op');await report(actor.db,actor.uid,request);
 }
 // Integration/security verification uses real emulator Auth tokens and the installed rules.
 const feedQuery=after=>query(collection(actor.db,'communityFeed'),where('schemaVersion','==',2),where('audience','==','community'),where('state','==','visible'),orderBy('firstPublishedAt','desc'),orderBy(documentId(),'desc'),...(after?[startAfter(after)]:[]),limit(20));
 const page1=await getDocs(feedQuery()),page2=await getDocs(feedQuery(page1.docs.at(-1)));
 assert.equal(page1.size,20);assert.equal(page2.size,20);assert(!page2.docs.some(d=>page1.docs.some(p=>p.id===d.id)));
 assert.equal((await getDoc(doc(actor.db,`communityCounts/${first}`))).data().likeCount,3);
 assert.equal((await getDoc(doc(actor.db,'system/communityPolicy'))).data().enabled,false);
 await assertFails(social(actor.db,actor.uid,intent('publish',randomUUID(),0,0,{body:'Rollout must remain blocked',bundledIllustrationKey:null,consent:true})));
 await assertFails(setDoc(doc(actor.db,`securitySubjects/${actor.uid}`),{status:'active',roles:['admin'],updatedAt:serverTimestamp()}));
 await assertFails(getDoc(doc(actors[1].db,`users/${actor.uid}/reportStatus/${rid}`)));
 const result={seed,projectId,synthetic:true,accounts:actors.map(a=>a.email),publishedFixtures:42,privateDrafts:3,comments:3,likes:3,receivedReports:1,rolloutEnabled:false,checks:['real Auth sign-in/verified tokens','two disjoint 20-item feed pages','consistent like count','new publication denied','Admin escalation denied','cross-account report denied']};
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),`_demoSeeds/${seed}`),{version:1,...result,updatedAt:Timestamp.now()}));
 console.log(JSON.stringify(result,null,2));
}finally{await Promise.all(apps.map(deleteApp));await env.cleanup();}
