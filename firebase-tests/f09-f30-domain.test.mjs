import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import sharp from 'sharp';
import {MediaService,DAY,HOUR,MAX_BYTES,hash} from '../firebase-domain/media.mjs';
import {ImageSanitizer} from '../firebase-domain/image-sanitizer.mjs';
import {NotificationService,BindingService} from '../firebase-domain/notifications.mjs';

// Serial, rollback-capable transaction fixture, enforcing Firestore read-before-write ordering.
class Store {
 data=new Map(); tail=Promise.resolve();
 async transaction(work){let release;const next=new Promise(r=>release=r),prior=this.tail;this.tail=next;await prior;
  const writes=new Map();try{const result=await work({get:async path=>{assert.equal(writes.size,0,'read after write');return structuredClone(this.data.get(path));},set:(path,value)=>writes.set(path,structuredClone(value))});for(const [p,v]of writes)this.data.set(p,v);return result;}finally{release();}}
 put(path,value){this.data.set(path,structuredClone(value));}get(path){return this.data.get(path);}
}
const now=Date.UTC(2026,9,14,12),actor={uid:'alice',emailVerified:true};
function setup(){const s=new Store();for(const uid of ['alice','bob','charlie'])s.put(`users/${uid}`,{uid,schemaVersion:1,createdAt:new Date(now)});
 s.put('communityMemberships/alice',{uid:'alice',schemaVersion:1,status:'active',membershipVersion:1,createdAt:new Date(now),updatedAt:new Date(now)});s.put('mediaPolicy/current',{schemaVersion:1,policyVersion:1,enabled:true});
 s.put('notificationPolicy/current',{schemaVersion:1,policyVersion:1,enabled:true,pushEnabled:true,allowedAdapterVersions:['comment_v1','own_intake_v1'],activationWatermark:new Date(now-DAY)});
 return s;}
const mediaInput=()=>({assetId:randomUUID(),operationId:randomUUID(),purpose:'community_private',inputHash:'a'.repeat(64)});
function variants(a){return Object.fromEntries(['display','thumbnail'].map(key=>[key,{path:`final/alice/${a}/${key}.jpg`,generation:'2',width:100,height:100,bytes:100}]));}
const processor=a=>({sanitize:async()=>variants(a),deleteOriginal:async()=>{}});

