// Approved fictional course reference data only. Existing IDs are never overwritten.
const fs=require('node:fs'), path=require('node:path'), assert=require('node:assert/strict');
const {createHash}=require('node:crypto');
const {isDeepStrictEqual}=require('node:util');
const project='aniloveproject', base=`projects/${project}/databases/(default)/documents`;
const operationId='course-catalog-seed-r1', actorUid='trusted-course-seed-tool';
const entries=[];
function entry(collection,id,kind,vi,en,extra={}) {
  entries.push({path:`${collection}/${id}`,data:{schemaVersion:1,id,kind,labels:{vi,en},normalizedLabel:en.toLowerCase(),sortKey:id,state:'active',version:1,...extra}});
}
entry('catalogSpecies','cat','species','Mèo','Cat');
entry('catalogSpecies','dog','species','Chó','Dog');
entry('catalogSpecies','unknown','species','Chưa rõ loài','Unknown species');
for(const speciesId of ['cat','dog','unknown']) for(const variant of ['unknown','mixed'])
  entry('catalogBreeds',`${speciesId}_${variant}`,'breed',variant==='unknown'?'Chưa rõ giống':'Giống lai',variant==='unknown'?'Unknown breed':'Mixed breed',{speciesId,variant});
for(const [id,vi,en] of [['demo_north','Khu Bắc (mô phỏng)','Demo North'],['demo_central','Khu Trung (mô phỏng)','Demo Central'],['demo_south','Khu Nam (mô phỏng)','Demo South']])
  entry('catalogRegions',id,'region',vi,en,{level:'coarse',parentRegionId:null});
function encode(v) {
  if(v===null)return {nullValue:null};
  if(typeof v==='boolean')return {booleanValue:v};
  if(typeof v==='string')return {stringValue:v};
  if(typeof v==='number')return {integerValue:String(v)};
  if(Array.isArray(v))return {arrayValue:{values:v.map(encode)}};
  if(v&&typeof v==='object')return {mapValue:{fields:fields(v)}};
  throw Error('unsupported_value');
}
function fields(v){return Object.fromEntries(Object.entries(v).map(([k,value])=>[k,encode(value)]));}
const digest=createHash('sha256').update(JSON.stringify(entries)).digest('hex');
const records=[];
for(const e of entries) {
  assert.match(e.data.id,/^[a-z0-9][a-z0-9_-]{0,79}$/);
  assert.ok(Buffer.byteLength(JSON.stringify(e.data))<2048);
  if(e.data.kind==='breed')assert.ok(entries.some(p=>p.data.kind==='species'&&p.data.id===e.data.speciesId));
  records.push({path:e.path,data:e.data,timestamps:['updatedAt']});
  // Flattened immutable sanitized snapshot, same commit time as the current entry.
  records.push({path:`${e.path}/revisions/v1`,data:{...e.data,actorUid,reason:'Initial fictional course catalog seed; regions are not real geographic claims.',operationId},timestamps:['updatedAt','serverCreatedAt']});
}
records.push({path:`catalogOperations/${operationId}`,data:{schemaVersion:1,actorUid,inputDigest:digest,expectedVersion:0,resultVersion:1,result:'created',entryIds:entries.map(e=>e.path)},timestamps:['serverCreatedAt']});
function matches(doc,r){const actual={...doc.fields};for(const k of r.timestamps){if(typeof actual[k]?.timestampValue!=='string')return false;delete actual[k];}return isDeepStrictEqual(actual,fields(r.data));}
async function seed(client,apply,targetRecords=records){
  const missing=[],present=[];
  for(const r of targetRecords){let doc;try{doc=(await client.get(`${base}/${r.path}`,{retries:0})).body;}catch(e){if((e.status??e.context?.response?.statusCode)!==404)throw e;}
    if(doc){if(!matches(doc,r))throw Error(`existing_record_conflict:${r.path}`);present.push(r.path);}else missing.push(r);
  }
  // A partial prior dataset requires investigation rather than fabricating history.
  if(present.length&&missing.length)throw Error('partial_seed_conflict');
  if(apply&&missing.length)await client.post(`${base}:commit`,{writes:missing.map(r=>({update:{name:`${base}/${r.path}`,fields:fields(r.data)},updateTransforms:r.timestamps.map(fieldPath=>({fieldPath,setToServerValue:'REQUEST_TIME'})),currentDocument:{exists:false}}))},{retries:0});
  return {created:apply?missing.length:0,pending:apply?0:missing.length,preserved:present.length};
}
async function selfTest(){const docs=new Map();let commits=0;const client={get:async p=>{if(!docs.has(p))throw {status:404};return {body:docs.get(p)};},post:async(_,b)=>{commits++;assert.equal(b.writes.length,25);for(const w of b.writes){assert.equal(w.currentDocument.exists,false);const d={...w.update.fields};for(const t of w.updateTransforms)d[t.fieldPath]={timestampValue:'2026-10-17T00:00:00Z'};docs.set(w.update.name,{fields:d});}}};
  assert.equal((await seed(client,false)).pending,25);assert.equal(commits,0);
  assert.equal((await seed(client,true)).created,25);assert.equal((await seed(client,true)).preserved,25);assert.equal(commits,1);
  docs.delete(`${base}/${records[0].path}`);await assert.rejects(seed(client,true),/partial_seed_conflict/);assert.equal(commits,1);
  docs.get(`${base}/${records[2].path}`).fields.version={integerValue:'2'};await assert.rejects(seed(client,true),/existing_record_conflict/);
  console.log('PASS catalog validation, dry-run, atomic create-only snapshots/receipt, idempotency, partial/conflicting data preservation');
}
async function main(){if(process.argv.includes('--self-test'))return selfTest();
  const planning='C:/Users/Minht/Desktop/New folder';
  const receipt=JSON.parse(fs.readFileSync(path.join(planning,'approval_review/F11.r1.approved.json')));
  assert.equal(createHash('sha256').update(fs.readFileSync(path.join(planning,receipt.analysis_path))).digest('hex'),receipt.approved_artifact_sha256);
  const {getGlobalDefaultAccount}=require('firebase-tools/lib/auth'),{requireAuth}=require('firebase-tools/lib/requireAuth'),{Client}=require('firebase-tools/lib/apiv2');
  const account=getGlobalDefaultAccount();if(!account)throw Error('login_required');await requireAuth({...account,project,nonInteractive:true});
  const billing=new Client({urlPrefix:'https://cloudbilling.googleapis.com',apiVersion:'v1'});
  const check=async()=>assert.equal((await billing.get(`projects/${project}/billingInfo`,{retries:0})).body.billingEnabled,false);
  await check();const client=new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'}),apply=process.argv.includes('--apply');
  const result=await seed(client,apply);
  if(apply)for(const r of records)assert.ok(matches((await client.get(`${base}/${r.path}`,{retries:0})).body,r));
  await check();const report={project,apply,...result,verified:apply,entries:entries.length,revisions:entries.length,operationReceipts:1,inputDigest:digest,billingEnabled:false,fictionalRegions:true};
  fs.writeFileSync(path.join(__dirname,'../build/spark-catalog-seed.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));
}
if(require.main===module)main().catch(e=>{console.error(`Catalog seed stopped: ${e.message??'provider_error'}`);process.exitCode=1;});
module.exports={entries,records,seed,matches};
