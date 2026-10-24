import {Commands,Work,text,uuid,fail} from './domain/safety-common.mjs';
const must=(v,c='invalid_intent')=>{if(!v)fail(c);};
/** Course-demo welfare intake. It records a private case; it does not dispatch emergency services. */
export class WelfareReports extends Commands {
 constructor(store,options={}){super(store,'welfareReportReceipts',{submit:['reportId','category','statement','assetIds']},options);}
 async command(w,actor,c,now){
  must(c.expectedVersion===0&&uuid(c.reportId));must(['injury','illness','abandonment','malnutrition','unsafe_shelter','neglect','abuse','trapped','other'].includes(c.category),'invalid_category');must(text(c.statement,2000,10),'invalid_text');must(Array.isArray(c.assetIds)&&c.assetIds.length>=1&&c.assetIds.length<=6&&new Set(c.assetIds).size===c.assetIds.length&&c.assetIds.every(uuid),'invalid_asset');must(!(await w.get('reports/'+c.reportId)),'conflict');
  for(const id of c.assetIds){const asset=await w.get('mediaAssets/'+id);must(asset?.ownerUid===actor.uid&&asset.purpose==='adoption_private'&&asset.state==='ready'&&!asset.targetId,'invalid_asset');w.set('mediaAssets/'+id,{...asset,state:'attached',targetId:'report:'+c.reportId,version:asset.version+1});}
  const at=new Date(now),report={schemaVersion:1,reporterUid:actor.uid,targetKind:'account',targetId:actor.uid,parentId:null,category:'Phúc lợi thú cưng · '+c.category,statement:c.statement,status:'received',statusVersion:1,demoOnly:true,createdAt:at,updatedAt:at};
  w.set('reports/'+c.reportId,report);w.set(`reports/${c.reportId}/restrictedEvidence/current`,{schemaVersion:1,reportId:c.reportId,ownerUid:actor.uid,assetIds:c.assetIds,description:c.statement,createdAt:at});w.set(`users/${actor.uid}/reportStatus/${c.reportId}`,{schemaVersion:1,reportId:c.reportId,status:'received',statusVersion:1,demoOnly:true,createdAt:at,updatedAt:at});return {reportId:c.reportId,status:'received',demoOnly:true};
 }
}
