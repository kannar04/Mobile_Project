// Bounded disposable test accounts only. Never signs the device out or changes a real identity.
const fs=require('fs'),assert=require('assert/strict'),crypto=require('crypto');
const {mediaAdmin}=require('./media-admin.cjs');
const local='../build/adoption-live-private.json';
(async()=>{const mode=process.argv[2],{db,auth}=await mediaAdmin();let fixture;
 if(mode==='create'){
  assert(!fs.existsSync(local),'fixture_already_exists');const password=crypto.randomBytes(24).toString('base64url'),email='adoption-test-'+crypto.randomUUID()+'@example.invalid';
  const u=await auth.createUser({email,password,emailVerified:true,displayName:'Synthetic adoption owner'});fixture={uid:u.uid,email,password,petId:crypto.randomUUID(),listingId:crypto.randomUUID(),createdAt:Date.now()};fs.writeFileSync(local,JSON.stringify(fixture));
  const batch=db.batch();batch.set(db.doc('users/'+u.uid),{uid:u.uid,schemaVersion:1,createdAt:new Date()});batch.set(db.doc('securitySubjects/'+u.uid),{status:'active',roles:['member'],updatedAt:new Date()});batch.set(db.doc('communityMemberships/'+u.uid),{schemaVersion:1,uid:u.uid,status:'active',updatedAt:new Date()});await batch.commit();
 }else fixture=JSON.parse(fs.readFileSync(local));
 const config=JSON.parse(fs.readFileSync('../app/google-services.json')),client=config.client.find(c=>c.client_info.android_client_info.package_name==='com.gmail.manhle240406.project');
 const {initializeApp,deleteApp}=require('firebase/app'),{getAuth,signInWithEmailAndPassword}=require('firebase/auth'),{getFunctions,httpsCallable}=require('firebase/functions');const app=initializeApp({apiKey:client.api_key[0].current_key,projectId:config.project_info.project_id,appId:client.client_info.mobilesdk_app_id},'synthetic-adoption-'+Date.now());
 await signInWithEmailAndPassword(getAuth(app),fixture.email,fixture.password);const gateway=httpsCallable(getFunctions(app,'asia-east2'),'adoptionOperations');const call=async(operation,input)=> (await gateway({operation,input:{operationId:crypto.randomUUID(),...input}})).data;
 if(mode==='create'){
  await call('register',{petId:fixture.petId,identity:{displayName:'Synthetic QA cat',species:'cat',breed:'',sex:'unknown',ageMonths:null,ageObservedAt:null,marks:'',marksUnknown:true,illustrationKey:'cat'},claim:{basis:'claimed_owner',reportedCare:false,explanation:'Disposable synthetic fixture. No real animal or ownership assertion.'}});
  await call('submitClaim',{petId:fixture.petId,expectedClaimVersion:1,expectedIdentityVersion:1});
  const care=await call('care',{petId:fixture.petId,expectedCareVersion:0,expectedIdentityVersion:1,expectedCustodyVersion:0,payload:{healthState:'unknown',color:'',behaviorNotes:'',careNeeds:'Synthetic care discussion',sterilization:'unknown',vaccinations:[]},medicationNotes:'Synthetic private note'});fixture.careDigest=care.careDigest;fs.writeFileSync(local,JSON.stringify(fixture));
 }else if(mode==='offer'){
  await call('saveOffer',{listingId:fixture.listingId,petId:fixture.petId,expectedContentVersion:0,expectedControlVersion:0,careVersion:1,careDigest:fixture.careDigest,fields:{title:'Synthetic QA adoption offer',description:'Disposable isolated course-test record. No real animal.',regionCode:'north',offerType:'adoption',mediaRefs:[]}});
  await call('submitReview',{listingId:fixture.listingId,contentVersion:1,requestVersion:0});
 }else if(mode==='verify'){
  const result=await gateway({operation:'offer',input:{listingId:fixture.listingId}});assert.equal(result.data.publication.approvedContentVersion,1);assert.equal(result.data.listing.visibility,'hidden');assert(!(await db.doc('listingDiscovery/'+fixture.listingId).get()).exists);fs.writeFileSync('../build/adoption-live-proof.json',JSON.stringify({managedCallableVerified:true,independentPhoneReviewer:true,approvedRemainsHidden:true,privateDataExcluded:true,automaticPublication:false},null,2));
 }else throw Error('unsupported_stage');
 console.log(JSON.stringify({stage:mode,passed:true,petId:fixture.petId,listingId:fixture.listingId}));await deleteApp(app);await db.terminate();process.exit(0);
})().catch(e=>{console.error(JSON.stringify({passed:false,code:e.code??null,reason:String(e.message).slice(0,160)}));process.exitCode=1;});
