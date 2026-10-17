// Fixed-project, exact-approval and tested-source release. No data/service/index/billing writes.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {getGlobalDefaultAccount}=require('firebase-tools/lib/auth');
const {requireAuth}=require('firebase-tools/lib/requireAuth');
const {Client}=require('firebase-tools/lib/apiv2');
const project='aniloveproject',apply=process.argv.includes('--apply');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const root=path.resolve(__dirname,'..'),read=n=>fs.readFileSync(path.join(root,n));
const json=n=>JSON.parse(read(n));
(async()=>{
 const planning='C:/Users/Minht/Desktop/New folder';
 for(const [feature,revision]of [['F01',3],['F03',3],['F31',5],['F42',3]]){
  const receipt=JSON.parse(fs.readFileSync(path.join(planning,`approval_review/${feature}.r${revision}.approved.json`),'utf8'));
  assert.equal(receipt.feature_id,feature);assert.equal(receipt.analysis_revision,revision);
  assert.equal(hash(fs.readFileSync(path.join(planning,receipt.analysis_path))),receipt.approved_artifact_sha256);
 }
 const source=read('firestore.spark.rules').toString('utf8'),sourceSha256=hash(Buffer.from(source)),proof=json('build/ux-rule-test-proof.json');
 assert.equal(proof.selectedRulesSha256,sourceSha256);assert.equal(proof.canonicalRulesSha256,hash(read('firestore.rules')));
 for(const name of ['ux-rules-regression.log','ux-spark-rules.log']){
  const log=read('build/'+name);assert.equal(hash(log),proof[name].sha256);assert.equal(proof[name].failures,0);
  assert.ok(Date.now()-fs.statSync(path.join(root,'build',name)).mtimeMs<12*60*60*1000,'stale_test_proof');
 }
 assert.equal(json('build/spark-inspection.json').billing.billingEnabled,false);
 await requireAuth({...getGlobalDefaultAccount(),project,nonInteractive:true});
 const api=new Client({urlPrefix:'https://firebaserules.googleapis.com',apiVersion:'v1'});
 const before=(await api.get(`projects/${project}/releases/cloud.firestore`,{retries:0})).body;
 const current=(await api.get(before.rulesetName,{retries:0})).body;
 const previousSha256=hash(Buffer.from(current.source.files.find(f=>f.name.endsWith('.rules')).content));
 assert.ok(previousSha256==='8dc05fad0398a7a6647003501bbde1b62bf22a62ca57b35a51a1667122646721'||previousSha256===sourceSha256,'unexpected_live_rules_change');
 const result={project,apply,sourceSha256,previousSha256,tested:true,billingEnabled:false,released:previousSha256===sourceSha256};
 if(apply&&!result.released){
  const compiled=(await api.post(`projects/${project}/rulesets`,{source:{files:[{name:'firestore.rules',content:source}]}},{retries:0,skipLog:{body:true}})).body;
  assert.ok(compiled.name,'provider_compile_failed');
  await api.patch(`projects/${project}/releases/cloud.firestore`,{release:{name:`projects/${project}/releases/cloud.firestore`,rulesetName:compiled.name}},{retries:0});
  const after=(await api.get(`projects/${project}/releases/cloud.firestore`,{retries:0})).body;
  assert.equal(after.rulesetName,compiled.name);result.released=true;result.providerCompiled=true;
 }
 fs.writeFileSync(path.join(root,'build/ux-rules-release.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
})().catch(()=>{console.error('UX rules release failed its approval/test/live-source/provider guard; no completion claimed.');process.exitCode=1;});