test('ticket retry is one grant; changed intent conflicts and foreign/unverified issuance fails',async()=>{
 const s=setup(),m=new MediaService(s,{clock:()=>now}),i=mediaInput();await m.issue(actor,i);await m.issue(actor,i);assert.equal(s.get('mediaQuota/alice').grants.length,1);
 await assert.rejects(m.issue(actor,{...i,inputHash:'b'.repeat(64)}),/intent_conflict/);await assert.rejects(m.issue({...actor,emailVerified:false},mediaInput()),/not_verified/);
 s.put('securitySubjects/alice',{status:'restricted'});await assert.rejects(m.issue(actor,mediaInput()),/not_ready/);
});
test('outstanding4 and rolling20 limits are transactional across concurrent tickets',async()=>{
 const s=setup(),m=new MediaService(s,{clock:()=>now});const outcomes=await Promise.allSettled(Array.from({length:5},()=>m.issue(actor,mediaInput())));assert.equal(outcomes.filter(r=>r.status==='fulfilled').length,4);
 s.put('mediaQuota/alice',{grants:Array(20).fill(new Date(now-100)),outstanding:[]});await assert.rejects(m.issue(actor,mediaInput()),/daily_quota/);
 s.put('mediaQuota/alice',{grants:Array(20).fill(new Date(now-DAY-1)),outstanding:[]});await m.issue(actor,mediaInput());
});
test('policy off, malformed shell and unsupported consumer fail without reservation',async()=>{
 const s=setup(),m=new MediaService(s,{clock:()=>now});s.put('mediaPolicy/current',{enabled:false});await assert.rejects(m.issue(actor,mediaInput()),/media_disabled/);
 s.put('mediaPolicy/current',{enabled:true,schemaVersion:1,policyVersion:1});s.put('users/alice',{uid:'alice',schemaVersion:1,createdAt:new Date(now),roles:['admin']});await assert.rejects(m.issue(actor,mediaInput()),/not_ready/);
 await assert.rejects(m.issue(actor,{...mediaInput(),purpose:'private_evidence'}),/invalid_intent/);assert.equal(s.get('mediaQuota/alice'),undefined);
});
test('expired ticket and mismatched generation never become ready',async()=>{
 const s=setup(),i=mediaInput();let time=now;const m=new MediaService(s,{clock:()=>time});await m.issue(actor,i);time+=HOUR;
 await assert.rejects(m.finalize(i.assetId,'1',processor(i.assetId)),/finalization_closed/);assert.equal(s.get(`mediaAssets/${i.assetId}`).state,'issued');
});
test('repeated finalization reconciles ready and retries quarantine deletion',async()=>{
 const s=setup(),i=mediaInput(),m=new MediaService(s,{clock:()=>now});await m.issue(actor,i);let deletes=0;const p={...processor(i.assetId),deleteOriginal:async()=>{deletes++;if(deletes===1)throw Error('object delete outage');}};
 await assert.rejects(m.finalize(i.assetId,'1',p),/outage/);await m.finalize(i.assetId,'1',p);assert.equal(deletes,2);assert.equal(s.get(`mediaAssets/${i.assetId}`).state,'ready');
 await assert.rejects(m.finalize(i.assetId,'9',p),/generation_conflict/);
});
test('cancellation races processor; late sanitized bytes cannot expose ready asset',async()=>{
 const s=setup(),i=mediaInput(),m=new MediaService(s,{clock:()=>now});await m.issue(actor,i);
 const p={...processor(i.assetId),sanitize:async()=>{await m.remove(actor,{assetId:i.assetId,operationId:randomUUID(),expectedVersion:2});return variants(i.assetId);}};
 await assert.rejects(m.finalize(i.assetId,'1',p),/finalization_revoked/);assert.equal(s.get(`mediaAssets/${i.assetId}`).state,'cleanup_pending');
});
test('rejection and policy/restriction recheck keep outputs private',async()=>{
 const s=setup(),i=mediaInput(),m=new MediaService(s,{clock:()=>now});await m.issue(actor,i);
 await assert.rejects(m.finalize(i.assetId,'1',{sanitize:async()=>{throw Error('bad bytes');}}),/bad bytes/);assert.equal(s.get(`mediaAssets/${i.assetId}`).state,'rejected');
 const j=mediaInput();await m.issue(actor,j);await assert.rejects(m.finalize(j.assetId,'1',{...processor(j.assetId),sanitize:async()=>{s.put('mediaPolicy/current',{enabled:false});return variants(j.assetId);}}),/finalization_revoked/);
});
test('removal remains available while restricted; receipts/version checks and retention protect cleanup',async()=>{
 const s=setup(),i=mediaInput(),m=new MediaService(s,{clock:()=>now});await m.issue(actor,i);await m.finalize(i.assetId,'1',processor(i.assetId));s.put('securitySubjects/alice',{status:'restricted'});
 await assert.rejects(m.remove({uid:'bob'},{assetId:i.assetId,operationId:randomUUID(),expectedVersion:3}),/not_owner/);
 const input={assetId:i.assetId,operationId:randomUUID(),expectedVersion:3};await m.remove(actor,input);await m.remove(actor,input);
 s.put(`mediaRetention/${i.assetId}`,{held:true});let deleted=false;await assert.rejects(m.cleanup(i.assetId,{deleteGeneration:async()=>deleted=true}),/retained/);assert.equal(deleted,false);
 // A separately trusted fixture removes its test hold, not a client capability.
 s.put(`mediaRetention/${i.assetId}`,{held:false});const seen=[];await m.cleanup(i.assetId,{deleteGeneration:async(p,g)=>seen.push([p,g])});assert.equal(seen.length,3);assert.equal(s.get(`mediaAssets/${i.assetId}`).state,'deleted');
});
test('real decoder rotates/strips metadata and only outputs bounded fresh JPEG',async()=>{
 const bytes=await sharp({create:{width:300,height:100,channels:3,background:'red'}}).withMetadata({orientation:6}).jpeg().toBuffer();const objects=new Map();
 const san=new ImageSanitizer(sharp,{readGeneration:async()=>({bytes,contentType:'image/jpeg'}),createImmutable:async(p,b)=>{objects.set(p,b);return '17';}});
 const out=await san.sanitize({stagingPath:'staging/alice/a/candidate',generation:'1',inputHash:hash(bytes),assetId:'a',ownerUid:'alice'});
 const metadata=await sharp(objects.get(out.display.path)).metadata();assert.equal(metadata.width,100);assert.equal(metadata.height,300);assert.equal(metadata.format,'jpeg');assert.equal(metadata.exif,undefined);assert.equal(metadata.icc,undefined);assert(out.thumbnail.bytes<MAX_BYTES);
});
test('decoder rejects forged MIME/hash, oversized/decode bombs and invalid magic',async()=>{
 const bytes=await sharp({create:{width:10,height:10,channels:3,background:'blue'}}).png().toBuffer();let object={bytes,contentType:'image/jpeg'};
 const san=new ImageSanitizer(sharp,{readGeneration:async()=>object,createImmutable:async()=>{throw Error('must not create');}});const input={inputHash:hash(bytes)};
 await assert.rejects(san.sanitize(input),/mime_mismatch/);object={bytes,contentType:'image/png'};await assert.rejects(san.sanitize({inputHash:'b'.repeat(64)}),/invalid_bytes/);
 object={bytes:Buffer.alloc(MAX_BYTES+1),contentType:'image/png'};await assert.rejects(san.sanitize({inputHash:hash(object.bytes)}),/invalid_bytes/);
 object={bytes:Buffer.from('fake jpeg bytes'),contentType:'image/jpeg'};await assert.rejects(san.sanitize({inputHash:hash(object.bytes)}),/mime_mismatch/);
 const bomb=await sharp({create:{width:5000,height:5000,channels:3,background:'white'}}).png().toBuffer();object={bytes:bomb,contentType:'image/png'};await assert.rejects(san.sanitize({inputHash:hash(bomb)}),/pixel limit/i);
});

