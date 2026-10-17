// Explicitly requested prerecorded fictional stories. Never enables publication or grants roles.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {createHash}=require('node:crypto');
const {seed,matches}=require('./seed-spark-catalog.cjs');
const project='aniloveproject',base=`projects/${project}/databases/(default)/documents`;
const authorUid='anilove-fictional-story-author-r1'; // No Auth account or impersonated user.
function uuid(key){const h=createHash('sha256').update(`anilove-course-stories-r1/${key}`).digest('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-4${h.slice(13,16)}-a${h.slice(17,20)}-${h.slice(20,32)}`;}
const stories=[
 ['Một góc nắng nhỏ','Hôm nay Mướp chọn chiếc hộp giấy cạnh cửa sổ làm chỗ nghỉ. Một chiếc chăn quen thuộc và góc yên tĩnh đã đủ cho buổi chiều thật dễ chịu.','cat'],
 ['Chuyến đi dạo buổi sáng','Bông có một chuyến đi dạo ngắn với nhiều lần dừng lại ngửi cỏ. Chúng mình mang nước và chọn đường râm mát. Bạn nhỏ trở về với chiếc đuôi vẫn vẫy.','dog'],
 ['Chiếc hộp được yêu thích','Đồ chơi mới nhất của Mướp lại là một chiếc hộp giấy sạch. Bạn ấy khám phá một lúc rồi cuộn tròn ngủ bên trong. Những niềm vui nhỏ thật đáng nhớ.','cat'],
 ['Một nhịp sinh hoạt quen thuộc','Bông đang làm quen với góc nghỉ riêng. Chúng mình giữ lịch sinh hoạt nhẹ nhàng và để bạn ấy tự chọn lúc đến gần. Mỗi ngày có thêm một khoảnh khắc thoải mái.','dog'],
 ['Bức ảnh trong trí nhớ','Mướp ngồi nhìn những chiếc lá chuyển động ngoài cửa sổ. Chưa cần ảnh tải lên, mình vẫn muốn ghi lại câu chuyện bình dị này bằng vài dòng chữ.','cat'],
 ['Một ngày bình yên','Sau giờ chơi, Bông nằm nghỉ cạnh chiếc chăn cũ. Câu chuyện hôm nay chỉ có vậy: một ngày bình yên và một người bạn nhỏ ở bên.','dog']
];
const records=[],postIds=[];
const stamp=(p,data,timestamps)=>records.push({path:p,data,timestamps});
for(const [i,[title,text,image]] of stories.entries()){
 const postId=uuid(`post-${i}`),operationId=uuid(`publish-${i}`),body=`[DEMO — CÂU CHUYỆN HƯ CẤU]\n${title}\n\n${text}\n\nDữ liệu mẫu cho bài tập ANILOVE; không phải hồ sơ động vật hay lời kêu gọi nhận nuôi thật.`;
 assert.ok(body.length<=2000);postIds.push(postId);
 stamp(`communityPosts/${postId}`,{schemaVersion:2,postId,authorUid,type:'community',lifecycle:'active',contentVersion:1,currentRevisionId:'v1',controlVersion:1,visibility:'visible',moderatorHold:'clear',editedAt:null,lastOperationId:operationId},['firstPublishedAt','createdAt','updatedAt']);
 stamp(`communityPosts/${postId}/revisions/v1`,{schemaVersion:2,postId,authorUid,type:'community',contentVersion:1,body,bundledIllustrationKey:image,operationId},['createdAt']);
 stamp(`communityFeed/${postId}`,{schemaVersion:3,postId,authorUid,type:'community',contentVersion:1,controlVersion:1,body,bundledIllustrationKey:image,audience:'community',state:'visible',editedAt:null},['firstPublishedAt']);
 stamp(`communityCounts/${postId}`,{schemaVersion:2,postId,likeCount:0,countVersion:0,lastOperationId:operationId},['updatedAt']);
 stamp(`users/${authorUid}/communityOperations/${operationId}`,{schemaVersion:2,ownerUid:authorUid,operationId,postId,targetId:null,command:'publish',base:{contentVersion:0,controlVersion:0,targetVersion:0},result:{contentVersion:1,controlVersion:1},input:{body,bundledIllustrationKey:image,consent:true},status:'committed'},['committedAt']);
}
async function selfTest(){let writes;const fake={get:async()=>{throw {status:404};},post:async(_,body)=>{writes=body.writes;}};
 assert.equal((await seed(fake,false,records)).pending,30);assert.equal(writes,undefined);
 await seed(fake,true,records);assert.equal(writes.length,30);
 for(const w of writes){assert.equal(w.currentDocument.exists,false);assert.ok(!w.update.name.includes('/system/'));}
 assert.equal(records.filter(r=>r.path.startsWith('communityFeed/')).length,6);
 assert.equal(records.filter(r=>r.path.startsWith('communityCounts/')).every(r=>r.data.likeCount===0),true);
 console.log('PASS: six labeled stories, consistent companion records, dry run, atomic create-only writes, no policy writes');
}
async function main(){if(process.argv.includes('--self-test'))return selfTest();
 const planning='C:/Users/Minht/Desktop/New folder';
 for(const name of ['F31.r3','F39.r1']){const receipt=JSON.parse(fs.readFileSync(path.join(planning,`approval_review/${name}.approved.json`)));assert.equal(createHash('sha256').update(fs.readFileSync(path.join(planning,receipt.analysis_path))).digest('hex'),receipt.approved_artifact_sha256);}
 const {getGlobalDefaultAccount}=require('firebase-tools/lib/auth'),{requireAuth}=require('firebase-tools/lib/requireAuth'),{Client}=require('firebase-tools/lib/apiv2');
 const account=getGlobalDefaultAccount();if(!account)throw Error('login_required');await requireAuth({...account,project,nonInteractive:true});
 const client=new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'}),billing=new Client({urlPrefix:'https://cloudbilling.googleapis.com',apiVersion:'v1'});
 const check=async()=>{assert.equal((await billing.get(`projects/${project}/billingInfo`,{retries:0})).body.billingEnabled,false);
   assert.equal((await client.get(`${base}/system/communityPolicy`,{retries:0})).body.fields.enabled.booleanValue,false);
   assert.equal((await client.get(`${base}/system/reportPolicy`,{retries:0})).body.fields.intakeEnabled.booleanValue,false);};
 await check();const apply=process.argv.includes('--apply'),result=await seed(client,apply,records);
 if(apply)for(const r of records)assert.ok(matches((await client.get(`${base}/${r.path}`,{retries:0})).body,r));
 await check();const report={project,apply,...result,verified:apply,fictional:true,posts:6,postIds,authorUid,authAccountsCreated:0,billingEnabled:false,publicationEnabled:false};
 fs.writeFileSync(path.join(__dirname,'../build/spark-post-seed.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));
}
if(require.main===module)main().catch(e=>{console.error(`Post seed stopped: ${e.message??'provider_error'}`);process.exitCode=1;});
module.exports={postIds,records};
