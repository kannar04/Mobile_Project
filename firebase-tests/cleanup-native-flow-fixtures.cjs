// Owner-CLI teardown ONLY identities recorded by this task's on-device synthetic fixtures.
// No arbitrary UID input, account scans, recursive deletion or production-account operations.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const project='aniloveproject',root=`projects/${project}/databases/(default)/documents`;
const recorded=new Set();
for(const relative of ['../build/native-flow-fixture-private.json','../build/native-flow-final-evidence/fixture-private.json','../build/native-flow-large-evidence/fixture-private.json','../build/native-flow-confirmed-evidence/fixture-private.json']){
 const file=path.join(__dirname,relative);if(fs.existsSync(file)){const record=JSON.parse(fs.readFileSync(file));assert.equal(record.createdByThisRun,true);recorded.add(record.uid);}
}
for(const relative of ['../build/native-flow-final-evidence/fixture-history-private.jsonl','../build/native-flow-large-evidence/fixture-history-private.jsonl','../build/native-flow-confirmed-evidence/fixture-history-private.jsonl']){
 const file=path.join(__dirname,relative);if(fs.existsSync(file))for(const line of fs.readFileSync(file,'utf8').trim().split('\n').filter(Boolean)){const record=JSON.parse(line);assert.equal(record.createdByThisRun,true);recorded.add(record.uid);}
}
(async()=>{
 assert.ok(recorded.size>0&&recorded.size<=16,'fixture_count_guard');for(const id of recorded)assert.match(id,/^[A-Za-z0-9_-]{1,128}$/);
 const {getGlobalDefaultAccount}=require('firebase-tools/lib/auth'),{requireAuth}=require('firebase-tools/lib/requireAuth'),{Client}=require('firebase-tools/lib/apiv2');
 const account=getGlobalDefaultAccount();if(!account)throw Error('login_required');await requireAuth({...account,project,nonInteractive:true});
 const db=new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'}),auth=new Client({urlPrefix:'https://identitytoolkit.googleapis.com',apiVersion:'v1'}),billing=new Client({urlPrefix:'https://cloudbilling.googleapis.com',apiVersion:'v1'});
 assert.equal((await billing.get(`projects/${project}/billingInfo`,{retries:0})).body.billingEnabled,false);
 async function get(id){try{return (await db.get(`${root}/${id}`,{retries:0})).body;}catch(error){if((error.status??error.context?.response?.statusCode)===404)return null;throw error;}}
 async function lookup(uid){const reply=await auth.post(`projects/${project}/accounts:query`,{expression:[{userId:uid}],limit:'1'},{retries:0});const users=reply.body.userInfo??[];assert.ok(users.length<=1);return users[0]??null;}
 const plan=[];
 // Validate ALL recorded fixtures before any mutation.
 for(const uid of recorded){
  const user=await lookup(uid);if(user){assert.equal(user.localId,uid);assert.match(user.email??'',/^native-flow-[a-f0-9-]+@example\.invalid$/);assert.ok(!user.customAttributes||user.customAttributes==='{}');assert.ok(!user.emailVerified);}
  const doc=await get(`users/${uid}`);
  if(doc){assert.deepEqual(Object.keys(doc.fields).sort(),['createdAt','schemaVersion','uid']);assert.equal(doc.fields.uid.stringValue,uid);assert.equal(doc.fields.schemaVersion.integerValue,'1');assert.ok(doc.fields.createdAt.timestampValue);}
  const collections=await db.post(`${root}/users/${uid}:listCollectionIds`,{pageSize:10},{retries:0});assert.equal((collections.body.collectionIds??[]).length,0);assert.ok(!collections.body.nextPageToken);
  for(const name of ['securitySubjects','communityMemberships'])assert.equal(await get(`${name}/${uid}`),null);
  plan.push({uid,user,doc});
 }
 const apply=process.argv.includes('--cleanup-recorded-fixtures');let authDeleted=0,rootsDeleted=0;
 if(apply)for(const item of plan){
  if(item.user){await auth.post(`projects/${project}/accounts:delete`,{localId:item.uid},{retries:0});authDeleted++;}
  if(item.doc){await db.post(`${root}:commit`,{writes:[{delete:`${root}/users/${item.uid}`,currentDocument:{updateTime:item.doc.updateTime}}]},{retries:0});rootsDeleted++;}
  assert.equal(await lookup(item.uid),null);assert.equal(await get(`users/${item.uid}`),null);
 }
 const report={project,apply,recordedSyntheticIdentities:plan.length,authDeleted,minimalRootsDeleted:rootsDeleted,verifiedClean:apply,billingEnabled:false};
 fs.writeFileSync(path.join(__dirname,'../build/native-flow-cleanup.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));
})().catch(error=>{console.error('Synthetic fixture teardown stopped: '+(error.code??error.status??error.message?.split(':')[0]??'validation_failure'));process.exitCode=1;});
