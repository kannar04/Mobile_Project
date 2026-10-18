import {exact,integer,ms,fail,shell} from './safety-common.mjs';
export async function browseRelease(w){
 const p=await w.get('adoptionBrowseRelease/current'),workflow=await w.get('adoptionWorkflowPolicy/current');
 if(!exact(p,['schemaVersion','version','enabled','mode','allowedSourceSchema','applicationsEnabled','updatedAt','auditRef'])||p.schemaVersion!==1||!integer(p.version,1)||p.enabled!==true||!['validated_browse_share','pet_reviewed_social_adoption'].includes(p.mode)||p.allowedSourceSchema!==2||(p.mode==='validated_browse_share'?p.applicationsEnabled!==false:p.applicationsEnabled!==true)||!Number.isFinite(ms(p.updatedAt))||typeof p.auditRef!=='string'||!p.auditRef||p.auditRef.length>128||workflow?.schemaVersion!==2||workflow.publicationEnabled!==true||workflow.writerSetVersion!==2||workflow.reviewCapacityVerified!==true)fail('publication_not_ready');
 return p;
}
export async function browseMember(w,uid){await shell(w,uid);const m=await w.get(`communityMemberships/${uid}`),s=await w.get(`securitySubjects/${uid}`);if(m?.status!=='active'||m.uid!==uid||s&&s.status!=='active')fail('account_unavailable');}
