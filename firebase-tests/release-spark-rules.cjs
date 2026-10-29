// Provider-side compilation via the supported Ruleset API when CLI :test returns 503.
// Releases only the exact emulator-tested source; no policy/data/roles/billing changes.
const {getGlobalDefaultAccount}=require('firebase-tools/lib/auth');
const {requireAuth}=require('firebase-tools/lib/requireAuth');
const {Client}=require('firebase-tools/lib/apiv2');
const fs=require('node:fs'),crypto=require('node:crypto');
if(process.argv[2]!=='--release-tested-source')throw Error('Explicit release flag required');
(async()=>{
 const project='aniloveproject',source=fs.readFileSync('../firestore.spark.rules','utf8');
 const summary={project,sourceSha256:crypto.createHash('sha256').update(source).digest('hex'),providerCompiled:false,released:false};
 try{
  const inspection=JSON.parse(fs.readFileSync('../build/spark-inspection.json','utf8'));
  if(inspection.billing.billingEnabled!==false||inspection.rootCollectionCount!==0||!inspection.rootInventoryComplete)throw Error('preflight_changed');
  const regression=fs.readFileSync('../build/wiring-protected-regression.txt');const rules=fs.readFileSync('../build/wiring-rules.txt');
  const decode=b=>b[0]===255&&b[1]===254?b.toString('utf16le'):b.toString('utf8');
  if(!decode(regression).includes('# pass 34')||!decode(rules).includes('# pass 9'))throw Error('evidence_missing');
  if(!decode(fs.readFileSync('../build/wiring-spark-rules.txt')).includes('# pass 9'))throw Error('spark_evidence_missing');
  await requireAuth({...getGlobalDefaultAccount(),project,nonInteractive:true});
  const api=new Client({urlPrefix:'https://firebaserules.googleapis.com',apiVersion:'v1'});
  const before=(await api.get(`projects/${project}/releases/cloud.firestore`,{retries:0})).body;
  summary.previousRuleset=before.rulesetName;
  let compiled;
  const savedPath='../build/spark-rules-release.json';
  if(fs.existsSync(savedPath)){
   const saved=JSON.parse(fs.readFileSync(savedPath,'utf8'));
   if(saved.sourceSha256===summary.sourceSha256&&saved.compiledRuleset)compiled=(await api.get(saved.compiledRuleset,{retries:0})).body;
  }
  if(!compiled)compiled=(await api.post(`projects/${project}/rulesets`,{source:{files:[{name:'firestore.rules',content:source}]}},{retries:0,skipLog:{body:true}})).body;
  summary.providerCompiled=true;
  summary.compiledRuleset=compiled.name;
  summary.services=compiled.metadata?.services;
  await api.patch(`projects/${project}/releases/cloud.firestore`,{release:{name:`projects/${project}/releases/cloud.firestore`,rulesetName:compiled.name}},{retries:0});
  const after=(await api.get(`projects/${project}/releases/cloud.firestore`,{retries:0})).body;
  summary.released=after.rulesetName===compiled.name;summary.ruleset=compiled.name;
  if(!summary.released)throw Error('release_not_confirmed');
 }catch(e){summary.failureStatus=e.status??e.context?.response?.statusCode??'provider_or_preflight_failure';summary.providerError=e.message;summary.providerDetails=e.context?.body??e.context?.response?.body;process.exitCode=1;}
 fs.writeFileSync('../build/spark-rules-release.json',JSON.stringify(summary,null,2));console.log(JSON.stringify(summary));
})().catch(()=>{console.log('Rules release unavailable; do not claim deployment.');process.exitCode=1;});
