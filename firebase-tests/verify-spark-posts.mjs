import {readFileSync,writeFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
import {initializeApp,deleteApp} from 'firebase/app';
import {getAuth,createUserWithEmailAndPassword,deleteUser} from 'firebase/auth';
import {getFirestore,collection,query,where,orderBy,documentId,limit,getDocs,getDocFromServer,doc,setDoc,terminate} from 'firebase/firestore';
const require=createRequire(import.meta.url),{postIds}=require('./seed-spark-posts.cjs');
const config=JSON.parse(readFileSync(new URL('../app/google-services.json',import.meta.url)));
assert.equal(config.project_info.project_id,'aniloveproject');
const c=config.client.find(c=>c.client_info.android_client_info.package_name==='com.gmail.manhle240406.project');
const app=initializeApp({projectId:'aniloveproject',apiKey:c.api_key[0].current_key,appId:c.client_info.mobilesdk_app_id},'posts-check-'+randomUUID());
const auth=getAuth(app),db=getFirestore(app),passed=[];let own;
const result={project:'aniloveproject',passed,cleanup:false};
const feed=()=>getDocs(query(collection(db,'communityFeed'),where('schemaVersion','in',[2,3]),where('audience','==','community'),where('state','==','visible'),orderBy('firstPublishedAt','desc'),orderBy(documentId(),'desc'),limit(20)));
try{
 await assert.rejects(feed,e=>e.code==='permission-denied');passed.push('anonymous_feed_denied');
 own=(await createUserWithEmailAndPassword(auth,`post-check-${randomUUID()}@example.invalid`,randomUUID()+randomUUID())).user;
 const page=await feed();for(const id of postIds)assert.ok(page.docs.some(d=>d.id===id));passed.push('app_mixed_schema_feed_query_all_six');
 for(const id of postIds){const story=(await getDocFromServer(doc(db,`communityFeed/${id}`))).data();assert.equal(story.schemaVersion,3);assert.equal(story.controlVersion,1);assert.ok(story.body.startsWith('[DEMO'));assert.equal((await getDocFromServer(doc(db,`communityCounts/${id}`))).data().likeCount,0);}
 passed.push('six_exact_story_reads_and_zero_counters');
 await assert.rejects(()=>setDoc(doc(db,`communityFeed/${postIds[0]}`),{body:'overwrite'},{merge:true}),e=>e.code==='permission-denied');passed.push('foreign_story_edit_denied');
 const policy=(await getDocFromServer(doc(db,'system/communityPolicy'))).data();assert.equal(policy.enabled,false);passed.push('publication_rollout_still_disabled');
}catch(e){result.failureCode=e.code??'assertion_failure';process.exitCode=1;}
finally{
 if(own){try{await deleteUser(own);result.cleanup=true;}catch{process.exitCode=1;}}
 await terminate(db);await deleteApp(app);
 writeFileSync(new URL('../build/spark-post-client-check.json',import.meta.url),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
}
