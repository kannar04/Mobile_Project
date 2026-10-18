import {onRequest} from 'firebase-functions/v2/https';
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
  const methods={reserve:'reserve',post:'post',avatar:'avatar',google:'google',remove:'remove',saveProfile:'saveProfile',publishProfile:'publishProfile'};ensure(methods[operation],'method_denied');res.json(await service[methods[operation]](actor,input));
 }catch(e){const code=String(e.message??'unavailable');const safe=['invalid','invalid_text','invalid_image','invalid_asset','invalid_listing','listing_unavailable','listing_changed','output_size','budget_corrupt','budget_unavailable','capacity_full','daily_quota','outstanding_quota','media_disabled','not_verified','not_ready','restricted','denied','conflict','expired','busy','cancelled','unavailable','rollout_closed','held','consent_required','provider_photo_unavailable','not_new_registration','retained','cleanup_unknown','cleanup_conflict','unauthenticated','method_denied'].includes(code)?code:'unavailable';res.status(safe==='unauthenticated'?401:['denied','restricted','not_verified'].includes(safe)?403:safe==='conflict'?409:400).json({error:safe});}
});