function source(s,{uid='bob',postId=randomUUID(),time=now,version=1}={}){
 const commentId=randomUUID(),operationId=randomUUID();
 s.put(`communityPosts/${postId}`,{schemaVersion:2,authorUid:'alice',contentVersion:version,lifecycle:'active',visibility:'visible',moderatorHold:'clear'});s.put(`communityFeed/${postId}`,{schemaVersion:2,authorUid:'alice',contentVersion:version,state:'visible',audience:'community'});
 s.put(`communityPosts/${postId}/comments/${commentId}`,{schemaVersion:2,postId,authorUid:uid,state:'visible'});s.put(`communityPosts/${postId}/comments/${commentId}/revisions/v1`,{schemaVersion:2,postId,sourceContentVersion:version,authorUid:uid,commentId,commentVersion:1,state:'visible',operationId});
 s.put(`users/${uid}/communityOperations/${operationId}`,{schemaVersion:2,ownerUid:uid,operationId,postId,targetId:commentId,command:'add_comment',status:'committed',base:{contentVersion:version},result:{targetVersion:1},committedAt:new Date(time)});return {writerUid:uid,postId,commentId,operationId};
}
test('independent comment witness derives once; grouping preserves chronology and owner state',async()=>{
 const s=setup(),n=new NotificationService(s,{clock:()=>now}),event=source(s);const a=await n.deriveComment(event),b=await n.deriveComment(event);assert.deepEqual(a,b);
 const path=`users/alice/notificationInbox/${a.itemId}`,original=s.get(path);s.put(path,{...original,readAt:new Date(now),state:'dismissed',dismissedAt:new Date(now)});
 const next=source(s,{postId:event.postId});await n.deriveComment(next);const row=s.get(path);assert.equal(row.createdAt.getTime(),now);assert.equal(row.state,'dismissed');assert(row.readAt);assert.equal(s.get(`notificationQuota/alice/days/2026-10-14`).inboxNewCount,1);assert.equal(Object.keys(row).length,14);
});
test('self, forged, withdrawn, held, stale and pre-watermark sources cannot derive',async()=>{
 for(const mode of ['self','forged','withdrawn','held','old','before']){
  const s=setup(),n=new NotificationService(s,{clock:()=>now}),event=source(s,{uid:mode==='self'?'alice':'bob',time:mode==='old'?now-8*DAY:mode==='before'?now-2*DAY:now});
  if(mode==='forged')s.put(`users/bob/communityOperations/${event.operationId}`,{command:'add_comment',ownerUid:'bob'});
  if(mode==='withdrawn')s.get(`communityPosts/${event.postId}/comments/${event.commentId}`).state='withdrawn';
  if(mode==='held')s.get(`communityPosts/${event.postId}`).moderatorHold='held';
  await assert.rejects(n.deriveComment(event),/ineligible_source|source_age/);
 }
});
test('daily cap summarizes overflow and no own-intake push/reviewer fiction exists',async()=>{
 const s=setup(),n=new NotificationService(s,{clock:()=>now});s.put('notificationQuota/alice/days/2026-10-14',{inboxNewCount:50,pushAttemptCount:0});const event=source(s),derived=await n.deriveComment(event);const row=s.get(`users/alice/notificationInbox/${derived.itemId}`);assert.equal(row.kind,'summary');assert.equal(row.routeType,'inbox');
 const reportId=randomUUID(),operationId=randomUUID();s.put(`users/alice/reportOperations/${operationId}`,{schemaVersion:1,ownerUid:'alice',reportId,operationId,command:'submit',resultVersion:1,committedAt:new Date(now)});s.put(`users/alice/reportStatus/${reportId}`,{schemaVersion:1,reportId,status:'received'});
 const intake=await n.deriveOwnIntake({uid:'alice',reportId,operationId});assert.equal(intake.sourceType,'own_intake');assert.equal([...s.data.keys()].filter(p=>p.startsWith('pushJobs/')).length,0);
});
async function bind(s,uid='alice',token='token-one',installationId=randomUUID(),replaceConfirmed=false){
 const b=new BindingService(s,{clock:()=>now}),secret='f'.repeat(64);let payload;const request={installationId,operationId:randomUUID(),token,secret,replaceConfirmed};const response=await b.register({uid,emailVerified:true},request,{sendData:async(t,p)=>payload=p});
 const proof={challengeId:response.challengeId,nonce:payload.nonce,secret};const confirmed=await b.confirm({uid,emailVerified:true},proof);return {b,secret,request,response,proof,...confirmed};
}
test('possession challenge stays off registration response; retries and confirmation are idempotent',async()=>{
 const s=setup(),binding=await bind(s);assert.equal(binding.response.nonce,undefined);const retry=await binding.b.register(actor,binding.request,{sendData:async()=>assert.fail('duplicate challenge')});assert.equal(retry.challengeId,binding.response.challengeId);
 const again=await binding.b.confirm(actor,binding.proof);assert.equal(again.bindingEpoch,binding.bindingEpoch);await assert.rejects(binding.b.register(actor,{...binding.request,token:'other'},{sendData:async()=>{}}),/intent_conflict/);
});
test('foreign/mismatched/expired challenge fails; wrong proof increments bounded attempts',async()=>{
 const s=setup(),b=new BindingService(s,{clock:()=>now});let payload;const secret='a'.repeat(64),response=await b.register(actor,{installationId:randomUUID(),operationId:randomUUID(),token:'foreign-token',secret,replaceConfirmed:false},{sendData:async(t,p)=>payload=p});
 assert.equal(s.get(`pushBindingChallenges/${response.challengeId}`).state,'pending');
 for(let i=0;i<5;i++)await assert.rejects(b.confirm(actor,{challengeId:response.challengeId,nonce:'b'.repeat(64),secret}),/invalid_proof/);
 assert.equal(s.get(`pushBindingChallenges/${response.challengeId}`).failedAttemptCount,5);await assert.rejects(b.confirm(actor,{challengeId:response.challengeId,nonce:payload.nonce,secret}),/challenge_closed/);
});
test('token reassociation revokes former owner; fourth active binding requires confirmation',async()=>{
 const s=setup(),a=await bind(s),b=await bind(s,'bob','token-one');assert.equal(s.get(`pushInstallations/${a.installationId}`).state,'revoked');assert.equal(s.get(`pushTokenOwners/${hash('token-one')}`).uid,'bob');
 await assert.rejects(a.b.revoke(actor,{installationId:b.installationId,bindingEpoch:b.bindingEpoch,secret:b.secret}),/stale_binding/);
 await bind(s,'bob','t2');await bind(s,'bob','t3');await assert.rejects(bind(s,'bob','t4'),/replacement_confirmation/);await bind(s,'bob','t4',randomUUID(),true);assert.equal(s.get('pushUserBindings/bob').ids.length,3);
});
test('data-only sender distinguishes provider acceptance; source/preference/policy suppression leaves inbox',async()=>{
 const s=setup(),binding=await bind(s),n=new NotificationService(s,{clock:()=>now});s.put('users/alice/notificationPreferences/main',{pushEnabled:true,commentsPush:true});const sourceResult=await n.deriveComment(source(s)),jobId=[...s.data.keys()].find(p=>p.startsWith('pushJobs/')).split('/')[1];let data;
 const result=await n.send(jobId,binding.installationId,{sendData:async(t,p)=>{data=p;return {code:'provider_accepted'};}},async()=>true);assert.equal(result.outcome,'provider_accepted');assert.deepEqual(Object.keys(data).sort(),['bindingEpoch','expiresAt','installationId','itemId','schemaVersion','uid']);assert(s.get(`users/alice/notificationInbox/${sourceResult.itemId}`));
 assert.deepEqual(await n.send(jobId,binding.installationId,{sendData:async()=>assert.fail()},async()=>true),{outcome:'not_sent'});
});
test('stale provider error cannot revoke refreshed binding; retry max/TTL and per-day cap enforced',async()=>{
 const s=setup(),binding=await bind(s),n=new NotificationService(s,{clock:()=>now});s.put('users/alice/notificationPreferences/main',{pushEnabled:true,commentsPush:true});await n.deriveComment(source(s));const path=[...s.data.keys()].find(p=>p.startsWith('pushJobs/')),jobId=path.split('/')[1];
 await n.send(jobId,binding.installationId,{sendData:async()=>{const old=s.get(`pushInstallations/${binding.installationId}`);s.put(`pushInstallations/${binding.installationId}`,{...old,bindingEpoch:old.bindingEpoch+1,tokenHash:hash('refreshed')});return {code:'unregistered'};}},async()=>true);assert.equal(s.get(`pushInstallations/${binding.installationId}`).state,'active');assert.equal(s.get(path).status,'permanent_failure');
 s.put(path,{...s.get(path),status:'queued',attemptCount:5});await n.send(jobId,binding.installationId,{sendData:async()=>assert.fail()},async()=>true);assert.equal(s.get(path).status,'expired');
});
test('sender independently rechecks withdrawn source, preference, kill switch and20-attempt budget',async()=>{
 for(const mode of ['withdrawn','preference','policy','quota']){
  const s=setup(),binding=await bind(s),n=new NotificationService(s,{clock:()=>now});s.put('users/alice/notificationPreferences/main',{pushEnabled:true,commentsPush:true});const event=source(s);await n.deriveComment(event);const path=[...s.data.keys()].find(p=>p.startsWith('pushJobs/'));
  if(mode==='withdrawn')s.get(`communityPosts/${event.postId}/comments/${event.commentId}`).state='withdrawn';
  if(mode==='preference')s.put('users/alice/notificationPreferences/main',{pushEnabled:false,commentsPush:false});
  if(mode==='policy')s.get('notificationPolicy/current').pushEnabled=false;
  if(mode==='quota')s.get('notificationQuota/alice/days/2026-10-14').pushAttemptCount=20;
  await n.send(path.split('/')[1],binding.installationId,{sendData:async()=>assert.fail('suppressed push')});assert.equal(s.get(path).status,'suppressed');
 }
});
test('binding renew respects exact UID/epoch/secret and requires fresh possession after expiry',async()=>{
 const s=setup(),binding=await bind(s),proof={installationId:binding.installationId,bindingEpoch:binding.bindingEpoch,secret:binding.secret};await binding.b.renew(actor,proof);
 await assert.rejects(binding.b.renew({uid:'bob',emailVerified:true},proof),/stale_binding/);await assert.rejects(binding.b.renew(actor,{...proof,bindingEpoch:0}),/stale_binding/);
 const late=new BindingService(s,{clock:()=>now+31*DAY});await assert.rejects(late.renew(actor,proof),/stale_binding/);
 await binding.b.revoke(actor,proof);await binding.b.revoke(actor,proof);assert.equal(s.get(`pushInstallations/${binding.installationId}`).state,'revoked');
});
test('expired/foreign challenge never activates and a failed-send retry cannot bypass possession',async()=>{
 const s=setup(),b=new BindingService(s,{clock:()=>now});let hint;const response=await b.register(actor,{installationId:randomUUID(),operationId:randomUUID(),token:'target-token',secret:'c'.repeat(64),replaceConfirmed:false},{sendData:async(t,p)=>hint=p});
 const proof={challengeId:response.challengeId,nonce:hint.nonce,secret:'c'.repeat(64)};await assert.rejects(b.confirm({uid:'bob',emailVerified:true},proof),/challenge_closed/);
 const late=new BindingService(s,{clock:()=>now+600001});await assert.rejects(late.confirm(actor,proof),/challenge_closed/);assert.equal([...s.data.keys()].filter(p=>p.startsWith('pushInstallations/')).length,0);
});
test('retryable send uses bounded backoff/lease and expires without treating invalid argument as token revocation',async()=>{
 const s=setup(),binding=await bind(s);let time=now;const n=new NotificationService(s,{clock:()=>time});s.put('users/alice/notificationPreferences/main',{pushEnabled:true,commentsPush:true});await n.deriveComment(source(s));const path=[...s.data.keys()].find(p=>p.startsWith('pushJobs/')),job=path.split('/')[1];
 await n.send(job,binding.installationId,{sendData:async()=>({code:'invalid_argument'})});assert.equal(s.get(path).status,'retryable_failure');assert.equal(s.get(`pushInstallations/${binding.installationId}`).state,'active');await assert.rejects(n.send(job,binding.installationId,{sendData:async()=>assert.fail()}),/lease_busy/);
 time=now+DAY+1;await n.send(job,binding.installationId,{sendData:async()=>assert.fail()});assert.equal(s.get(path).status,'expired');
});
