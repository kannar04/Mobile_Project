import {onCall,HttpsError,onRequest} from 'firebase-functions/v2/https';
import {initializeApp} from 'firebase-admin/app';
import {getAuth} from 'firebase-admin/auth';
import {getFirestore} from 'firebase-admin/firestore';
import {getStorage} from 'firebase-admin/storage';
import {MediaService} from './service.mjs';
import {CANDIDATE,ensure,uuid} from './contract.mjs';
initializeApp();
const service=new MediaService({db:getFirestore(),auth:getAuth(),bucket:getStorage().bucket('aniloveproject.firebasestorage.app')});
export const mediaApi=onRequest({region:'asia-east2',serviceAccount:'anilove-media-runtime@aniloveproject.iam.gserviceaccount.com',memory:'512MiB',timeoutSeconds:60,minInstances:0,maxInstances:2,concurrency:1,cors:false,invoker:'public'},async(req,res)=>{
 res.set('Cache-Control','private, no-store');res.set('X-Content-Type-Options','nosniff');
 try{
  ensure(req.method==='POST','method_denied');const operation=req.path.replace(/^\//,'');let actor=null;
  const token=req.get('Authorization');if(token){ensure(token.startsWith('Bearer '),'unauthenticated');actor=await getAuth().verifyIdToken(token.slice(7),true);}
  ensure(operation==='read'||actor?.uid,'unauthenticated');
  if(operation==='receive'){const id=req.get('X-Asset-Id');ensure(uuid(id)&&req.rawBody.length<=CANDIDATE,'invalid_image');res.json(await service.receive(actor,id,req.rawBody));return;}
  ensure(req.rawBody.length<=32768&&req.is('application/json'),'invalid');const input=req.body;
  if(operation==='read'){const bytes=await service.bytes(actor,input);res.type('image/jpeg').send(bytes);return;}
  const methods={directStart:'directStart',directSend:'directSend',directThread:'directThread',directInbox:'directInbox',directPeople:'directPeople',directBlock:'directBlock',publicProfilePage:'publicProfilePage',personal:'personal',personalRead:'personalRead',reserve:'reserve',post:'post',avatar:'avatar',google:'google',remove:'remove',saveProfile:'saveProfile',publishProfile:'publishProfile'};ensure(methods[operation],'method_denied');res.json(await service[methods[operation]](actor,input));
 }catch(e){const code=String(e.message??'unavailable');const safe=['invalid','invalid_text','invalid_image','invalid_asset','invalid_listing','listing_unavailable','listing_changed','output_size','budget_corrupt','budget_unavailable','capacity_full','daily_quota','outstanding_quota','media_disabled','not_verified','not_ready','restricted','denied','conflict','expired','busy','cancelled','unavailable','rollout_closed','held','consent_required','provider_photo_unavailable','not_new_registration','retained','cleanup_unknown','cleanup_conflict','unauthenticated','method_denied'].includes(code)?code:'unavailable';res.status(safe==='unauthenticated'?401:['denied','restricted','not_verified'].includes(safe)?403:safe==='conflict'?409:400).json({error:safe});}
});

// Authenticated adoption gateway. Client never supplies actor/role or receives Admin credentials.
import {AdoptionRuntime} from './adoption-runtime.mjs';
const adoption=new AdoptionRuntime();
const adoptionReads=new Set(['discovery','discoveryWindow','discoveryDetail','workspace','petWorkspace','receipt','pet','offer','reviewQueue','candidate','claimCandidate','publicDetail','offerSource','applications','application','reservation']);
export const adoptionOperations=onCall({region:'asia-east2',serviceAccount:'anilove-adoption-runtime@aniloveproject.iam.gserviceaccount.com',memory:'512MiB',timeoutSeconds:30,minInstances:0,maxInstances:2,concurrency:4},async req=>{
 try{if(!req.auth?.uid)throw new HttpsError('unauthenticated','Sign in required');if(!req.data||Object.keys(req.data).sort().join('|')!=='input|operation'||typeof req.data.operation!=='string'||Buffer.byteLength(JSON.stringify(req.data))>32768)throw new HttpsError('invalid-argument','invalid_intent');
  const user=await getAuth().getUser(req.auth.uid);if(user.disabled)throw new HttpsError('permission-denied','account_unavailable');const actor={uid:user.uid,emailVerified:user.emailVerified,disabled:user.disabled};const {operation,input}=req.data;const result=adoptionReads.has(operation)?await adoption.read(actor,operation,input):await adoption.execute(actor,operation,input);return JSON.parse(JSON.stringify(result));
 }catch(e){if(e instanceof HttpsError)throw e;const code=String(e.message??'unavailable');const allowed=/^[a-z_]{3,60}$/.test(code)?code:'unavailable';throw new HttpsError(['conflict','intent_conflict','source_changed','care_changed','identity_changed','self_or_stale_review'].includes(allowed)?'failed-precondition':'permission-denied',allowed);}
});
