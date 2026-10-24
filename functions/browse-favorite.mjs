import {Work,exact,uuid,integer,fail,shell} from './domain/safety-common.mjs';
import {currentBrowseSource} from './domain/adoption-release.mjs';
import {sha} from './domain/adoption-source2.mjs';
export async function browseFavorite(runtime,actor,c){
 if(!exact(c,['operationId','listingId','desiredState','base'])||!uuid(c.operationId)||!uuid(c.listingId)||typeof c.desiredState!=='boolean'||!exact(c.base,['favoriteVersion','metaVersion'])||!integer(c.base.favoriteVersion)||!integer(c.base.metaVersion))fail('invalid_intent');
 const uid=actor.uid,inputDigest=sha({listingId:c.listingId,desiredState:c.desiredState,base:c.base}),path=`users/${uid}/`;
 return runtime.store.transaction(async tx=>{const w=new Work(tx);await shell(w,uid);const receipt=path+'favoriteOperations/'+c.operationId,prior=await w.get(receipt);if(prior){if(prior.inputDigest!==inputDigest)fail('intent_conflict');return prior;}
  const item=path+'favorites/'+c.listingId,meta=path+'favoriteMeta/main',old=await w.get(item),count=await w.get(meta),fv=old?.favoriteVersion??0,mv=count?.metaVersion??0,active=count?.activeCount??0;
  if(fv!==c.base.favoriteVersion||mv!==c.base.metaVersion)fail('version_conflict');if(!integer(active)||active>200||old&&(!count||active===0))fail('favorite_meta_repair_required');if(!count&&(await tx.scan(path+'favorites',1)).length)fail('favorite_meta_repair_required');
  const delta=Number(c.desiredState)-Number(!!old);if(delta>0){await currentBrowseSource(w,c.listingId);if(active===200)fail('favorite_cap');}if(delta===0&&!count)return {saved:false,unchanged:true};
  const now=new Date(),nextMv=mv+(delta?1:0),record={schemaVersion:1,uid,listingId:c.listingId,operationId:c.operationId,command:'desired_saved_or_removed',desiredState:c.desiredState,inputDigest,base:c.base,result:{favoriteVersion:c.desiredState?(old?fv:1):0,metaVersion:nextMv,activeCount:active+delta,saved:c.desiredState},delta,serverCreatedAt:now};
  if(delta){if(c.desiredState)w.set(item,{schemaVersion:1,uid,listingId:c.listingId,savedAt:now,favoriteVersion:1,lastOperationId:c.operationId});else w.delete(item);w.set(meta,{schemaVersion:1,uid,activeCount:active+delta,metaVersion:nextMv,lastOperationId:c.operationId,updatedAt:now});}w.set(receipt,record);w.flush();return record;
 });
}
