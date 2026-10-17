// Read-only configuration check using existing CLI login. Never prints tokens, keys or account emails.
const {getGlobalDefaultAccount}=require('firebase-tools/lib/auth');
const {requireAuth}=require('firebase-tools/lib/requireAuth');
const {Client}=require('firebase-tools/lib/apiv2');
const fs=require('node:fs');
const project='aniloveproject';
(async()=>{
 const account=getGlobalDefaultAccount();if(!account)throw Error('login_required');
 await requireAuth({...account,project,nonInteractive:true});
 const get=async(origin,version,path)=>new Client({urlPrefix:origin,apiVersion:version}).get(path,{retries:0});
 const summary={project,readOnly:true};
 for(const [key,run] of [
  ['billing',async()=>{const r=await get('https://cloudbilling.googleapis.com','v1',`projects/${project}/billingInfo`);return {billingEnabled:r.body.billingEnabled};}],
  ['auth',async()=>{const r=await get('https://identitytoolkit.googleapis.com','v2',`projects/${project}/config`);const b=r.body;const callback=b.notification?.sendEmail?.callbackUri;let hosted=!callback;try{if(callback)hosted=new URL(callback).hostname===`${project}.firebaseapp.com`&&new URL(callback).protocol==='https:';}catch{}return {passwordEnabled:b.signIn?.email?.enabled,enumerationProtection:b.emailPrivacyConfig?.enableImprovedEmailPrivacy===true,authorizedDomainCount:b.authorizedDomains?.length??0,defaultHostedDomainAuthorized:(b.authorizedDomains??[]).includes(`${project}.firebaseapp.com`),hostedHandler:hosted,passwordPolicyConfigured:!!b.passwordPolicyConfig};}],
  ['firestore',async()=>{const r=await get('https://firestore.googleapis.com','v1',`projects/${project}/databases/(default)`);return {exists:true,type:r.body.type,location:r.body.locationId};}],
  ['indexes',async()=>{const r=await get('https://firestore.googleapis.com','v1',`projects/${project}/databases/(default)/collectionGroups/-/indexes`);const indexes=r.body.indexes??[];const states={};for(const i of indexes)states[i.state]=(states[i.state]??0)+1;const expected=JSON.parse(fs.readFileSync('../firestore.spark.indexes.json','utf8')).indexes;const signature=i=>JSON.stringify([i.collectionGroup??i.name.match(/collectionGroups\/([^/]+)\//)?.[1],i.queryScope,i.fields.filter(f=>f.fieldPath!=="__name__").map(f=>[f.fieldPath,f.order??f.arrayConfig])]);return {count:indexes.length,complete:!r.body.nextPageToken,states,wiredExpected:expected.length,wiredReady:expected.filter(e=>indexes.some(i=>i.state==='READY'&&signature(i)===signature(e))).length};}],
  ['rules',async()=>{const r=await get('https://firebaserules.googleapis.com','v1',`projects/${project}/releases/cloud.firestore`);const s=await get('https://firebaserules.googleapis.com','v1',r.body.rulesetName);const files=s.body.source?.files??[];const f=files.find(x=>x.name.endsWith('.rules'))??files[0];if(f)fs.writeFileSync('../build/cloud-current.rules',f.content);const hash=require('node:crypto').createHash('sha256').update(f?.content??'').digest('hex');return {exists:!!f,sourceSha256:hash,matchesSparkSource:hash===require('node:crypto').createHash('sha256').update(fs.readFileSync('../firestore.spark.rules')).digest('hex')};}]
 ]) { try{summary[key]=await run();}catch(e){summary[key]={unverified:true,status:e.status??e.context?.response?.statusCode??'request_failed'};} }
 summary.inventory={};
 try { const r=await new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'}).post(`projects/${project}/databases/(default)/documents:listCollectionIds`,{pageSize:100});
  summary.rootCollectionCount=(r.body.collectionIds??[]).length;summary.rootInventoryComplete=!r.body.nextPageToken;
 } catch(e) {summary.rootInventoryComplete=false;}
 for(const collection of ['users','communityMemberships','posts','feed','communityPosts','communityFeed','securitySubjects']) {
  try { const r=await get('https://firestore.googleapis.com','v1',`projects/${project}/databases/(default)/documents/${collection}?pageSize=1&mask.fieldPaths=__name__`);
   summary.inventory[collection]={hasDocuments:(r.body.documents??[]).length>0};
  } catch(e) { summary.inventory[collection]={unverified:true}; }
 }
 fs.writeFileSync('../build/spark-inspection.json',JSON.stringify(summary,null,2));console.log(JSON.stringify(summary));
})().catch(()=>{console.log('Read-only inspection unavailable; no project changes made.');process.exitCode=1;});
