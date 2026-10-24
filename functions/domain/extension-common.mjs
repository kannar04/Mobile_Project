import {Commands,exact,text,integer,ms,digest,fail,shell,protectedMember,scopedRestriction} from './safety-common.mjs';
export {exact,text,integer,ms,digest,fail,shell};
export const DAY=86400000;
export const id=v=>typeof v==='string'&&/^[a-zA-Z0-9][a-zA-Z0-9_-]{0,127}$/.test(v);
export function fields(v,keys){if(!exact(v,keys))fail('payload_schema');return v;}
export async function member(w,a,{newAction=false,scope=null}={}){await shell(w,a?.uid);if(a.emailVerified!==true||a.disabled===true)fail('membership_unavailable');const m=await w.get(`communityMemberships/${a.uid}`),subject=await w.get(`securitySubjects/${a.uid}`);if(!exact(m,['schemaVersion','uid','status','membershipVersion','createdAt','updatedAt'])||m.schemaVersion!==1||m.uid!==a.uid||m.status!=='active'||!integer(m.membershipVersion,1)||!Number.isFinite(ms(m.createdAt))||!Number.isFinite(ms(m.updatedAt))||subject&&subject.status!=='active')fail('membership_unavailable');if(newAction&&scope)await scopedRestriction(w,a.uid,scope);}
export async function enabled(w,key){const p=await w.get('extensionRuntimePolicy/current');if(p?.schemaVersion!==1||p[key]!==true)fail('extension_adapter_unavailable');}
export async function catalog(w,kind,value,{unknown=false}={}){if(unknown&&value==='unknown')return;if(!id(value))fail('catalog_id');const row=await w.get(`${kind}/${value}`);if(row?.schemaVersion!==1||row.id!==value||row.state!=='active')fail('catalog_not_active');}
export function dateOnly(value){if(typeof value!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(value)||Number(value.slice(0,4))<1900||Number(value.slice(0,4))>2200)fail('date_schema');const n=Date.parse(value+'T00:00:00Z');if(!Number.isFinite(n)||new Date(n).toISOString().slice(0,10)!==value)fail('date_schema');return n;}
export function zone(value){if(typeof value!=='string'||value.length>80||value!=='UTC'&&!value.includes('/'))fail('zone_schema');try{new Intl.DateTimeFormat('en',{timeZone:value}).format();}catch{fail('zone_schema');}return value;}
export function localDay(now,z){zone(z);const parts=new Intl.DateTimeFormat('en-CA',{timeZone:z,year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(now),get=t=>parts.find(p=>p.type===t).value;return `${get('year')}-${get('month')}-${get('day')}`;}
export class ExtensionCommands extends Commands {
 constructor(store,receipt,commands,options={}){super(store,receipt,Object.fromEntries(commands.map(c=>[c,['payload']])),options);}
 async command(w,a,c,now){if(!c.payload||typeof c.payload!=='object'||Array.isArray(c.payload))fail('payload_schema');for(const[k,value]of Object.entries(c.payload))if(k!=='zoneId'&&/(Id|Uid)$/.test(k)&&value!==null&&!id(value))fail('invalid_identifier');return this.apply(w,a,c.command,c.payload,c.expectedVersion,c.operationId,now);}
}
export function base(row,expected){if((row?.version??0)!==expected)fail('stale_version');}
export function own(row,uid){if(!row||row.ownerUid!==uid&&row.authorUid!==uid&&row.claimantUid!==uid)fail('not_owner');}
export function unavailable(){fail('compatible_source_adapter_required');}
