import {exact,fail} from './safety-common.mjs';
// No endpoint, Admin credentials, IAM or service deployment is registered here.
export function extensionRouter(services,{authenticate=async()=>fail('managed_auth_not_configured')}={}){
 return async(context,request)=>{
  if(!exact(request,['module','command','input'])||!['lostfound','foster','exchange','preference','reminder','dashboard','feed'].includes(request.module))fail('extension_request');
  const actor=await authenticate(context);if(!actor?.uid||actor.disabled===true)fail('unauthenticated');const service=services[request.module];if(!service)fail('extension_unavailable');
  if(request.module==='dashboard'&&request.command==='read')return service.read(actor,request.input);
  if(request.module==='feed'&&request.command==='read_page')return service.read(actor,request.input);
  if(request.module==='lostfound'&&request.command==='read_evidence'){if(!exact(request.input,['noticeId','claimId','purpose']))fail('evidence_scope');return service.readEvidence(actor,request.input.noticeId,request.input.claimId,request.input.purpose);}
  if(!exact(request.input,['operationId','expectedVersion','payload']))fail('command_envelope');
  return service.execute(actor,{command:request.command,...request.input});
 };
}
