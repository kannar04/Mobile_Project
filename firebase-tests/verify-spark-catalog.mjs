import {readFileSync,writeFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import assert from 'node:assert/strict';
import {initializeApp,deleteApp} from 'firebase/app';
import {getAuth,createUserWithEmailAndPassword,deleteUser} from 'firebase/auth';
import {getFirestore,collection,query,where,orderBy,documentId,limit,getDocs,getDocFromServer,doc,setDoc,terminate} from 'firebase/firestore';
const config=JSON.parse(readFileSync(new URL('../app/google-services.json',import.meta.url)));
assert.equal(config.project_info.project_id,'aniloveproject');
const c=config.client.find(c=>c.client_info.android_client_info.package_name==='com.gmail.manhle240406.project');
const app=initializeApp({projectId:'aniloveproject',apiKey:c.api_key[0].current_key,appId:c.client_info.mobilesdk_app_id},'catalog-check-'+randomUUID());
const auth=getAuth(app),db=getFirestore(app),passed=[];let own;
const result={project:'aniloveproject',passed,cleanup:false};
async function page(name,clauses=[]){return getDocs(query(collection(db,name),where('state','==','active'),...clauses,orderBy('sortKey'),orderBy(documentId()),limit(50)));}
try{
  await assert.rejects(()=>page('catalogSpecies'),e=>e.code==='permission-denied');passed.push('anonymous_catalog_denied');
  own=(await createUserWithEmailAndPassword(auth,`catalog-check-${randomUUID()}@example.invalid`,randomUUID()+randomUUID())).user;
  const speciesPage=await page('catalogSpecies');assert.equal(speciesPage.size,7);for(const id of ['cat','dog','unknown','rabbit','bird','fish','hamster'])assert.ok(speciesPage.docs.some(row=>row.id===id));passed.push('signed_in_species_query');
  assert.ok((await page('catalogRegions')).size>=3);passed.push('signed_in_coarse_regions_query');
  for(const species of ['cat','dog','unknown']){
    const breeds=await page('catalogBreeds',[where('speciesId','==',species)]);assert.equal(breeds.size,2);
    for(const breed of breeds.docs){assert.equal(breed.data().speciesId,species);assert.ok(['unknown','mixed'].includes(breed.data().variant));}
  }passed.push('all_species_bound_breed_queries');
  assert.equal((await getDocFromServer(doc(db,'catalogSpecies/cat/revisions/v1'))).data().version,1);passed.push('version_snapshot_read');
  await assert.rejects(()=>setDoc(doc(db,'catalogSpecies/cat'),{state:'retired'},{merge:true}),e=>e.code==='permission-denied');passed.push('member_catalog_edit_denied');
  await assert.rejects(()=>getDocFromServer(doc(db,'catalogOperations/course-catalog-seed-r1')),e=>e.code==='permission-denied');passed.push('operator_receipt_private');
}catch(e){result.failureCode=e.code??'assertion_failure';result.failureMessage=e.message;process.exitCode=1;}
finally{
  if(own){try{await deleteUser(own);result.cleanup=true;}catch{result.cleanup=false;process.exitCode=1;}}
  await terminate(db);await deleteApp(app);
  writeFileSync(new URL('../build/spark-catalog-client-check.json',import.meta.url),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
}
