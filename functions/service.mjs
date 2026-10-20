import {recordParticipationConsent} from './domain/participation-consent.mjs';
import {TERMS} from './domain/course-terms.mjs';
import {participationPolicy,ordinaryMember} from './domain/participation.mjs';
import {Work,capability} from './domain/safety-common.mjs';
import {DirectChatService} from './direct-chat.mjs';
import sharp from 'sharp';
import {AdoptionSourceService} from './domain/adoption-source2.mjs';
import {adoptionSource} from './adoption.mjs';
import {randomUUID} from 'node:crypto';
import {LIMIT,RESERVATION,CANDIDATE,DISPLAY,THUMBNAIL,PURPOSES,uuid,ensure,exact,digest,selection,reserveCount,googleImageUrl,bodyText,choice} from './contract.mjs';
const ms=x=>x?.toMillis?.()??(x instanceof Date?+x:NaN);
const number=(x,k)=>x?.[k]??0;
const stamp=x=>Number.isFinite(ms(x));
const same=(a,b)=>digest(a)===digest(b);
const pathUid=uid=>{ensure(typeof uid==='string'&&uid.length>0&&uid.length<=128&&!uid.includes('/'),'unauthenticated');return uid;};
const descriptor=v=>({width:v.width,height:v.height,bytes:v.bytes});
export class MediaService {
 constructor({db,bucket,auth,clock=Date.now,fetcher=fetch}){this.db=db;this.bucket=bucket;this.auth=auth;this.clock=clock;this.fetcher=fetcher;}
 ref(path){return this.db.doc(path);}
 async read(tx,path){return (await tx.get(this.ref(path))).data()??null;}
 async ready(tx,actor,{verified=false,publication=false}={}){
  const uid=pathUid(actor?.uid);ensure(!verified||actor.email_verified===true,'not_verified');
  const [shell,member,subject,restriction]=await Promise.all(['users/','communityMemberships/','securitySubjects/','accountRestrictionState/'].map(p=>this.read(tx,p+uid)));
  exact(shell,['uid','schemaVersion','createdAt']);ensure(shell.uid===uid&&shell.schemaVersion===1&&stamp(shell.createdAt),'not_ready');
  exact(member,['uid','schemaVersion','status','membershipVersion','createdAt','updatedAt']);ensure(member.uid===uid&&member.schemaVersion===1&&member.status==='active'&&Number.isInteger(member.membershipVersion)&&member.membershipVersion>=1&&stamp(member.createdAt)&&stamp(member.updatedAt),'restricted');
  if(subject){exact(subject,['status','roles','updatedAt']);ensure(subject.status==='active'&&Array.isArray(subject.roles)&&stamp(subject.updatedAt),'restricted');}
  if(restriction){ensure(restriction.schemaVersion===1&&Array.isArray(restriction.scopes),'restricted');ensure(!publication||!restriction.scopes.includes('community_publication'),'restricted');}
  return uid;
 }
 async policy(tx){const p=await this.read(tx,'mediaPolicy/current');ensure(p?.schemaVersion===1&&p.enabled===true&&p.policyVersion>=1,'media_disabled');return p;}
 async reserve(actor,input){
  exact(input,['assetId','operationId','purpose','inputHash']);ensure(uuid(input.assetId)&&uuid(input.operationId)&&PURPOSES.includes(input.purpose)&&/^[a-f0-9]{64}$/.test(input.inputHash));
  const uid=pathUid(actor?.uid),hash=digest(input),now=this.clock();
  return this.db.runTransaction(async tx=>{
   const receipt=await this.read(tx,`mediaCommands/${uid}/receipts/${input.operationId}`);
   if(receipt){ensure(receipt.inputHash===hash&&receipt.command==='reserve','conflict');return receipt.result;}
   await this.ready(tx,actor,{verified:['post_image','pet_photo'].includes(input.purpose),publication:input.purpose==='post_image'});await this.policy(tx);
   const [budget,asset,quota]=await Promise.all([this.read(tx,'mediaBudget/current'),this.read(tx,`mediaAssets/${input.assetId}`),this.read(tx,`mediaQuota/${uid}`)]);
   ensure(budget?.schemaVersion===1&&budget.limitBytes===LIMIT&&!asset,'budget_unavailable');
   const grants=(quota?.grants??[]).filter(t=>stamp(t)&&ms(t)>now-86400000);ensure(grants.length<200,'daily_quota');
   const existing=quota?.outstanding??[];ensure(existing.length<=100,'quota_corrupt');
   const outstanding=[];for(const id of existing){const a=await this.read(tx,`mediaAssets/${id}`);if(a&&!['deleted','attached','cancelled','rejected'].includes(a.state))outstanding.push(id);}
   ensure(outstanding.length<100,'outstanding_quota');const reserved=reserveCount(budget.reservedBytes);
   const result={assetId:input.assetId,state:'issued',expiresAt:now+3600000};
   tx.set(this.ref('mediaBudget/current'),{...budget,reservedBytes:reserved,version:budget.version+1,updatedAt:new Date(now)});
   tx.create(this.ref(`mediaAssets/${input.assetId}`),{schemaVersion:2,ownerUid:uid,assetId:input.assetId,purpose:input.purpose,inputHash:input.inputHash,state:'issued',version:1,createdAt:new Date(now),expiresAt:new Date(now+3600000),targetId:null,variants:{},leaseUntil:null,writerActive:false});
   tx.create(this.ref(`mediaReservations/${input.assetId}`),{schemaVersion:1,ownerUid:uid,assetId:input.assetId,purpose:input.purpose,reservedBytes:RESERVATION,state:'occupied',createdAt:new Date(now)});
   tx.set(this.ref(`mediaQuota/${uid}`),{grants:[...grants,new Date(now)],outstanding:[...outstanding,input.assetId]});
   tx.create(this.ref(`mediaCommands/${uid}/receipts/${input.operationId}`),{command:'reserve',inputHash:hash,result,createdAt:new Date(now)});return result;
  });
 }
 async storeObject(path,bytes,onUncertain=()=>{}){
  const file=this.bucket.file(path),hash=digest(bytes);
  try{await file.save(bytes,{resumable:false,validation:'crc32c',preconditionOpts:{ifGenerationMatch:0},metadata:{contentType:'image/jpeg',cacheControl:'private, no-store',metadata:{sha256:hash}}});}
  catch(e){if(e.code!==412){onUncertain();throw e;}const [m]=await file.getMetadata();ensure(m.metadata?.sha256===hash&&Number(m.size)===bytes.length,'generation_conflict');}
  const [m]=await file.getMetadata();ensure(!m.metadata?.firebaseStorageDownloadTokens,'unsafe_object');return {path,generation:String(m.generation),bytes:bytes.length};
 }
 async receive(actor,id,bytes){
  const uid=pathUid(actor?.uid);ensure(uuid(id)&&Buffer.isBuffer(bytes)&&bytes.length>0&&bytes.length<=CANDIDATE,'invalid_image');const hash=digest(bytes),lease=randomUUID();
  const initial=await this.db.runTransaction(async tx=>{
   const a=await this.read(tx,`mediaAssets/${id}`),reservation=await this.read(tx,`mediaReservations/${id}`);ensure(a?.ownerUid===uid&&a.inputHash===hash&&reservation?.state==='occupied','denied');
   await this.ready(tx,actor,{verified:['post_image','pet_photo'].includes(a.purpose),publication:a.purpose==='post_image'});await this.policy(tx);
   if(['ready','attached'].includes(a.state))return a;
   ensure(ms(a.expiresAt)>this.clock()&&['issued','receiving'].includes(a.state),'expired');ensure(a.writerActive===false,'busy');
   // Never infer writer completion from timeout. A lost acknowledgement keeps capacity occupied.
   const next={...a,state:'receiving',lease,writerActive:true,leaseUntil:new Date(this.clock()+300000),version:a.version+1};tx.set(this.ref(`mediaAssets/${id}`),next);return next;
  });
  if(['ready','attached'].includes(initial.state))return {assetId:id,state:initial.state};
  let writeUncertain=false;
  try{
   const decoder=sharp(bytes,{limitInputPixels:20_000_000,animated:false,failOn:'error'}),meta=await decoder.metadata();
   ensure(['jpeg','png'].includes(meta.format)&&meta.width>0&&meta.height>0&&meta.width*meta.height<=20_000_000&&(!meta.pages||meta.pages===1),'invalid_image');
   const display=await decoder.rotate().resize({width:2048,height:2048,fit:'inside',withoutEnlargement:true}).flatten({background:'#FCF5E9'}).jpeg({quality:82}).toBuffer({resolveWithObject:true});
   const thumb=await sharp(display.data).resize({width:480,height:480,fit:'inside',withoutEnlargement:true}).jpeg({quality:75}).toBuffer({resolveWithObject:true});
   ensure(display.data.length<=DISPLAY&&thumb.data.length<=THUMBNAIL,'output_size');
   for(const [variant,data] of [['candidate',bytes],['display',display.data],['thumbnail',thumb.data]]){
    await this.db.runTransaction(async tx=>{const a=await this.read(tx,`mediaAssets/${id}`);await this.policy(tx);ensure(a?.lease===lease&&a.state==='receiving'&&ms(a.leaseUntil)>this.clock(),'cancelled');});
    await this.storeObject(variant==='candidate'?`staging/${uid}/${id}/candidate`:`final/${uid}/${id}/${variant}.jpg`,data,()=>{writeUncertain=true;});
   }
   const variants={};for(const [key,v] of [['display',display],['thumbnail',thumb]]){const [m]=await this.bucket.file(`final/${uid}/${id}/${key}.jpg`).getMetadata();variants[key]={path:`final/${uid}/${id}/${key}.jpg`,generation:String(m.generation),bytes:v.data.length,width:v.info.width,height:v.info.height};}
   await this.db.runTransaction(async tx=>{const a=await this.read(tx,`mediaAssets/${id}`);await this.ready(tx,actor,{verified:['post_image','pet_photo'].includes(a?.purpose),publication:a?.purpose==='post_image'});await this.policy(tx);ensure(a?.lease===lease&&a.state==='receiving','cancelled');tx.set(this.ref(`mediaAssets/${id}`),{...a,state:'ready',version:a.version+1,expiresAt:new Date(this.clock()+86400000),variants});});
   const candidate=this.bucket.file(`staging/${uid}/${id}/candidate`);const [m]=await candidate.getMetadata();await candidate.delete({ifGenerationMatch:m.generation});return {assetId:id,state:'ready'};
  }catch(e){await this.db.runTransaction(async tx=>{const a=await this.read(tx,`mediaAssets/${id}`);if(a?.lease===lease&&a.state==='receiving')tx.set(this.ref(`mediaAssets/${id}`),{...a,state:'rejected',version:a.version+1});});throw e;}
  finally{
   // All object operations above are awaited. No further object write exists after this acknowledgement.
   await this.db.runTransaction(async tx=>{const a=await this.read(tx,`mediaAssets/${id}`);if(a?.lease===lease)tx.set(this.ref(`mediaAssets/${id}`),{...a,writerActive:writeUncertain,leaseUntil:null,...(writeUncertain?{writerOutcome:'unknown'}:{})});});
  }
 }
 async sourceAllowed(tx,actor,a){
  if(a.ownerUid===actor?.uid)return true;
  if(!a.targetId)return false;
  if(a.targetId.startsWith('flow:')&&actor?.uid){
   const id=a.targetId.slice(5);if(!uuid(id))return false;const d=await this.read(tx,'adoptions/'+id);if(d?.schemaVersion!==2||!Array.isArray(d.participantUids))return false;
   const w=new Work({get:p=>this.read(tx,p)}),course=await participationPolicy(w);
   if(d.participantUids.includes(actor.uid)){
    if(course){try{await ordinaryMember(w,{uid:actor.uid},{recovery:true});return true;}catch(e){if(['account_restricted','account_unavailable'].includes(e.message))return false;throw e;}}
    const [m,s]=await Promise.all([w.get('communityMemberships/'+actor.uid),w.get('securitySubjects/'+actor.uid)]);return m?.status==='active'&&s?.status==='active';
   }
   try{await capability(w,{uid:actor.uid,emailVerified:actor.email_verified===true},'adoption_lifecycle');}catch(e){if(['protected_account_ineligible','capability_or_conflict','scoped_restriction','account_unavailable'].includes(e.message))return false;throw e;}
    return true;
  }
  if(a.targetId.startsWith('post:')){
   const id=a.targetId.slice(5),p=await this.read(tx,`communityPosts/${id}`),f=await this.read(tx,`communityFeed/${id}`);
   if(!p||p.lifecycle!=='active'||p.visibility!=='visible'||p.moderatorHold!=='clear'||!p.mediaAssetIds?.includes(a.assetId)||f?.contentVersion!==p.contentVersion)return false;
   const policy=await this.read(tx,'system/communityPolicy');if(policy?.enabled!==true)return false;
   if(actor?.uid)return true;const pub=await this.read(tx,'system/communityPublicPolicy');return p.audience==='everyone'&&pub?.enabled===true;
  }
  if(a.targetId.startsWith('pet:')&&actor?.uid){const id=a.targetId.slice(4),media=await this.read(tx,`petMedia/${id}`),slot=await this.read(tx,`petListingSlots/${id}`);if(!media?.assetIds?.includes(a.assetId))return false;const pet=await this.read(tx,`pets/${id}`),claim=pet?await this.read(tx,`pets/${id}/claims/${pet.editorUid}`):null;if(claim?.reviewState==='submitted'&&media.ownerUid===pet.editorUid){try{const {reviewer}=await import('./domain/adoption-source2.mjs');const {Work}=await import('./domain/safety-common.mjs');await reviewer(new Work({get:p=>this.read(tx,p)}),{uid:actor.uid,emailVerified:actor.email_verified===true},'pet_offer_review');return true;}catch{}}if(!slot?.activeListingId)return false;try{const {currentBrowseSource}=await import('./domain/adoption-release.mjs');const {Work}=await import('./domain/safety-common.mjs');const b=await currentBrowseSource(new Work({get:p=>this.read(tx,p)}),slot.activeListingId);return b.detail.petId===id;}catch{return false;}}
  if(a.targetId.startsWith('profile:')&&actor?.uid){const uid=a.targetId.slice(8),p=await this.read(tx,`publicProfiles/${uid}`),c=await this.read(tx,`publicProfileControls/${uid}`),h=await this.read(tx,`profileModeration/${uid}`);return c?.visibility==='public'&&h?.hold==='clear'&&p?.avatarAssetId===a.assetId;}
  return false;
 }
 async bytes(actor,input){
  exact(input,['assetId','variant']);ensure(uuid(input.assetId)&&['display','thumbnail'].includes(input.variant));
  const v=await this.db.runTransaction(async tx=>{const a=await this.read(tx,`mediaAssets/${input.assetId}`);ensure(a&&['ready','attached'].includes(a.state),'unavailable');ensure(a.state==='attached'||ms(a.expiresAt)>this.clock(),'expired');ensure(await this.sourceAllowed(tx,actor,a),'denied');const v=a.variants[input.variant];ensure(v?.path===`final/${a.ownerUid}/${a.assetId}/${input.variant}.jpg`,'unavailable');return v;});
  const file=this.bucket.file(v.path,{generation:v.generation});const [b]=await file.download({validation:'crc32c'});ensure(b.length<=DISPLAY,'invalid_image');return b;
 }
 async post(actor,c){
  exact(c,['command','postId','targetId','operationId','base','input']);ensure(uuid(c.postId)&&uuid(c.operationId)&&c.targetId===null&&['publish','create_private','edit_private','edit_visible','show','hide','archive'].includes(c.command));exact(c.base,['contentVersion','controlVersion','targetVersion']);ensure(c.base.targetVersion===0);choice(c.base.contentVersion);choice(c.base.controlVersion);
  const uid=pathUid(actor?.uid),now=new Date(this.clock()),rp=`users/${uid}/communityOperations/${c.operationId}`;
  return this.db.runTransaction(async tx=>{
   const receipt=await this.read(tx,rp);if(receipt){ensure(receipt.command===c.command&&receipt.postId===c.postId&&same(receipt.base,c.base)&&same(receipt.input,c.input),'conflict');return receipt;}
   const old=await this.read(tx,`communityPosts/${c.postId}`);ensure(!old||[2,4,5,6].includes(old.schemaVersion)&&old.authorUid===uid&&old.lifecycle==='active','denied');ensure(number(old,'contentVersion')===c.base.contentVersion&&number(old,'controlVersion')===c.base.controlVersion,'conflict');
   const safeExit=['hide','archive'].includes(c.command);if(!safeExit){await this.ready(tx,actor,{verified:true,publication:true});await this.policy(tx);}
   const visible=['publish','show','edit_visible'].includes(c.command),material=['create_private','edit_private','edit_visible'].includes(c.command)||c.command==='publish'&&!old;
   const policy=await this.read(tx,'system/communityPolicy'),report=await this.read(tx,'system/reportPolicy'),pub=await this.read(tx,'system/communityPublicPolicy');
   const prior=old?await this.read(tx,`communityPosts/${c.postId}/revisions/${old.currentRevisionId}`):null;
   const count=await this.read(tx,`communityCounts/${c.postId}`);
   let ids=old?.mediaAssetIds??[],body=prior?.body,art=prior?.bundledIllustrationKey??null;
   if(material){ensure(Object.keys(c.input).every(k=>['body','bundledIllustrationKey','mediaAssetIds','consent','audience','type','listingId','listingContentVersion','listingPublicationVersion','petId','listingRequirements','participationConsent'].includes(k)));body=bodyText(c.input.body);art=c.input.bundledIllustrationKey??null;ensure([null,'default','cat','dog'].includes(art));ensure(Buffer.byteLength(body)+(art?.length??0)+100<=8192,'invalid_text');ids=selection(c.input.mediaAssetIds??[]);}
   else exact(c.input,visible?['consent']:[]);
   if(c.command==='archive')ids=[];
   const audience=old?(old.audience??(old.schemaVersion===4?'everyone':'community')):(c.input.audience??'community');ensure(['everyone','community'].includes(audience));
   if(visible){ensure(policy?.enabled===true&&policy.publicationMode==='post_moderated'&&report?.intakeEnabled===true&&report.supportedTargetKinds?.includes('post'),'rollout_closed');ensure(audience!=='everyone'||pub?.enabled===true,'rollout_closed');ensure(!old||old.moderatorHold==='clear','held');ensure(c.command==='edit_visible'||c.input.consent===true,'consent_required');}
   const type=material?(c.input.type??old?.type??'community'):(old?.type??'community');ensure(['community','adoption'].includes(type));
   const adoptionWrites=new Map(),adoptionRead=async path=>adoptionWrites.has(path)?adoptionWrites.get(path):this.read(tx,path);
   let link=type==='adoption'?Object.fromEntries(['listingId','listingContentVersion','listingPublicationVersion'].map(k=>[k,material?c.input[k]:old?.[k]])):null;
   if(type==='community')ensure(!['listingId','listingContentVersion','listingPublicationVersion'].some(k=>c.input[k]!=null));
   if(type==='adoption'&&material&&!old&&await participationPolicy({get:adoptionRead})){
    const bridge={get:adoptionRead,set:(p,d)=>adoptionWrites.set(p,d)};if(c.input.participationConsent)await recordParticipationConsent(bridge,{uid,emailVerified:actor.email_verified===true},c.input.participationConsent,+now);const terms=await adoptionRead('adoptionTerms/'+uid);ensure(terms?.termsVersion===1&&terms.termsHash===digest(TERMS),'terms_required');
   }
   if(type==='adoption'&&material&&c.input.petId&&!old&&!c.input.listingId){ensure(c.command==='publish','consent_required');ensure(uuid(c.input.petId),'invalid_listing');const identityRoot=await adoptionRead(`pets/${c.input.petId}`),identity=identityRoot?await adoptionRead(`pets/${c.input.petId}/identityRevisions/${identityRoot.currentRevisionId}`):null;ensure(identityRoot?.editorUid===uid&&identity?.identity,'listing_unavailable');const bridge={get:adoptionRead,set:(p,d)=>adoptionWrites.set(p,d),delete:p=>adoptionWrites.set(p,null)};const simple=await participationPolicy(bridge);const result=await new AdoptionSourceService({transaction:fn=>fn(bridge)},{clock:this.clock}).execute({uid,emailVerified:actor.email_verified===true},simple?'publishCourse':'createFromPet',{...(simple?{expectedContentVersion:0,expectedControlVersion:0,requirements:c.input.listingRequirements,participationConsent:c.input.participationConsent??null}:{}),operationId:c.operationId,petId:c.input.petId,listingId:c.postId,consent:c.input.consent===true,fields:{title:(identity.identity.displayName+' tìm mái nhà mới').slice(0,80),description:[...body].slice(0,2000).join('').trim(),regionCode:null,offerType:'adoption',mediaRefs:[]}});link={listingId:result.listingId,listingContentVersion:result.listingContentVersion,listingPublicationVersion:result.listingPublicationVersion};}
   if(type==='adoption'){ensure(audience==='community','invalid_listing');if(!safeExit)link=await adoptionSource(adoptionRead,uid,link);}
   const linkedListing=type==='adoption'?await adoptionRead(`listings/${link.listingId}`):null;if(type==='adoption'&&c.input.petId)ensure(linkedListing?.petId===c.input.petId,'invalid_listing');const petPhotos=linkedListing?(await adoptionRead(`petMedia/${linkedListing.petId}`))?.assetIds??[]:[];
   const all=[...new Set([...(old?.mediaAssetIds??[]),...ids])],assets={};for(const id of all)assets[id]=await this.read(tx,`mediaAssets/${id}`);
   if(!safeExit)for(const id of ids){const a=assets[id];ensure(a?.ownerUid===uid&&['ready','attached'].includes(a.state)&&(a.purpose==='post_image'&&(!a.targetId||a.targetId===`post:${c.postId}`)||a.purpose==='pet_photo'&&a.targetId===`pet:${linkedListing?.petId}`&&petPhotos.includes(id)),'invalid_asset');ensure(a.state==='attached'||ms(a.expiresAt)>this.clock(),'expired');}
   const cv=c.base.contentVersion+(material?1:0),ctl=c.base.controlVersion+1;ensure(cv>=1,'invalid');
   const first=old?.firstPublishedAt??(visible?now:null),edited=material&&old?.firstPublishedAt?now:old?.editedAt??null;
   const next={schemaVersion:5,postId:c.postId,authorUid:uid,type:'community',audience,lifecycle:c.command==='archive'?'archived':'active',moderatorHold:old?.moderatorHold??'clear',contentVersion:cv,currentRevisionId:`v${cv}`,controlVersion:ctl,visibility:visible?'visible':'hidden',firstPublishedAt:first,editedAt:edited,createdAt:old?.createdAt??now,updatedAt:now,lastOperationId:c.operationId,mediaAssetIds:ids,manifestVersion:cv};
   const revision={schemaVersion:5,postId:c.postId,authorUid:uid,type:'community',contentVersion:cv,body,bundledIllustrationKey:art,mediaAssetIds:ids,manifestVersion:cv,createdAt:now,operationId:c.operationId};
   const feed={schemaVersion:5,controlVersion:ctl,postId:c.postId,authorUid:uid,type:'community',contentVersion:cv,body,bundledIllustrationKey:art,audience,state:'visible',firstPublishedAt:first,editedAt:edited,mediaAssetIds:ids,manifestVersion:cv};
   if(type==='adoption')for(const record of [next,revision,feed])Object.assign(record,{schemaVersion:6,type,...link});
   const result={schemaVersion:2,ownerUid:uid,operationId:c.operationId,postId:c.postId,targetId:null,command:c.command,base:c.base,result:{contentVersion:cv,controlVersion:ctl},input:c.input,status:'committed',committedAt:now};
   for(const[p,d]of adoptionWrites)d===null?tx.delete(this.ref(p)):tx.set(this.ref(p),d);
   tx.set(this.ref(`communityPosts/${c.postId}`),next);if(material)tx.create(this.ref(`communityPosts/${c.postId}/revisions/v${cv}`),revision);
   if(visible){tx.set(this.ref(`communityFeed/${c.postId}`),feed);if(!count)tx.create(this.ref(`communityCounts/${c.postId}`),{schemaVersion:2,postId:c.postId,likeCount:0,countVersion:0,updatedAt:now,lastOperationId:c.operationId});}else tx.delete(this.ref(`communityFeed/${c.postId}`));
   if(visible&&audience==='everyone'){const minimal={...feed};delete minimal.authorUid;delete minimal.type;delete minimal.controlVersion;tx.set(this.ref(`communityPublicFeed/${c.postId}`),minimal);}else tx.delete(this.ref(`communityPublicFeed/${c.postId}`));
   tx.set(this.ref(`mediaAccess/post:${c.postId}`),{schemaVersion:2,targetId:`post:${c.postId}`,sourceContentVersion:cv,controlVersion:ctl,assetIds:visible?ids:[],audience:visible?audience:'private'});
   if(c.command!=='hide')for(const id of all){const a=assets[id];if(a?.purpose==='pet_photo')continue;if(ids.includes(id))tx.set(this.ref(`mediaAssets/${id}`),{...a,targetId:`post:${c.postId}`,state:'attached',version:a.version+1});else if(a?.targetId===`post:${c.postId}`)tx.set(this.ref(`mediaAssets/${id}`),{...a,targetId:null,state:'ready',expiresAt:new Date(this.clock()+86400000),version:a.version+1});}
   tx.create(this.ref(rp),result);return result;
  });
 }
 async avatar(actor,input){
  exact(input,['operationId','expectedVersion','assetId']);ensure(uuid(input.operationId)&&(input.assetId===null||uuid(input.assetId)));choice(input.expectedVersion);
  const uid=pathUid(actor?.uid),rp=`mediaCommands/${uid}/receipts/${input.operationId}`,hash=digest(input);
  return this.db.runTransaction(async tx=>{
   const receipt=await this.read(tx,rp);if(receipt){ensure(receipt.command==='avatar'&&receipt.inputHash===hash,'conflict');return receipt.result;}
   await this.ready(tx,actor);const old=await this.read(tx,`users/${uid}/private/avatar`),draft=await this.read(tx,`users/${uid}/profileDraft/current`),published=await this.read(tx,`publicProfiles/${uid}`),control=await this.read(tx,`publicProfileControls/${uid}`),hold=await this.read(tx,`profileModeration/${uid}`);ensure(number(old,'choiceVersion')===input.expectedVersion,'conflict');
   const ids=[...new Set([old?.assetId,input.assetId].filter(Boolean))],assets={};for(const id of ids)assets[id]=await this.read(tx,`mediaAssets/${id}`);
   if(input.assetId){const a=assets[input.assetId];ensure(a?.ownerUid===uid&&['profile_avatar','google_avatar'].includes(a.purpose)&&['ready','attached'].includes(a.state)&&(!a.targetId||a.targetId===`profile:${uid}`),'invalid_asset');ensure(a.state==='attached'||ms(a.expiresAt)>this.clock(),'expired');}
   const result={schemaVersion:2,ownerUid:uid,assetId:input.assetId,choiceVersion:input.expectedVersion+1,googleHandled:true,updatedAt:new Date(this.clock())};
   tx.set(this.ref(`users/${uid}/private/avatar`),result);
   if(draft){const next={...draft,schemaVersion:2,avatarAssetId:input.assetId,avatarChoiceVersion:result.choiceVersion,version:draft.version+1,lastOperationId:input.operationId,updatedAt:new Date(this.clock())};await this.autoProfile(tx,uid,next,control,published,hold,input.operationId);tx.set(this.ref(`users/${uid}/profileDraft/current`),next);}
   for(const id of ids){const a=assets[id];if(id===input.assetId)tx.set(this.ref(`mediaAssets/${id}`),{...a,state:'attached',targetId:`profile:${uid}`,version:a.version+1});else if(control?.visibility!=='hidden'||published?.avatarAssetId!==id)tx.set(this.ref(`mediaAssets/${id}`),{...a,state:'ready',targetId:null,expiresAt:new Date(this.clock()+86400000),version:a.version+1});}
   tx.create(this.ref(rp),{command:'avatar',inputHash:hash,result,createdAt:new Date(this.clock())});return result;
  });
 }
 async personal(actor,input){
  const uid=pathUid(actor?.uid);exact(input,['operationId','expectedVersion','gender','birthdate']);
  ensure(uuid(input.operationId));choice(input.expectedVersion);ensure([null,'male','female'].includes(input.gender),'invalid');
  const date=input.birthdate;
  ensure(date===null||typeof date==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(date)&&date>='1900-01-01'&&Number.isFinite(Date.parse(date+'T00:00:00Z'))&&new Date(date+'T00:00:00Z').toISOString().slice(0,10)===date&&date<=new Date(this.clock()).toISOString().slice(0,10),'invalid');
  const path=`users/${uid}/private/personal`,rp=`mediaCommands/${uid}/receipts/${input.operationId}`,hash=digest(input);
  return this.db.runTransaction(async tx=>{const receipt=await this.read(tx,rp);if(receipt){ensure(receipt.command==='personal'&&receipt.inputHash===hash,'conflict');return receipt.result;}await this.ready(tx,actor);const old=await this.read(tx,path);ensure(number(old,'version')===input.expectedVersion,'conflict');const result={schemaVersion:1,ownerUid:uid,gender:input.gender,birthdate:date,version:input.expectedVersion+1,updatedAt:new Date(this.clock())};tx.set(this.ref(path),result);tx.create(this.ref(rp),{command:'personal',inputHash:hash,result,createdAt:new Date(this.clock())});return result;});
 }
 async directStart(actor,input){return new DirectChatService(this).start(actor,input);}
 async directSend(actor,input){return new DirectChatService(this).send(actor,input);}
 async directThread(actor,input){return new DirectChatService(this).thread(actor,input);}
 async directInbox(actor,input){return new DirectChatService(this).inbox(actor,input);}
 async directPeople(actor,input){return new DirectChatService(this).people(actor,input);}
 async directBlock(actor,input){return new DirectChatService(this).block(actor,input);}
 async publicProfilePage(actor,input){
  await this.db.runTransaction(tx=>this.ready(tx,actor));exact(input,['uid','after']);const uid=pathUid(input.uid);ensure(input.after===null||uuid(input.after));
  const [control,profile,hold]=await Promise.all([this.ref(`publicProfileControls/${uid}`).get(),this.ref(`publicProfiles/${uid}`).get(),this.ref(`profileModeration/${uid}`).get()]);
  ensure(control.data()?.visibility==='public'&&profile.exists&&hold.data()?.hold==='clear','denied');
  let q=this.db.collection('communityFeed').where('authorUid','==',uid).orderBy('__name__').limit(20);if(input.after)q=q.startAfter(input.after);const page=await q.get(),posts=[];
  for(const d of page.docs){const f=d.data(),root=await this.read({get:r=>r.get()},`communityPosts/${d.id}`);if(f.state==='visible'&&['community','everyone'].includes(f.audience)&&root?.visibility==='visible'&&root.lifecycle==='active'&&root.moderatorHold==='clear')posts.push({...f,postId:d.id});}
  const p=profile.data();return {profile:Object.fromEntries(['ownerUid','displayName','avatarKey','regionCode','bio','avatarAssetId','avatarChoiceVersion','publicationVersion'].map(k=>[k,p[k]??null])),posts,next:page.size===20?page.docs.at(-1).id:null};
 }
 async personalRead(actor,input){exact(input,[]);const uid=pathUid(actor?.uid);return this.db.runTransaction(async tx=>{await this.ready(tx,actor);return await this.read(tx,`users/${uid}/private/personal`)??{schemaVersion:1,ownerUid:uid,gender:null,birthdate:null,version:0};});}
 async autoProfile(tx,uid,draft,control,published,hold,op){
  if(control?.visibility==='hidden')return;
  ensure(hold?.hold==='clear','held');
  const candidate=Object.fromEntries(['displayName','avatarKey','regionCode','bio'].map(k=>[k,draft[k]]));
  ensure(!/(\+?\d[\d .()\-]{5,}\d|[\w.+-]+@[\w.-]+\.[\p{L}]{2,}|(?:địa chỉ|dia chi|address|đường|duong|street|phường|phuong|số nhà|so nha)\s*[:\d\p{L}])/iu.test(candidate.displayName+'\n'+candidate.bio),'denied');
  const now=new Date(this.clock());
  tx.set(this.ref(`publicProfiles/${uid}`),{...candidate,ownerUid:uid,schemaVersion:2,publicationVersion:number(published,'publicationVersion')+1,sourceDraftVersion:draft.version,publishedAt:now,avatarAssetId:draft.avatarAssetId??null,avatarChoiceVersion:draft.avatarChoiceVersion??0});
  if(!control)tx.set(this.ref(`publicProfileControls/${uid}`),{schemaVersion:1,ownerUid:uid,visibility:'public',version:1,lastOperationId:op,updatedAt:now});
  tx.set(this.ref(`mediaAccess/profile:${uid}`),{schemaVersion:2,targetId:`profile:${uid}`,sourceContentVersion:draft.version,assetIds:draft.avatarAssetId?[draft.avatarAssetId]:[],audience:'signed_in_profile'});
 }
 async saveProfile(actor,input){
  exact(input,['operationId','baseVersion','payload']);ensure(uuid(input.operationId));choice(input.baseVersion);
  const p=input.payload;exact(p,['displayName','avatarKey','regionCode','bio','submission']);
  ensure(typeof p.displayName==='string'&&p.displayName===p.displayName.trim()&&[...p.displayName].length>=2&&[...p.displayName].length<=40&&typeof p.bio==='string'&&[...p.bio].length<=500&&['default','cat','dog'].includes(p.avatarKey)&&(p.regionCode===null||['north','central','south'].includes(p.regionCode))&&p.submission==='draft','invalid');
  const uid=pathUid(actor?.uid),rp=`mediaCommands/${uid}/receipts/${input.operationId}`,hash=digest(input);
  return this.db.runTransaction(async tx=>{
   const prior=await this.read(tx,rp);if(prior){ensure(prior.command==='saveProfile'&&prior.inputHash===hash,'conflict');return prior.result;}
   const shell=await this.read(tx,`users/${uid}`);exact(shell,['uid','schemaVersion','createdAt']);ensure(shell.uid===uid&&shell.schemaVersion===1&&stamp(shell.createdAt),'not_ready');
   const old=await this.read(tx,`users/${uid}/profileDraft/current`),avatar=await this.read(tx,`users/${uid}/private/avatar`),moderation=await this.read(tx,`profileModeration/${uid}`),control=await this.read(tx,`publicProfileControls/${uid}`),published=await this.read(tx,`publicProfiles/${uid}`);
   ensure(number(old,'version')===input.baseVersion,'conflict');
   const next={...p,ownerUid:uid,schemaVersion:2,version:input.baseVersion+1,lastOperationId:input.operationId,updatedAt:new Date(this.clock()),avatarAssetId:avatar?.assetId??old?.avatarAssetId??null,avatarChoiceVersion:avatar?.choiceVersion??old?.avatarChoiceVersion??0};
   await this.autoProfile(tx,uid,next,control,published,moderation??(!old?{hold:'clear'}:null),input.operationId);
   tx.set(this.ref(`users/${uid}/profileDraft/current`),next);
   if(!moderation&&!old)tx.create(this.ref(`profileModeration/${uid}`),{schemaVersion:1,ownerUid:uid,hold:'clear',moderationVersion:1,reasonCode:null,decisionId:null,updatedAt:new Date(this.clock())});
   tx.create(this.ref(rp),{command:'saveProfile',inputHash:hash,result:{version:next.version},createdAt:new Date(this.clock())});return {version:next.version};
  });
 }
 async publishProfile(actor,input){
  exact(input,['operationId','base','candidate']);ensure(uuid(input.operationId));exact(input.base,['draft','control','publication','moderation']);for(const n of Object.values(input.base))choice(n);
  exact(input.candidate,['displayName','avatarKey','regionCode','bio']);const uid=pathUid(actor?.uid),rp=`users/${uid}/profileOperations/${input.operationId}`;
  return this.db.runTransaction(async tx=>{
   const prior=await this.read(tx,rp);if(prior){ensure(prior.command==='publish'&&same(prior.base,input.base)&&same(prior.input,input.candidate),'conflict');return {committed:true};}
   await this.ready(tx,actor,{verified:true,publication:true});
   const draft=await this.read(tx,`users/${uid}/profileDraft/current`),control=await this.read(tx,`publicProfileControls/${uid}`),published=await this.read(tx,`publicProfiles/${uid}`),hold=await this.read(tx,`profileModeration/${uid}`);
   ensure(draft&&draft.version===input.base.draft&&number(control,'version')===input.base.control&&number(published,'publicationVersion')===input.base.publication,'conflict');ensure(hold?.hold==='clear'&&hold.moderationVersion===input.base.moderation,'held');
   for(const key of Object.keys(input.candidate))ensure(draft[key]===input.candidate[key],'conflict');
   ensure(!/(\+?\d[\d .()\-]{5,}\d|[\w.+-]+@[\w.-]+\.[\p{L}]{2,}|(?:địa chỉ|dia chi|address|đường|duong|street|phường|phuong|số nhà|so nha)\s*[:\d\p{L}])/iu.test(draft.displayName+'\n'+draft.bio),'denied');
   const assetId=draft.avatarAssetId??null;const a=assetId?await this.read(tx,`mediaAssets/${assetId}`):null;
   const oldAssetId=published?.avatarAssetId??null;const oldAsset=oldAssetId&&oldAssetId!==assetId?await this.read(tx,`mediaAssets/${oldAssetId}`):null;
   if(assetId)ensure(a?.ownerUid===uid&&a.targetId===`profile:${uid}`&&a.state==='attached'&&['profile_avatar','google_avatar'].includes(a.purpose),'invalid_asset');
   const now=new Date(this.clock());
   tx.set(this.ref(`publicProfiles/${uid}`),{...input.candidate,ownerUid:uid,schemaVersion:2,publicationVersion:input.base.publication+1,sourceDraftVersion:input.base.draft,publishedAt:now,avatarAssetId:assetId,avatarChoiceVersion:draft.avatarChoiceVersion??0});
   tx.set(this.ref(`publicProfileControls/${uid}`),{schemaVersion:1,ownerUid:uid,visibility:'public',version:input.base.control+1,lastOperationId:input.operationId,updatedAt:now});
   tx.create(this.ref(rp),{schemaVersion:1,ownerUid:uid,operationId:input.operationId,command:'publish',base:input.base,result:{control:input.base.control+1,publication:input.base.publication+1},input:input.candidate,consent:true,committedAt:now});
   tx.set(this.ref(`mediaAccess/profile:${uid}`),{schemaVersion:2,targetId:`profile:${uid}`,sourceContentVersion:input.base.draft,assetIds:assetId?[assetId]:[],audience:'signed_in_profile'});
   if(oldAsset?.targetId===`profile:${uid}`)tx.set(this.ref(`mediaAssets/${oldAssetId}`),{...oldAsset,state:'ready',targetId:null,expiresAt:new Date(this.clock()+86400000),version:oldAsset.version+1});return {committed:true};
  });
 }
 async google(actor,input){
  exact(input,['operationId','expectedVersion','newRegistration']);ensure(uuid(input.operationId)&&typeof input.newRegistration==='boolean');choice(input.expectedVersion);
  const uid=pathUid(actor?.uid),bindOp=digest({operationId:input.operationId,kind:'google_bind'}).slice(0,32).replace(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/,'$1-$2-$3-$4-$5');
  const committed=(await this.ref(`mediaCommands/${uid}/receipts/${bindOp}`).get()).data();if(committed){ensure(committed.command==='avatar'&&committed.inputHash===digest({operationId:bindOp,expectedVersion:input.expectedVersion,assetId:input.operationId}),'conflict');return committed.result;}
  const user=await this.auth.getUser(uid),provider=user.providerData.find(x=>x.providerId==='google.com');ensure(provider,'provider_photo_unavailable');
  const avatar=(await this.ref(`users/${uid}/private/avatar`).get()).data();
  if(input.newRegistration){const created=Date.parse(user.metadata.creationTime);ensure(actor.firebase?.sign_in_provider==='google.com'&&this.clock()-created<600000&&!avatar?.googleHandled,'not_new_registration');}
  ensure(number(avatar,'choiceVersion')===input.expectedVersion,'conflict');let url=googleImageUrl(provider.photoURL),response;
  for(let n=0;n<3;n++){response=await this.fetcher(url,{redirect:'manual',signal:AbortSignal.timeout(8000)});if(response.status>=300&&response.status<400){url=googleImageUrl(new URL(response.headers.get('location'),url).href);continue;}break;}
  ensure(response?.ok,'provider_photo_unavailable');const chunks=[];let total=0;for await(const chunk of response.body){total+=chunk.length;ensure(total<=CANDIDATE,'invalid_image');chunks.push(chunk);}const raw=Buffer.concat(chunks);
  const source=sharp(raw,{limitInputPixels:20_000_000,animated:false});const info=await source.metadata();ensure(['jpeg','png','webp'].includes(info.format)&&(!info.pages||info.pages===1),'invalid_image');
  const normalized=await source.rotate().resize({width:2048,height:2048,fit:'inside',withoutEnlargement:true}).jpeg({quality:82}).toBuffer();
  // Deterministic operation-linked IDs prevent duplicate quota on a lost response.
  const assetId=input.operationId;await this.reserve(actor,{assetId,operationId:input.operationId,purpose:'google_avatar',inputHash:digest(normalized)});await this.receive(actor,assetId,normalized);
  return this.avatar(actor,{operationId:bindOp,expectedVersion:input.expectedVersion,assetId});
 }
 async remove(actor,input){
  exact(input,['assetId']);ensure(uuid(input.assetId));const uid=pathUid(actor?.uid);
  const a=await this.db.runTransaction(async tx=>{const a=await this.read(tx,`mediaAssets/${input.assetId}`),r=await this.read(tx,`mediaReservations/${input.assetId}`),hold=await this.read(tx,`mediaRetention/${input.assetId}`);ensure(a?.ownerUid===uid,'denied');ensure(!a.targetId&&!hold?.held,'retained');if(r?.state==='released')return a;const next={...a,state:'cleanup_pending',version:a.version+1};tx.set(this.ref(`mediaAssets/${input.assetId}`),next);return next;});
  if(a.state==='deleted')return {assetId:a.assetId,state:'deleted'};
  if(a.writerActive!==false)return {assetId:a.assetId,state:'cleanup_pending'};
  for(const path of [`staging/${uid}/${a.assetId}/candidate`,`final/${uid}/${a.assetId}/display.jpg`,`final/${uid}/${a.assetId}/thumbnail.jpg`]){const file=this.bucket.file(path);try{const [m]=await file.getMetadata();await file.delete({ifGenerationMatch:m.generation});}catch(e){if(e.code!==404)throw e;}const [exists]=await file.exists();ensure(!exists,'cleanup_unknown');}
  return this.db.runTransaction(async tx=>{const current=await this.read(tx,`mediaAssets/${a.assetId}`),reservation=await this.read(tx,`mediaReservations/${a.assetId}`),budget=await this.read(tx,'mediaBudget/current'),hold=await this.read(tx,`mediaRetention/${a.assetId}`);if(reservation.state==='released')return {assetId:a.assetId,state:'deleted'};ensure(current.state==='cleanup_pending'&&current.writerActive===false&&!current.targetId&&!hold?.held,'cleanup_conflict');ensure(budget.reservedBytes>=RESERVATION&&budget.limitBytes===LIMIT,'budget_corrupt');tx.set(this.ref('mediaBudget/current'),{...budget,reservedBytes:budget.reservedBytes-RESERVATION,version:budget.version+1,updatedAt:new Date(this.clock())});tx.set(this.ref(`mediaReservations/${a.assetId}`),{...reservation,state:'released'});tx.set(this.ref(`mediaAssets/${a.assetId}`),{...current,state:'deleted',variants:{},version:current.version+1});return {assetId:a.assetId,state:'deleted'};});
 }
}
