// Read-only rollout facts. No identities, credentials or raw documents are printed.
const {getGlobalDefaultAccount}=require('firebase-tools/lib/auth');
const {requireAuth}=require('firebase-tools/lib/requireAuth');
const {Client}=require('firebase-tools/lib/apiv2');
const fs=require('node:fs');
(async()=>{const project='aniloveproject',account=getGlobalDefaultAccount();if(!account)throw Error('login_required');await requireAuth({...account,project,nonInteractive:true});const api=new Client({urlPrefix:'https://firestore.googleapis.com',apiVersion:'v1'}),result={project,readOnly:true};
for(const name of ['communityPolicy','reportPolicy','moderationCapacity','communityPublicPolicy']){try{const response=await api.get(`projects/${project}/databases/(default)/documents/system/${name}`,{retries:0}),f=response.body.fields??{};result[name]={exists:true};for(const field of ['enabled','intakeEnabled','capacityVerified'])if(f[field]?.booleanValue!==undefined)result[name][field]=f[field].booleanValue;}catch(error){result[name]={exists:false,requestStatus:error.status??error.context?.response?.statusCode??'unverified'};}}
fs.writeFileSync('../build/community-rollout-gates.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));})().catch(()=>{console.error('Read-only gate inspection failed.');process.exitCode=1;});
