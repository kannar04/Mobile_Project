// Explicit synthetic-fixture smoke test. Never runs on import or against another project.
import {readFileSync,writeFileSync} from 'node:fs';
import {randomUUID,createHash} from 'node:crypto';
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
import {initializeApp,deleteApp} from 'firebase/app';
import {getAuth,createUserWithEmailAndPassword,deleteUser} from 'firebase/auth';
import {getFirestore,doc,setDoc,getDocFromServer,serverTimestamp,terminate} from 'firebase/firestore';
const require=createRequire(import.meta.url);
if(process.argv[2]!=='--run-synthetic-fixture')throw Error('Explicit fixture flag required');
const release=JSON.parse(readFileSync('../build/spark-rules-release.json','utf8'));
assert.equal(release.released,true);
assert.equal(release.sourceSha256,createHash('sha256').update(readFileSync('../firestore.spark.rules')).digest('hex'));
const config=JSON.parse(readFileSync('../app/google-services.json','utf8'));
assert.equal(config.project_info.project_id,'aniloveproject');
const client=config.client.find(x=>x.client_info.android_client_info.package_name==='com.gmail.manhle240406.project');
const app=initializeApp({projectId:'aniloveproject',apiKey:client.api_key[0].current_key,appId:client.client_info.mobilesdk_app_id},'spark-fixture-'+randomUUID());
const auth=getAuth(app),db=getFirestore(app),email='spark-wiring-'+randomUUID()+'@example.invalid';
let own,created=false,root=false;const results={project:'aniloveproject',syntheticOnly:true,passed:[],cleanup:{}};
async function denied(operation){try{await operation;return false;}catch(e){return ['permission-denied','firestore/permission-denied'].includes(e.code);}}
try{
 own=(await createUserWithEmailAndPassword(auth,email,randomUUID()+randomUUID())).user;created=true;
 writeFileSync('../build/spark-fixture-private.json',JSON.stringify({uid:own.uid,syntheticEmail:email,createdByThisRun:true}));
 results.passed.push('actual_password_provider_sign_up');
 await setDoc(doc(db,'users',own.uid),{uid:own.uid,schemaVersion:1,createdAt:serverTimestamp()});root=true;
 const value=(await getDocFromServer(doc(db,'users',own.uid))).data();assert.equal(value.uid,own.uid);assert.equal(Object.keys(value).length,3);
 results.passed.push('own_minimal_shell_write_and_server_read');
 assert.ok(await denied(getDocFromServer(doc(db,'users','foreign-'+randomUUID()))));results.passed.push('foreign_account_read_denied');
 assert.ok(await denied(setDoc(doc(db,'communityMemberships',own.uid),{uid:own.uid,schemaVersion:1,status:'active',membershipVersion:1,createdAt:serverTimestamp(),updatedAt:serverTimestamp()})));
 results.passed.push('unverified_membership_write_denied');
 assert.ok(await denied(setDoc(doc(db,'securitySubjects',own.uid),{status:'active',roles:['admin'],updatedAt:serverTimestamp()})));
 results.passed.push('client_admin_grant_denied');
}catch(e){results.failureCode=e.code??'assertion_or_provider_failure';process.exitCode=1;}
finally{
 if(created){
  try{
   // Teardown ONLY this newly created minimal fixture, never a user-selected account/UID tree.
   // Check exact source fields and conditional updateTime before owner-CLI deletion of the one root.
   if(root){
    const {getGlobalDefaultAccount}=require('firebase-tools/lib/auth');const {requireAuth}=require('firebase-tools/lib/requireAuth');const {Client}=require('firebase-tools/lib/apiv2');
    await requireAuth({...getGlobalDefaultAccount(),project:'aniloveproject',nonInteractive:true});
    const api=new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'});
    const path=`projects/aniloveproject/databases/(default)/documents/users/${own.uid}`;
    const actual=(await api.get(path,{retries:0})).body;
    assert.equal(actual.fields.uid.stringValue,own.uid);assert.equal(Object.keys(actual.fields).length,3);assert.equal(own.email,email);
    await api.delete(path+'?currentDocument.updateTime='+encodeURIComponent(actual.updateTime),{retries:0});results.cleanup.minimalRoot=true;
   }
   assert.equal(auth.currentUser?.uid,own.uid);assert.equal(own.email,email);await deleteUser(own);results.cleanup.syntheticAuth=true;
   writeFileSync('../build/spark-fixture-private.json',JSON.stringify({cleaned:true}));
  }catch(e){results.cleanup.requiresOperator=true;results.cleanup.code=e.code??'conditional_teardown_failed';process.exitCode=1;}
 }
 await terminate(db);await deleteApp(app);
 writeFileSync('../build/spark-live-smoke.json',JSON.stringify(results,null,2));console.log(JSON.stringify(results));
}
