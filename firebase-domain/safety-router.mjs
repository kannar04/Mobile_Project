import {exact,fail} from './safety-common.mjs';
// Deployment-independent callable adapter. Production auth/verified-token handling
// must be explicitly supplied; no endpoint or bypass fallback is registered.
export function safetyRouter(services,{authenticate=async()=>fail('managed_auth_handler_not_configured')}={}){
 return async(context,request)=>{
  if(!exact(request,['module','command','input'])||!['care','account','moderation','support','followup','welfare','return','decision','experience'].includes(request.module)||!request.input||typeof request.input!=='object')fail('callable_request');
  const actor=await authenticate(context);if(typeof actor?.uid!=='string'||!actor.uid||actor.disabled===true)fail('unauthenticated');
  const service=services[request.module];if(!service)fail('domain_adapter_unavailable');const {command,input}=request;
  if(request.module==='care'){if(command!=='save')fail('care_command');return service.save(actor,input);}
  if(request.module==='account'&&['lookup_uid','lookup_email'].includes(command)){if(!exact(input,[command==='lookup_uid'?'uid':'email']))fail('exact_lookup_required');return service.lookup(actor,input);}
  if(request.module==='welfare'&&command==='read_evidence'){if(!exact(input,['caseId','purpose']))fail('read_scope');return service.readEvidence(actor,input.caseId,input.purpose);}
  if(request.module==='followup'&&command==='evaluate_due'){if(!exact(input,['planId','task']))fail('due_scope');return service.evaluate(actor,input.planId,input.task);}
  if(input.command!==command)fail('command_mismatch');return service.execute(actor,input);
 };
}
