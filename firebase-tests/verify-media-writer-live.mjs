// Final deployed writer fence proof, using only the existing disposable fixture.
import {readFileSync,writeFileSync} from 'node:fs';
import {randomUUID,createHash} from 'node:crypto';
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
import {initializeApp,deleteApp} from 'firebase/app';
import {getAuth,signInWithEmailAndPassword} from 'firebase/auth';
const require=createRequire(import.meta.url),{mediaAdmin}=require('./media-admin.cjs'),sharp=require('../functions/node_modules/sharp');
const file='../build/private-media-live-fixture.json',f=JSON.parse(readFileSync(file)),c=JSON.parse(readFileSync('../app/google-services.json')).client.find(c=>c.client_info.android_client_info.package_name==='com.gmail.manhle240406.project');
let app,db;
try {
 assert(f.prepared&&!f.cleaned);const admin=await mediaAdmin();db=admin.db;const u=f.users[0],actual=await admin.auth.getUser(u.uid);assert.equal(actual.email,u.email);assert(/^media-flow-[a-f0-9-]+@example\.invalid$/.test(u.email));
 const password=randomUUID()+randomUUID();await admin.auth.updateUser(u.uid,{password});app=initializeApp({projectId:'aniloveproject',apiKey:c.api_key[0].current_key,appId:c.client_info.mobilesdk_app_id},'writer-'+randomUUID());const user=(await signInWithEmailAndPassword(getAuth(app),u.email,password)).user;
 async function call(route,input,raw=false){const r=await fetch('https://asia-east2-aniloveproject.cloudfunctions.net/mediaApi/'+route,{method:'POST',headers:{Authorization:'Bearer '+await user.getIdToken(), 'Content-Type':raw?'image/jpeg':'application/json',...(raw?{'X-Asset-Id':input.assetId}:{})},body:raw?input.bytes:JSON.stringify(input),signal:AbortSignal.timeout(65000)});if(!r.ok)throw Error((await r.json()).error);return route==='read'?Buffer.from(await r.arrayBuffer()):r.json();}
 const bytes=await sharp({create:{width:120,height:90,channels:3,background:'#456851'}}).jpeg().toBuffer(),input={assetId:randomUUID(),operationId:randomUUID(),purpose:'post_image',inputHash:createHash('sha256').update(bytes).digest('hex')};
 f.assets.push({uid:u.uid,assetId:input.assetId,operationId:input.operationId});writeFileSync(file,JSON.stringify(f,null,2));
 await call('reserve',input);await call('receive',{assetId:input.assetId,bytes},true);
 const a=(await db.doc('mediaAssets/'+input.assetId).get()).data();assert.equal(a.writerActive,false);assert.equal(a.state,'ready');assert((await call('read',{assetId:input.assetId,variant:'display'})).length>0);
 const removed=await call('remove',{assetId:input.assetId});assert.equal(removed.state,'deleted');
 const result={deployedHttpUpload:true,acknowledgedWriterInactive:true,decodedDisplayAvailable:true,immediateCleanupAfterAcknowledgedWriter:true,uploadsEnabled:(await db.doc('mediaPolicy/current').get()).data().enabled,phoneAccountModified:false};assert(result.uploadsEnabled);writeFileSync('../build/media-writer-live-results.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
}catch(e){console.error(JSON.stringify({verified:false,reason:e.code??e.message}));process.exitCode=1;}finally{if(app)await deleteApp(app);if(db)await db.terminate();}
